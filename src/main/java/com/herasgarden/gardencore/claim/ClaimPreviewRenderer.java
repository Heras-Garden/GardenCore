package com.herasgarden.gardencore.claim;

import com.herasgarden.gardencore.GardenCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClaimPreviewRenderer {
    private final GardenCore plugin;
    private final ClaimSessionManager sessions;
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
    }

    private void renderAll() {
        for (Map.Entry<UUID, ClaimSession> entry : sessions.sessions().entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            render(player, entry.getValue());
        }
    }

    private void render(Player player, ClaimSession session) {
        World world = Bukkit.getWorld(session.worldId());
        if (world == null || !player.getWorld().getUID().equals(world.getUID())) {
            player.sendActionBar(Component.text("Return to the claim world to continue.", NamedTextColor.RED));
            return;
        }

        List<ClaimPoint> raw = session.points();
        if (raw.isEmpty()) {
            player.sendActionBar(Component.text("Right-click the first corner.", NamedTextColor.YELLOW));
            return;
        }

        ClaimGeometry geometry = session.geometry(world);
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

        if (session.closed()) {
            drawSparseFill(player, geometry, baseY + 0.05, dust);
        }

        String dimensions;
        if (session.shape() == ClaimShape.RECTANGLE) {
            dimensions = geometry.width() + " x " + geometry.length();
            if (!geometry.fullHeight()) {
                dimensions += " x " + geometry.height();
            }
        } else {
            dimensions = geometry.vertices().size() + " points | " + geometry.width() + " x " + geometry.length();
            if (!geometry.fullHeight()) {
                dimensions += " x " + geometry.height();
            }
        }
        String status = !session.closed() ? "Click the first point to close"
                : validation != null && validation.valid() ? "Ready to confirm"
                : validation == null ? "Incomplete" : validation.reason();
        NamedTextColor textColor = session.closed() && validation != null && !validation.valid()
                ? NamedTextColor.RED : NamedTextColor.WHITE;
        player.sendActionBar(Component.text(dimensions + " | " + geometry.blockAreaEstimate() + " blocks² | " + status,
                textColor));
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
                    player.spawnParticle(Particle.DUST, x + 0.5, y, z + 0.5,
                            1, 0, 0, 0, 0, dust);
                    emitted++;
                }
            }
        }
    }
}
