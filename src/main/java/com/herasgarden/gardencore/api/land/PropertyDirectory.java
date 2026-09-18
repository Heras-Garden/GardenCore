package com.herasgarden.gardencore.api.land;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only property/address/mailbox directory implemented by GardenLands.
 *
 * Domain plugins such as GardenPost should use this service instead of
 * querying GardenCore's transitional property tables directly.
 */
public interface PropertyDirectory {
    Optional<PropertyAddress> find(UUID propertyId) throws SQLException;

    Optional<PropertyAddress> findByClaim(UUID claimId) throws SQLException;

    Optional<PropertyAddress> resolveAddress(
            String scopeKey,
            String road,
            String number,
            String unitLabel
    ) throws SQLException;

    Optional<PropertyMailbox> mailbox(UUID propertyId) throws SQLException;

    Optional<PropertyMailbox> mailboxAt(UUID worldId, int x, int y, int z) throws SQLException;

    List<PropertyMailbox> mailboxesOwnedBy(UUID playerId) throws SQLException;

    List<PropertyAddress> listedForSale() throws SQLException;

    boolean isOwnedBy(UUID propertyId, UUID playerId) throws SQLException;
}
