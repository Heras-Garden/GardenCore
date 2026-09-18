package com.herasgarden.gardencore.api.organization;

import java.util.Map;
import java.util.UUID;

public record OrganizationView(
        UUID id,
        String type,
        String name,
        UUID founderId,
        long treasury,
        Map<String, OrganizationRoleView> roles,
        Map<UUID, String> memberRoles
) {
    public OrganizationView {
        roles = roles == null ? Map.of() : Map.copyOf(roles);
        memberRoles = memberRoles == null ? Map.of() : Map.copyOf(memberRoles);
    }
}
