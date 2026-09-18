package com.herasgarden.gardencore.property;

import org.bukkit.Location;

import java.util.UUID;

public record BlockPosition(UUID worldId, String worldName, int x, int y, int z) {
    public static BlockPosition from(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("A block position requires a world.");
        }
        return new BlockPosition(location.getWorld().getUID(), location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public String key() {
        return worldId + ":" + x + ":" + y + ":" + z;
    }
}
