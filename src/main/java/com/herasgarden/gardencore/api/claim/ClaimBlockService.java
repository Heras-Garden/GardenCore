package com.herasgarden.gardencore.api.claim;

import java.sql.SQLException;
import java.util.UUID;

/**
 * Public claim-block economy service.
 *
 * The quote is dynamic and is derived from the current Garden economy. Domain
 * plugins should use this service instead of duplicating claim-block pricing.
 */
public interface ClaimBlockService {
    long pricePerBlock();

    long quote(long blocks);

    boolean purchase(UUID playerId, long blocks) throws SQLException;
}
