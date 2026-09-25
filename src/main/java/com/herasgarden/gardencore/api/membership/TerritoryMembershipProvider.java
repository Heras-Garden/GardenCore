package com.herasgarden.gardencore.api.membership;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Neutral Territory membership seam for optional domain plugins.
 *
 * GardenCivics owns citizenship records and registers this provider.
 * GardenLands may consume it without a compile-time dependency on GardenCivics.
 */
public interface TerritoryMembershipProvider {
    Optional<UUID> territoryClaimOf(UUID identityId);

    void setMembership(UUID identityId, UUID territoryClaimId) throws SQLException;

    boolean clearMembership(UUID identityId) throws SQLException;

    List<UUID> members(UUID territoryClaimId);
}
