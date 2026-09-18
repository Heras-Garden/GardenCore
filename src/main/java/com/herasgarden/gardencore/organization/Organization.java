package com.herasgarden.gardencore.organization;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class Organization {
    private final UUID id;
    private final OrganizationType type;
    private final String name;
    private final String nameKey;
    private final UUID founderId;
    private final Instant createdAt;
    private long treasury;
    private final Map<String, OrganizationRole> roles = new HashMap<>();
    private final Map<UUID, String> memberRoles = new HashMap<>();

    public Organization(UUID id, OrganizationType type, String name, UUID founderId, long treasury, Instant createdAt) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.nameKey = normalize(name);
        this.founderId = founderId;
        this.treasury = treasury;
        this.createdAt = createdAt;
    }

    public UUID id() { return id; }
    public OrganizationType type() { return type; }
    public String name() { return name; }
    public String nameKey() { return nameKey; }
    public UUID founderId() { return founderId; }
    public long treasury() { return treasury; }
    public Instant createdAt() { return createdAt; }
    public Map<String, OrganizationRole> roles() { return Collections.unmodifiableMap(roles); }
    public Map<UUID, String> memberRoles() { return Collections.unmodifiableMap(memberRoles); }

    public void setTreasury(long treasury) { this.treasury = Math.max(0L, treasury); }
    public void putRole(OrganizationRole role) { roles.put(role.key(), role); }
    public void putMember(UUID playerId, String roleKey) { memberRoles.put(playerId, roleKey); }

    public OrganizationRole roleOf(UUID playerId) {
        String roleKey = memberRoles.get(playerId);
        return roleKey == null ? null : roles.get(roleKey);
    }

    public boolean has(UUID playerId, OrganizationPermission permission) {
        OrganizationRole role = roleOf(playerId);
        return role != null && role.has(permission);
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
