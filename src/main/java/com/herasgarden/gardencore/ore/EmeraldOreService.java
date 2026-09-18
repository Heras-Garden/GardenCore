package com.herasgarden.gardencore.ore;

import com.herasgarden.gardencore.GardenCore;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public final class EmeraldOreService implements Listener {
    private final GardenCore plugin;
    private final Deque<Chunk> queue = new ArrayDeque<>();
    private final Set<String> queued = new HashSet<>();
    private final NamespacedKey migratedKey;
    private BukkitTask task;

    public EmeraldOreService(GardenCore plugin) {
        this.plugin = plugin;
        int version = Math.max(1, plugin.getConfig().getInt("ore-migration.version", 1));
        this.migratedKey = new NamespacedKey(plugin, "emerald-ore-migration-v" + version);
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("ore-migration.enabled", true)) {
            return;
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (World world : plugin.getServer().getWorlds()) {
            if (world.getEnvironment() != World.Environment.NORMAL) {
                continue;
            }
            for (Chunk chunk : world.getLoadedChunks()) {
                queue(chunk);
            }
        }

        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::processQueue, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        queue.clear();
        queued.clear();
    }

    @EventHandler
    public void onChunkPopulate(ChunkPopulateEvent event) {
        queue(event.getChunk());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (event.getWorld().getEnvironment() == World.Environment.NORMAL) {
            queue(event.getChunk());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEmeraldOreBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.EMERALD_ORE) {
            event.setCancelled(true);
            block.setType(Material.STONE, false);
        } else if (block.getType() == Material.DEEPSLATE_EMERALD_ORE) {
            event.setCancelled(true);
            block.setType(Material.DEEPSLATE, false);
        }
    }

    private void queue(Chunk chunk) {
        if (chunk == null || chunk.getWorld().getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        if (isMigrated(chunk)) {
            return;
        }
        String key = key(chunk);
        if (queued.add(key)) {
            queue.addLast(chunk);
        }
    }

    private void processQueue() {
        int chunksPerTick = Math.max(1, plugin.getConfig().getInt("ore-migration.chunks-per-tick", 1));
        for (int i = 0; i < chunksPerTick; i++) {
            Chunk chunk = queue.pollFirst();
            if (chunk == null) {
                return;
            }
            queued.remove(key(chunk));
            if (chunk.isLoaded()) {
                replaceEmeraldOre(chunk);
            }
        }
    }

    private void replaceEmeraldOre(Chunk chunk) {
        World world = chunk.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return;
        }

        int configuredMin = plugin.getConfig().getInt("ore-migration.min-y", -16);
        int configuredMax = plugin.getConfig().getInt("ore-migration.max-y", 320);
        int minY = Math.max(world.getMinHeight(), configuredMin);
        int maxYExclusive = Math.min(world.getMaxHeight(), configuredMax + 1);
        ChunkSnapshot snapshot = chunk.getChunkSnapshot();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxYExclusive; y++) {
                    Material type = snapshot.getBlockType(x, y, z);
                    if (type == Material.EMERALD_ORE) {
                        chunk.getBlock(x, y, z).setType(Material.STONE, false);
                    } else if (type == Material.DEEPSLATE_EMERALD_ORE) {
                        chunk.getBlock(x, y, z).setType(Material.DEEPSLATE, false);
                    }
                }
            }
        }

        chunk.getPersistentDataContainer().set(migratedKey, PersistentDataType.BYTE, (byte) 1);
    }

    private boolean isMigrated(Chunk chunk) {
        Byte migrated = chunk.getPersistentDataContainer().get(migratedKey, PersistentDataType.BYTE);
        return migrated != null && migrated == (byte) 1;
    }

    private String key(Chunk chunk) {
        return chunk.getWorld().getUID() + ":" + chunk.getX() + ":" + chunk.getZ();
    }
}
