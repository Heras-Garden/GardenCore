package com.herasgarden.gardencore.api.organization;

import java.sql.SQLException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface OrganizationDirectory {
    Optional<OrganizationView> find(UUID organizationId);

    Optional<OrganizationView> findByName(String name);

    OrganizationView createGovernment(UUID founderId, String name) throws SQLException;

    OrganizationView defineRole(
            UUID organizationId,
            String roleKey,
            String displayName,
            long salary,
            Set<OrganizationCapability> capabilities
    ) throws SQLException;

    OrganizationView assignMember(UUID organizationId, UUID playerId, String roleKey) throws SQLException;

    boolean removeMember(UUID organizationId, UUID playerId) throws SQLException;

    boolean has(UUID organizationId, UUID playerId, OrganizationCapability capability);

    boolean creditTreasury(UUID organizationId, long amount) throws SQLException;

    boolean debitTreasury(UUID organizationId, long amount) throws SQLException;
}
