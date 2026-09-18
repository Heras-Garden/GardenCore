package com.herasgarden.gardencore.organization;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class OrganizationRole {
    private final String key;
    private final String displayName;
    private final boolean governmentPosition;
    private final long salary;
    private final Set<OrganizationPermission> permissions;

    public OrganizationRole(String key, String displayName, boolean governmentPosition, long salary,
                            Set<OrganizationPermission> permissions) {
        this.key = key;
        this.displayName = displayName;
        this.governmentPosition = governmentPosition;
        this.salary = salary;
        this.permissions = permissions == null || permissions.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(permissions));
    }

    public String key() { return key; }
    public String displayName() { return displayName; }
    public boolean governmentPosition() { return governmentPosition; }
    public long salary() { return salary; }
    public Set<OrganizationPermission> permissions() { return permissions; }

    public boolean has(OrganizationPermission permission) {
        return permissions.contains(permission);
    }
}
