package com.herasgarden.gardencore.api.land;

import java.util.UUID;

/**
 * Stable, human-readable identity for a GardenLands property.
 *
 * The address belongs to the property UUID, not to its current owner.
 */
public record PropertyAddress(
        UUID propertyId,
        UUID claimId,
        String scopeKey,
        String road,
        String number,
        String unitLabel,
        long price,
        boolean forSale
) {
    public String display() {
        String base = number + " " + road;
        return unitLabel == null || unitLabel.isBlank() ? base : base + ", " + unitLabel;
    }
}
