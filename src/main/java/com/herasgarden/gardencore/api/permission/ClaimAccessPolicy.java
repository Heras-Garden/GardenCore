package com.herasgarden.gardencore.api.permission;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Optional domain policy for temporary/delegated claim access.
 *
 * INHERIT means GardenCore should continue with the normal owner/public claim
 * permission hierarchy.
 */
public interface ClaimAccessPolicy {
    AccessDecision decide(Player player, UUID claimId, ClaimAccessAction action);
}
