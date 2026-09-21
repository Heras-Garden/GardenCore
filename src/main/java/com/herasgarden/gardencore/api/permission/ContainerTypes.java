package com.herasgarden.gardencore.api.permission;

import org.bukkit.Material;

/** Shared supported-container classification for Garden protection and land settings. */
public final class ContainerTypes {
    private ContainerTypes() {
    }

    public static boolean supported(Material type) {
        if (type == null) return false;
        String name = type.name();
        return type == Material.CHEST
                || type == Material.TRAPPED_CHEST
                || type == Material.BARREL
                || name.endsWith("COPPER_CHEST");
    }
}
