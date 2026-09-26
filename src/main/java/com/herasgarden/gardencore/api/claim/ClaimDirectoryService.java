package com.herasgarden.gardencore.api.claim;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only claim metadata directory implemented by GardenLands.
 *
 * Domain plugins use this service instead of reading Garden claim tables
 * directly. GardenLands is the canonical boundary for claim ancestry/type
 * queries even while legacy rows are migrated in place.
 */
public interface ClaimDirectoryService {
    Optional<ClaimSummary> find(UUID claimId);

    /**
     * Reconcile the provider's read cache after a durable ownership change.
     */
    default void refresh() throws SQLException {
    }

    default Optional<UUID> territoryAncestor(UUID claimId) {
        UUID current = claimId;
        java.util.HashSet<UUID> seen = new java.util.HashSet<>();
        while (current != null && seen.add(current)) {
            ClaimSummary claim = find(current).orElse(null);
            if (claim == null) return Optional.empty();
            if ("TERRITORY".equalsIgnoreCase(claim.type())) return Optional.of(claim.id());
            current = claim.parentId();
        }
        return Optional.empty();
    }
}
