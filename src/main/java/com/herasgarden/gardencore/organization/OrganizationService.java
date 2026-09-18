package com.herasgarden.gardencore.organization;

import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class OrganizationService {
    private final OrganizationRepository repository;
    private final Map<UUID, Organization> byId = new ConcurrentHashMap<>();
    private final Map<String, UUID> byTypedName = new ConcurrentHashMap<>();
    private final Map<String, UUID> byName = new ConcurrentHashMap<>();

    public OrganizationService(OrganizationRepository repository) {
        this.repository = repository;
    }

    public void load() throws SQLException {
        byId.clear();
        byTypedName.clear();
        byName.clear();
        for (Organization organization : repository.loadAll()) {
            cache(organization);
        }
    }

    public Collection<Organization> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public Organization get(UUID id) {
        return byId.get(id);
    }

    public Organization find(OrganizationType type, String name) {
        UUID id = byTypedName.get(key(type, name));
        return id == null ? null : byId.get(id);
    }

    public Organization findByName(String name) {
        UUID id = byName.get(Organization.normalize(name));
        return id == null ? null : byId.get(id);
    }

    public Organization createCompany(Player founder, String name) throws SQLException {
        validateName(name);
        if (findByName(name) != null) {
            throw new IllegalArgumentException("That name is already in use.");
        }
        Organization organization = repository.insert(OrganizationType.COMPANY, cleanName(name), founder.getUniqueId());
        cache(organization);
        return organization;
    }

    public Organization createGovernment(UUID founderId, String name) throws SQLException {
        validateName(name);
        if (findByName(name) != null) {
            throw new IllegalArgumentException("That name is already in use.");
        }
        Organization organization = repository.insert(OrganizationType.GOVERNMENT, cleanName(name), founderId);
        cache(organization);
        return organization;
    }

    public boolean has(Player player, Organization organization, OrganizationPermission permission) {
        return player != null && organization != null && organization.has(player.getUniqueId(), permission);
    }

    private void cache(Organization organization) {
        byId.put(organization.id(), organization);
        byTypedName.put(key(organization.type(), organization.name()), organization.id());
        byName.put(organization.nameKey(), organization.id());
    }

    private String key(OrganizationType type, String name) {
        return type.name() + ":" + Organization.normalize(name);
    }

    private void validateName(String name) {
        String clean = cleanName(name);
        if (clean.length() < 3 || clean.length() > 48) {
            throw new IllegalArgumentException("Organization names must be 3 to 48 characters.");
        }
        if (!clean.matches("[A-Za-z0-9 '&.-]+")) {
            throw new IllegalArgumentException("Organization names may use letters, numbers, spaces, apostrophes, &, periods, and hyphens.");
        }
    }

    private String cleanName(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ");
    }
}
