package com.herasgarden.gardencore.api.permission;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** Optional per-door policy supplied by GardenLands. */
public interface DoorAccessPolicy {
    AccessDecision decide(Player player, Block block, DoorAccessAction action);
}
