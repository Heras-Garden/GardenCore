package com.herasgarden.gardencore.claim;

import org.bukkit.Location;

public record ClaimPoint(int x, int z) {
    public static ClaimPoint from(Location location) {
        return new ClaimPoint(location.getBlockX(), location.getBlockZ());
    }

    public long packed() {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }
}
