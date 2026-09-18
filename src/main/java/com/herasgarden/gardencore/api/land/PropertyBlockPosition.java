package com.herasgarden.gardencore.api.land;

import org.bukkit.Location;

import java.util.UUID;

public record PropertyBlockPosition(
        UUID worldId,
        String worldName,
        int x,
        int y,
        int z
) {
    public static PropertyBlockPosition from(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("A property block position requires a world.");
        }
        return new PropertyBlockPosition(
                location.getWorld().getUID(),
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
    }
}
