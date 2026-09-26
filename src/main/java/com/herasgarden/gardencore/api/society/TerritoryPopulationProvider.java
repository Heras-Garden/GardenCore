package com.herasgarden.gardencore.api.society;

import java.util.UUID;

/**
 * Read-only population view supplied by GardenSociety.
 * Counts permanent Society residents rather than declared player citizenship.
 */
public interface TerritoryPopulationProvider {
    int population(UUID territoryClaimId);
}
