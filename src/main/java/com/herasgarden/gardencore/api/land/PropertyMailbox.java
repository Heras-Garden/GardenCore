package com.herasgarden.gardencore.api.land;

import java.util.UUID;

/**
 * Registered physical mailbox for a property address.
 */
public record PropertyMailbox(
        UUID propertyId,
        UUID claimId,
        UUID worldId,
        String worldName,
        int x,
        int y,
        int z,
        String address
) {
}
