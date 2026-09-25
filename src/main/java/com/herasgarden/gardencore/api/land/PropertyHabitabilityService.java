package com.herasgarden.gardencore.api.land;

import java.sql.SQLException;
import java.util.UUID;

/**
 * Read-only GardenLands property habitability/occupancy checks used by
 * domain plugins such as GardenSociety.
 */
public interface PropertyHabitabilityService {
    boolean hasBed(UUID claimId);

    /**
     * True when a claim is listed for rent or has an active renter and should
     * not be purchased/occupied by Society.
     */
    boolean rentalUnavailable(UUID claimId) throws SQLException;
}
