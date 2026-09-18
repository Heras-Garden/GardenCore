package com.herasgarden.gardencore.api.organization;

import java.util.Set;

public record OrganizationRoleView(
        String key,
        String displayName,
        boolean governmentPosition,
        long salary,
        Set<OrganizationCapability> capabilities
) {
    public OrganizationRoleView {
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }
}
