package com.herasgarden.gardencore.api.permission;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Optional policy extension for container-specific overrides. GardenLands
 * registers an implementation when enabled. INHERIT falls back to the claim's
 * normal container permission.
 */
public interface ContainerAccessPolicy {
    AccessDecision decide(Player player, Block block, ContainerAccessAction action);
}
