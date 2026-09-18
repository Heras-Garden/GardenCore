package com.herasgarden.gardencore.claim;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class Claim {
    private final UUID id;
    private final ClaimType type;
    private ClaimOwnerType ownerType;
    private UUID ownerId;
    private UUID parentId;
    private String name;
    private final ClaimGeometry geometry;
    private final UUID createdBy;
    private final Instant createdAt;
    private final Map<ClaimSubject, EnumMap<ClaimPermission, PermissionValue>> permissions =
            new EnumMap<>(ClaimSubject.class);

    public Claim(UUID id, ClaimType type, ClaimOwnerType ownerType, UUID ownerId, UUID parentId,
                 String name, ClaimGeometry geometry, UUID createdBy, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.ownerType = Objects.requireNonNull(ownerType, "ownerType");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.parentId = parentId;
        this.name = name;
        this.geometry = Objects.requireNonNull(geometry, "geometry");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public UUID id() { return id; }
    public ClaimType type() { return type; }
    public ClaimOwnerType ownerType() { return ownerType; }
    public UUID ownerId() { return ownerId; }
    public UUID parentId() { return parentId; }
    public String name() { return name; }
    public ClaimGeometry geometry() { return geometry; }
    public UUID createdBy() { return createdBy; }
    public Instant createdAt() { return createdAt; }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
    }

    public void setOwner(ClaimOwnerType ownerType, UUID ownerId) {
        this.ownerType = Objects.requireNonNull(ownerType, "ownerType");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
    }

    public void setParentId(UUID parentId) {
        this.parentId = parentId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PermissionValue permission(ClaimSubject subject, ClaimPermission permission) {
        Map<ClaimPermission, PermissionValue> values = permissions.get(subject);
        return values == null ? PermissionValue.INHERIT : values.getOrDefault(permission, PermissionValue.INHERIT);
    }

    public void setPermission(ClaimSubject subject, ClaimPermission permission, PermissionValue value) {
        permissions.computeIfAbsent(subject, ignored -> new EnumMap<>(ClaimPermission.class))
                .put(permission, value);
    }

    public Map<ClaimSubject, EnumMap<ClaimPermission, PermissionValue>> permissions() {
        Map<ClaimSubject, EnumMap<ClaimPermission, PermissionValue>> copy = new EnumMap<>(ClaimSubject.class);
        permissions.forEach((subject, map) -> copy.put(subject, new EnumMap<>(map)));
        return copy;
    }
}
