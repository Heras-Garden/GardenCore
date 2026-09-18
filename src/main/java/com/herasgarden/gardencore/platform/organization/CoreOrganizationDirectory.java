package com.herasgarden.gardencore.platform.organization;

import com.herasgarden.gardencore.api.organization.OrganizationCapability;
import com.herasgarden.gardencore.api.organization.OrganizationDirectory;
import com.herasgarden.gardencore.api.organization.OrganizationRoleView;
import com.herasgarden.gardencore.api.organization.OrganizationView;
import com.herasgarden.gardencore.organization.Organization;
import com.herasgarden.gardencore.organization.OrganizationPermission;
import com.herasgarden.gardencore.organization.OrganizationRepository;
import com.herasgarden.gardencore.organization.OrganizationRole;
import com.herasgarden.gardencore.organization.OrganizationService;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class CoreOrganizationDirectory implements OrganizationDirectory {
    private final OrganizationService organizations;
    private final OrganizationRepository repository;

    public CoreOrganizationDirectory(OrganizationService organizations, OrganizationRepository repository) {
        this.organizations = organizations;
        this.repository = repository;
    }

    @Override
    public Optional<OrganizationView> find(UUID organizationId) {
        return Optional.ofNullable(organizations.get(organizationId)).map(this::view);
    }

    @Override
    public Optional<OrganizationView> findByName(String name) {
        return Optional.ofNullable(organizations.findByName(name)).map(this::view);
    }

    @Override
    public synchronized OrganizationView createGovernment(UUID founderId, String name) throws SQLException {
        Organization organization = organizations.createGovernment(founderId, name);
        return view(organization);
    }

    @Override
    public synchronized OrganizationView defineRole(
            UUID organizationId,
            String roleKey,
            String displayName,
            long salary,
            Set<OrganizationCapability> capabilities
    ) throws SQLException {
        Organization organization = require(organizationId);
        String key = normalizeRoleKey(roleKey);
        if ("owner".equals(key)) {
            throw new IllegalArgumentException("The founder role cannot be replaced.");
        }
        String display = displayName == null ? "" : displayName.trim().replaceAll("\\s+", " ");
        if (display.isBlank() || display.length() > 48) {
            throw new IllegalArgumentException("Role display names must be 1 to 48 characters.");
        }
        if (salary < 0) {
            throw new IllegalArgumentException("Role salary cannot be negative.");
        }

        Set<OrganizationPermission> permissions = capabilities == null
                ? Set.of()
                : capabilities.stream()
                .map(capability -> OrganizationPermission.valueOf(capability.name()))
                .collect(Collectors.toUnmodifiableSet());

        repository.upsertRole(
                organization.id(),
                new OrganizationRole(key, display, true, salary, permissions)
        );
        organizations.load();
        return view(require(organizationId));
    }

    @Override
    public synchronized OrganizationView assignMember(UUID organizationId, UUID playerId, String roleKey)
            throws SQLException {
        Organization organization = require(organizationId);
        String key = normalizeRoleKey(roleKey);
        if (!organization.roles().containsKey(key)) {
            throw new IllegalArgumentException("That government role does not exist.");
        }
        if ("owner".equals(key) && !organization.founderId().equals(playerId)) {
            throw new IllegalArgumentException("The founder role cannot be assigned to another player.");
        }
        repository.assignMember(organizationId, playerId, key);
        organizations.load();
        return view(require(organizationId));
    }

    @Override
    public synchronized boolean removeMember(UUID organizationId, UUID playerId) throws SQLException {
        Organization organization = require(organizationId);
        if (organization.founderId().equals(playerId)) {
            throw new IllegalArgumentException("The government founder cannot be removed.");
        }
        boolean changed = repository.removeMember(organizationId, playerId);
        if (changed) {
            organizations.load();
        }
        return changed;
    }

    @Override
    public boolean has(UUID organizationId, UUID playerId, OrganizationCapability capability) {
        Organization organization = organizations.get(organizationId);
        if (organization == null || capability == null) {
            return false;
        }
        if (organization.founderId().equals(playerId)) {
            return true;
        }
        return organization.has(playerId, OrganizationPermission.valueOf(capability.name()));
    }

    @Override
    public synchronized boolean creditTreasury(UUID organizationId, long amount) throws SQLException {
        requirePositive(amount);
        require(organizationId);
        boolean changed = repository.creditTreasury(organizationId, amount);
        if (changed) {
            organizations.load();
        }
        return changed;
    }

    @Override
    public synchronized boolean debitTreasury(UUID organizationId, long amount) throws SQLException {
        requirePositive(amount);
        require(organizationId);
        boolean changed = repository.debitTreasury(organizationId, amount);
        if (changed) {
            organizations.load();
        }
        return changed;
    }

    private Organization require(UUID organizationId) {
        Organization organization = organizations.get(organizationId);
        if (organization == null) {
            throw new IllegalArgumentException("That Garden organization does not exist.");
        }
        return organization;
    }

    private String normalizeRoleKey(String value) {
        String key = value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "-")
                .replaceAll("-+", "-");
        if (key.startsWith("-")) key = key.substring(1);
        if (key.endsWith("-")) key = key.substring(0, key.length() - 1);
        if (key.isBlank() || key.length() > 32) {
            throw new IllegalArgumentException("Role keys must be 1 to 32 letters, numbers, dashes, or underscores.");
        }
        return key;
    }

    private void requirePositive(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be a positive whole number of Obols.");
        }
    }

    private OrganizationView view(Organization organization) {
        Map<String, OrganizationRoleView> roles = new LinkedHashMap<>();
        organization.roles().forEach((key, role) -> roles.put(key, new OrganizationRoleView(
                role.key(),
                role.displayName(),
                role.governmentPosition(),
                role.salary(),
                role.permissions().stream()
                        .map(permission -> OrganizationCapability.valueOf(permission.name()))
                        .collect(Collectors.toUnmodifiableSet())
        )));
        return new OrganizationView(
                organization.id(),
                organization.type().name(),
                organization.name(),
                organization.founderId(),
                organization.treasury(),
                roles,
                Map.copyOf(organization.memberRoles())
        );
    }
}
