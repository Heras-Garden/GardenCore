package com.herasgarden.gardencore.claim;

import com.herasgarden.gardencore.GardenCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ClaimPreviewRenderer {
    private static final int MAX_GLOWSTONE_MARKERS = 600;

    private final GardenCore plugin;
    private final ClaimSessionManager sessions;
    private final Map<UUID, Set<PreviewBlock>> glowstoneByPlayer = new HashMap<>();
    private final Map<UUID, BukkitTask> deletionTasks = new HashMap<>();
    private final Map<UUID, Set<PreviewBlock>> deletionGlowstone = new HashMap<>();
    private BukkitTask task;

    public ClaimPreviewRenderer(GardenCore plugin, ClaimSessionManager sessions) {
        this.plugin = plugin;
        this.sessions = sessions;
    }

    public void start() {
        stop();
        long period = Math.max(5L, plugin.getConfig().getLong("claims.preview.refresh-ticks", 10L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::renderAll, 10L, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID playerId : Set.copyOf(glowstoneByPlayer.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) restoreGlowstone(player);
            else glowstoneByPlayer.remove(playerId);
        }
        for (UUID playerId : Set.copyOf(deletionTasks.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) stopDeletionPreview(player);
        }
    }

    private void renderAll() {
        Set<UUID> active = sessions.sessions().keySet();
        for (UUID playerId : Set.copyOf(glowstoneByPlayer.keySet())) {
            if (!active.contains(playerId)) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) restoreGlowstone(player);
                else glowstoneByPlayer.remove(playerId);
            }
        }

        for (Map.Entry<UUID, ClaimSession> entry : sessions.sessions().entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;
            render(player, entry.getValue());
        }
    }

    private void render(Player player, ClaimSession session) {
        World world = Bukkit.getWorld(session.worldId());
        if (world == null || !player.getWorld().getUID().equals(world.getUID())) {
            restoreGlowstone(player);
            player.sendActionBar(Component.text("Return to the claim world to continue.", NamedTextColor.RED));
            return;
        }

        List<ClaimPoint> raw = session.points();
        if (raw.isEmpty()) {
            restoreGlowstone(player);
            player.sendActionBar(Component.text("Right-click the first corner.", NamedTextColor.YELLOW));
            return;
        }

        ClaimGeometry geometry = session.geometry(world);
        syncGlowstone(player, session, geometry, raw);

        ClaimValidation validation = geometry == null ? null : sessions.validation(player, session);
        Color color = !session.closed() ? Color.fromRGB(249, 195, 73)
                : validation != null && validation.valid() ? Color.fromRGB(143, 175, 126)
                : Color.fromRGB(220, 70, 70);
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.15f);

        if (geometry == null) {
            for (ClaimPoint point : raw) {
                drawColumn(player, point.x() + 0.5, session.previewY() + 1.0, point.z() + 0.5, 2.5, dust);
            }
            player.sendActionBar(Component.text("Point " + raw.size() + " set. Click the first point again when finished.",
                    NamedTextColor.YELLOW));
            return;
        }

        double baseY = session.fullHeight() ? player.getLocation().getY() + 0.3 : session.minY() + 0.15;
        drawPolygon(player, geometry.vertices(), baseY, dust);

        if (!session.fullHeight()) {
            double topY = session.maxY() + 1.0;
            drawPolygon(player, geometry.vertices(), topY, dust);
            for (ClaimPoint point : geometry.vertices()) {
                drawVerticalEdge(player, point.x() + 0.5, session.minY(), point.z() + 0.5,
                        session.maxY() + 1.0, dust);
            }
        } else {
            for (ClaimPoint point : geometry.vertices()) {
                drawColumn(player, point.x() + 0.5, baseY, point.z() + 0.5, 3.0, dust);
            }
        }

        if (session.closed()) drawSparseFill(player, geometry, baseY + 0.05, dust);

        String dimensions;
        if (session.shape() == ClaimShape.RECTANGLE) {
            dimensions = geometry.width() + " x " + geometry.length();
            if (!geometry.fullHeight()) dimensions += " x " + geometry.height();
        } else {
            dimensions = geometry.vertices().size() + " points | " + geometry.width() + " x " + geometry.length();
            if (!geometry.fullHeight()) dimensions += " x " + geometry.height();
        }
        String status = !session.closed() ? "Click the first point to close"
                : validation != null && validation.valid() ? "Ready to confirm"
                : validation == null ? "Incomplete" : validation.reason();
        NamedTextColor textColor = session.closed() && validation != null && !validation.valid()
                ? NamedTextColor.RED : NamedTextColor.WHITE;
        player.sendActionBar(Component.text(dimensions + " | " + geometry.blockAreaEstimate() + " blocks² | " + status,
                textColor));
    }

    private void syncGlowstone(Player player, ClaimSession session, ClaimGeometry geometry, List<ClaimPoint> raw) {
        World world = player.getWorld();
        Set<PreviewBlock> next = new HashSet<>();
        List<ClaimPoint> points = geometry == null ? raw : geometry.vertices();

        if (points.size() == 1) {
            addMarker(next, world, session, points.getFirst().x(), points.getFirst().z());
        } else {
            int edgeCount = geometry == null || !session.closed() ? points.size() - 1 : points.size();
            for (int i = 0; i < edgeCount && next.size() < MAX_GLOWSTONE_MARKERS; i++) {
                ClaimPoint from = points.get(i);
                ClaimPoint to = points.get((i + 1) % points.size());
                addEdgeMarkers(next, world, session, from, to);
            }
        }

        Set<PreviewBlock> previous = glowstoneByPlayer.getOrDefault(player.getUniqueId(), Set.of());
        for (PreviewBlock marker : previous) {
            if (!next.contains(marker)) restore(player, marker);
        }
        for (PreviewBlock marker : next) {
            if (!previous.contains(marker)) {
                player.sendBlockChange(new Location(world, marker.x(), marker.y(), marker.z()),
                        Material.GLOWSTONE.createBlockData());
            }
        }

        if (next.isEmpty()) glowstoneByPlayer.remove(player.getUniqueId());
        else {
            glowstoneByPlayer.put(player.getUniqueId(), next);
            if (previous.isEmpty()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) restoreGlowstone(player);
                }, 20L);
            }
        }
    }

    private void addEdgeMarkers(Set<PreviewBlock> markers, World world, ClaimSession session,
                                ClaimPoint from, ClaimPoint to) {
        double dx = to.x() - from.x();
        double dz = to.z() - from.z();
        double distance = Math.sqrt(dx * dx + dz * dz);
        int steps = Math.max(1, (int) Math.ceil(distance));
        for (int i = 0; i <= steps && markers.size() < MAX_GLOWSTONE_MARKERS; i++) {
            double t = i / (double) steps;
            int x = (int) Math.round(from.x() + dx * t);
            int z = (int) Math.round(from.z() + dz * t);
            addMarker(markers, world, session, x, z);
        }
    }

    private void addMarker(Set<PreviewBlock> markers, World world, ClaimSession session, int x, int z) {
        int y = session.fullHeight()
                ? Math.min(world.getMaxHeight() - 2, world.getHighestBlockYAt(x, z) + 1)
                : Math.max(world.getMinHeight(), Math.min(world.getMaxHeight() - 2, session.minY()));
        markers.add(new PreviewBlock(world.getUID(), x, y, z));
    }

    private void restoreGlowstone(Player player) {
        Set<PreviewBlock> previous = glowstoneByPlayer.remove(player.getUniqueId());
        if (previous == null) return;
        for (PreviewBlock marker : previous) restore(player, marker);
    }

    private void restore(Player player, PreviewBlock marker) {
        World world = Bukkit.getWorld(marker.worldId());
        if (world == null || !player.getWorld().getUID().equals(marker.worldId())) return;
        Location location = new Location(world, marker.x(), marker.y(), marker.z());
        player.sendBlockChange(location, location.getBlock().getBlockData());
    }

    public void startDeletionPreview(Player player, Claim claim) {
        stopDeletionPreview(player);
        Runnable pulse = () -> {
            if (!player.isOnline()) {
                stopDeletionPreview(player);
                return;
            }
            Set<PreviewBlock> markers = claimMarkers(player, claim);
            deletionGlowstone.put(player.getUniqueId(), markers);
            for (PreviewBlock marker : markers) {
                World world = Bukkit.getWorld(marker.worldId());
                if (world != null && player.getWorld().getUID().equals(marker.worldId())) {
                    player.sendBlockChange(new Location(world, marker.x(), marker.y(), marker.z()),
                            Material.GLOWSTONE.createBlockData());
                }
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> restoreDeletionGlowstone(player), 20L);
        };
        pulse.run();
        deletionTasks.put(player.getUniqueId(), Bukkit.getScheduler().runTaskTimer(plugin, pulse, 40L, 40L));
    }

    public void stopDeletionPreview(Player player) {
        BukkitTask task = deletionTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
        restoreDeletionGlowstone(player);
    }

    private void restoreDeletionGlowstone(Player player) {
        Set<PreviewBlock> markers = deletionGlowstone.remove(player.getUniqueId());
        if (markers == null) return;
        for (PreviewBlock marker : markers) restore(player, marker);
    }

    private Set<PreviewBlock> claimMarkers(Player player, Claim claim) {
        Set<PreviewBlock> markers = new HashSet<>();
        World world = Bukkit.getWorld(claim.geometry().worldId());
        if (world == null || !player.getWorld().getUID().equals(world.getUID())) return markers;
        List<ClaimPoint> points = claim.geometry().vertices();
        for (int i = 0; i < points.size() && markers.size() < MAX_GLOWSTONE_MARKERS; i++) {
            ClaimPoint from = points.get(i);
            ClaimPoint to = points.get((i + 1) % points.size());
            double dx = to.x() - from.x();
            double dz = to.z() - from.z();
            int steps = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dz * dz)));
            for (int step = 0; step <= steps && markers.size() < MAX_GLOWSTONE_MARKERS; step++) {
                double t = step / (double) steps;
                int x = (int) Math.round(from.x() + dx * t);
                int z = (int) Math.round(from.z() + dz * t);
                int y = claim.geometry().fullHeight()
                        ? Math.min(world.getMaxHeight() - 2, world.getHighestBlockYAt(x, z) + 1)
                        : claim.geometry().minY();
                markers.add(new PreviewBlock(world.getUID(), x, y, z));
                if (!claim.geometry().fullHeight()) {
                    markers.add(new PreviewBlock(world.getUID(), x, claim.geometry().maxY(), z));
                }
            }
        }
        return markers;
    }

    private void drawPolygon(Player player, List<ClaimPoint> points, double y, Particle.DustOptions dust) {
        for (int i = 0; i < points.size(); i++) {
            ClaimPoint from = points.get(i);
            ClaimPoint to = points.get((i + 1) % points.size());
            drawLine(player, from.x() + 0.5, y, from.z() + 0.5,
                    to.x() + 0.5, y, to.z() + 0.5, dust);
        }
    }

    private void drawLine(Player player, double x1, double y, double z1,
                          double x2, double y2, double z2, Particle.DustOptions dust) {
        double dx = x2 - x1;
        double dy = y2 - y;
        double dz = z2 - z1;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.max(1, Math.min(250, (int) Math.ceil(distance / 0.75)));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            player.spawnParticle(Particle.DUST, x1 + dx * t, y + dy * t, z1 + dz * t,
                    1, 0, 0, 0, 0, dust);
        }
    }

    private void drawVerticalEdge(Player player, double x, double minY, double z, double maxY,
                                  Particle.DustOptions dust) {
        drawLine(player, x, minY, z, x, maxY, z, dust);
    }

    private void drawColumn(Player player, double x, double y, double z, double height, Particle.DustOptions dust) {
        drawLine(player, x, y, z, x, y + height, z, dust);
    }

    private void drawSparseFill(Player player, ClaimGeometry geometry, double y, Particle.DustOptions dust) {
        long area = geometry.blockAreaEstimate();
        int step = Math.max(2, (int) Math.ceil(Math.sqrt(Math.max(1L, area) / 250.0)));
        int emitted = 0;
        int cap = Math.max(100, plugin.getConfig().getInt("claims.preview.maximum-fill-particles", 500));
        for (int x = geometry.minX(); x <= geometry.maxX() && emitted < cap; x += step) {
            for (int z = geometry.minZ(); z <= geometry.maxZ() && emitted < cap; z += step) {
                if (geometry.contains2D(x, z)) {
                    player.spawnParticle(Particle.DUST, x + 0.5, y, z + 0.5, 1, 0, 0, 0, 0, dust);
                    emitted++;
                }
            }
        }
    }

    private record PreviewBlock(UUID worldId, int x, int y, int z) {}
}
