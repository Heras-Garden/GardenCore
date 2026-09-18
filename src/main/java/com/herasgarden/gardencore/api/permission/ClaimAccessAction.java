package com.herasgarden.gardencore.api.permission;

/**
 * Claim-level actions that a domain plugin may grant or deny without becoming
 * the claim owner. GardenLands rentals use this for temporary tenant access.
 */
public enum ClaimAccessAction {
    BUILD,
    BREAK,
    DOOR_USE,
    CONTAINER_OPEN,
    CONTAINER_INSERT,
    CONTAINER_TAKE,
    CONTAINER_BREAK
}
