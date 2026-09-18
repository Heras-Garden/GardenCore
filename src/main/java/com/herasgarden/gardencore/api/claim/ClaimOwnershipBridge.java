package com.herasgarden.gardencore.api.claim;

import java.sql.SQLException;
import java.util.UUID;

/**
 * Transitional bridge used while the claim engine is moving from GardenCore
 * into GardenLands. New domain code should not write gc_claims ownership rows
 * directly because GardenCore still owns the live in-memory claim cache.
 */
public interface ClaimOwnershipBridge {
    boolean transferPlayerClaim(UUID claimId, UUID expectedOwner, UUID newOwner) throws SQLException;
}
