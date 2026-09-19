package com.herasgarden.gardencore.claim;

import com.herasgarden.gardencore.GardenCore;
import com.herasgarden.gardencore.api.claim.ClaimTransferPolicy;
import com.herasgarden.gardencore.api.permission.AccessDecision;
import com.herasgarden.gardencore.api.permission.ClaimAccessAction;
import com.herasgarden.gardencore.api.permission.ClaimAccessPolicy;
import com.herasgarden.gardencore.database.ClaimRepository;
import com.herasgarden.gardencore.organization.Organization;
import com.herasgarden.gardencore.organization.OrganizationPermission;
import com.herasgarden.gardencore.organization.OrganizationService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ClaimService {
    private final GardenCore plugin;
    private final ClaimRepository repository;
    private final OrganizationService organizations;
    private final Map<UUID, Claim> claims = new ConcurrentHashMap<>();
    private ClaimProfileService profiles;

    public ClaimService(GardenCore plugin, ClaimRepository repository, OrganizationService organizations) {
        this.plugin = plugin;
        this.repository = repository;
        this.organizations = organizations;
    }

    public void setProfiles(ClaimProfileService profiles) {
        this.profiles = profiles;
    }

    public void load() throws SQLException {
        claims.clear();
        for (Claim claim : repository.loadAll()) {
            claims.put(claim.id(), claim);
        }
    }

    public Collection<Claim> all() {
        return Collections.unmodifiableCollection(claims.values());
    }

    public int size() {
        return claims.size();
    }

    public Claim get(UUID id) {
        return claims.get(id);
    }

    public Claim findManagedParent(Player player, ClaimGeometry geometry) {
        return findManagedParent(player, geometry, null);
    }

    public Claim findManagedParent(Player player, ClaimGeometry geometry, ClaimType requiredType) {
        if (player == null || geometry == null) {
            return null;
        }
        return claims.values().stream()
                .filter(claim -> requiredType == null || claim.type() == requiredType)
                .filter(claim -> claim.geometry().containsGeometry(geometry))
                .filter(claim -> canManage(player, claim))
                .max(Comparator.comparingInt(this::depth))
                .orElse(null);
    }

    public boolean isSiblingNameAvailable(UUID parentId, ClaimType type, String name) {
        if (type == null || name == null || name.isBlank()) {
            return false;
        }
        String normalized = name.trim();
        return claims.values().stream()
                .filter(claim -> claim.type() == type)
                .filter(claim -> Objects.equals(parentId, claim.parentId()))
                .map(Claim::name)
                .filter(Objects::nonNull)
                .noneMatch(existing -> existing.equalsIgnoreCase(normalized));
    }

    public Claim findAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return claims.values().stream()
                .filter(claim -> claim.geometry().contains(location))
                .max(Comparator.comparingInt(this::depth))
                .orElse(null);
    }

    public List<Claim> findAllAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return List.of();
        }
        return claims.values().stream()
                .filter(claim -> claim.geometry().contains(location))
                .sorted(Comparator.comparingInt(this::depth).reversed())
                .toList();
    }

    public boolean isSameOrAncestor(Claim possibleAncestor, Claim claim) {
        if (possibleAncestor == null || claim == null) {
            return false;
        }
        return possibleAncestor.id().equals(claim.id()) || isAncestor(possibleAncestor, claim);
    }

    public int depth(Claim claim) {
        int depth = 0;
        UUID parent = claim.parentId();
        Set<UUID> visited = new HashSet<>();
        while (parent != null && visited.add(parent)) {
            Claim parentClaim = claims.get(parent);
            if (parentClaim == null) {
                break;
            }
            depth++;
            parent = parentClaim.parentId();
        }
        return depth;
    }

    public ClaimValidation validate(ClaimGeometry geometry, ClaimType type, ClaimOwnerType ownerType,
                                    UUID ownerId, UUID parentId) {
        if (geometry.vertices().size() > plugin.getConfig().getInt("claims.maximum-vertices", 64)) {
            return ClaimValidation.invalid("This claim has too many corners.");
        }
        if (!isSimplePolygon(geometry.vertices())) {
            return ClaimValidation.invalid("The claim boundary crosses itself.");
        }

        long area = geometry.blockAreaEstimate();
        if (area < plugin.getConfig().getLong("claims.minimum-area", 4L)) {
            return ClaimValidation.invalid("This claim is too small.");
        }

        if (type == ClaimType.TERRITORY) {
            long minimum = plugin.getConfig().getLong("claims.territory.minimum-area", 10000L);
            int minSpan = plugin.getConfig().getInt("claims.territory.minimum-span", 100);
            if (area < minimum || geometry.width() < minSpan || geometry.length() < minSpan) {
                return ClaimValidation.invalid("A territory must span at least " + minSpan + " x " + minSpan
                        + " blocks and contain at least " + minimum + " blocks².");
            }
        }

        Claim parent = parentId == null ? null : claims.get(parentId);
        if (parentId != null) {
            if (parent == null) {
                return ClaimValidation.invalid("The parent claim no longer exists.");
            }
            if (!parent.geometry().containsGeometry(geometry)) {
                return ClaimValidation.invalid("The claim must stay completely inside its parent claim.");
            }
        }

        if (type == ClaimType.DISTRICT && (parent == null || parent.type() != ClaimType.TERRITORY)) {
            return ClaimValidation.invalid("A district must be completely inside a territory claim that you manage.");
        }
        if (type == ClaimType.UNIT && (parent == null || parent.type() != ClaimType.PROPERTY)) {
            return ClaimValidation.invalid("A unit must be completely inside a property claim that you manage.");
        }

        for (Claim other : claims.values()) {
            if (!geometry.overlaps3D(other.geometry())) {
                continue;
            }
            if (parent != null && other.id().equals(parent.id())) {
                continue;
            }
            if (parent != null && isAncestor(other, parent)) {
                continue;
            }
            // Administrative boundaries may contain existing independent lower-level claims.
            // Those claims keep their current owner and parent until they are voluntarily transferred.
            if (type == ClaimType.TERRITORY && other.type() != ClaimType.TERRITORY) {
                continue;
            }
            if (type == ClaimType.DISTRICT
                    && other.type() != ClaimType.TERRITORY
                    && other.type() != ClaimType.DISTRICT) {
                continue;
            }
            return ClaimValidation.invalid("This area overlaps another claim.");
        }

        if (ownerType == ClaimOwnerType.COMPANY
                && type != ClaimType.PROPERTY && type != ClaimType.UNIT) {
            return ClaimValidation.invalid("Companies may create property and unit claims only.");
        }

        int limit = ownershipLimit(type, ownerType);
        if (limit == 0) {
            return ClaimValidation.invalid("This owner type cannot create that kind of claim.");
        }
        if (limit > 0) {
            long existing = type == ClaimType.TERRITORY && ownerType == ClaimOwnerType.PLAYER
                    ? claims.values().stream()
                            .filter(claim -> claim.type() == ClaimType.TERRITORY)
                            .filter(claim -> claim.createdBy().equals(ownerId))
                            .count()
                    : claims.values().stream()
                            .filter(claim -> claim.ownerType() == ownerType && claim.ownerId().equals(ownerId))
                            .filter(claim -> countTowardSameLimit(type, claim.type()))
                            .count();
            if (existing >= limit) {
                return ClaimValidation.invalid("You have reached the ownership limit for this claim type.");
            }
        }

        if (type == ClaimType.HOME && ownerType == ClaimOwnerType.PLAYER && profiles != null) {
            long available = profiles.availableHomeBlocks(ownerId);
            if (area > available) {
                return ClaimValidation.invalid("This home needs " + area + " claim blocks, but you only have "
                        + available + " available.");
            }
        }

        return ClaimValidation.ok();
    }

    public Claim create(ClaimType type, ClaimOwnerType ownerType, UUID ownerId, UUID parentId,
                        String name, ClaimTag tag, GovernmentType governmentType,
                        ClaimGeometry geometry, UUID createdBy) throws SQLException {
        ClaimValidation validation = validate(geometry, type, ownerType, ownerId, parentId);
        if (!validation.valid()) {
            throw new IllegalArgumentException(validation.reason());
        }

        Claim claim = new Claim(UUID.randomUUID(), type, ownerType, ownerId, parentId, name,
                tag, governmentType, geometry, createdBy, Instant.now());
        applyDefaultPermissions(claim);
        repository.insert(claim);
        claims.put(claim.id(), claim);
        return claim;
    }

    public void setTag(Claim claim, ClaimTag tag) throws SQLException {
        if (claim == null) throw new IllegalArgumentException("Claim is required.");
        if (tag != null && !tag.supports(claim.type())) {
            throw new IllegalArgumentException("That tag cannot be used with a "
                    + claim.type().name().toLowerCase(Locale.ROOT) + " claim.");
        }
        repository.updateTag(claim.id(), tag);
        claim.setTag(tag);
    }

    public void transferOwner(Claim claim, ClaimOwnerType ownerType, UUID ownerId) throws SQLException {
        if (claim == null || ownerType == null || ownerId == null) {
            throw new IllegalArgumentException("Claim and owner are required.");
        }
        repository.updateOwner(claim.id(), ownerType, ownerId);
        claim.setOwner(ownerType, ownerId);
    }

    public boolean hasChildren(Claim claim) {
        if (claim == null) {
            return false;
        }
        return claims.values().stream().anyMatch(other -> claim.id().equals(other.parentId()));
    }

    public void delete(Claim claim) throws SQLException {
        if (claim == null) {
            return;
        }
        if (hasChildren(claim)) {
            throw new IllegalArgumentException("Delete or move the child claims before deleting this claim.");
        }
        repository.delete(claim.id());
        claims.remove(claim.id());
    }

    /** Used when another repository completed the claim deletion in the same SQL transaction. */
    public void forgetDeletedClaim(UUID claimId) {
        if (claimId != null) {
            claims.remove(claimId);
        }
    }

    public void savePermissions(Claim claim) throws SQLException {
        repository.updatePermissions(claim);
    }

    public boolean isDirectPlayerOwner(Player player, Claim claim) {
        return claim != null && claim.ownerType() == ClaimOwnerType.PLAYER
                && claim.ownerId().equals(player.getUniqueId());
    }

    public boolean canManage(Player player, Claim claim) {
        if (player == null || claim == null) {
            return false;
        }
        if (player.hasPermission("gardencore.claim.admin") || isDirectPlayerOwner(player, claim)) {
            return true;
        }
        if (claim.ownerType() == ClaimOwnerType.PLAYER) {
            return false;
        }
        Organization organization = organizations.get(claim.ownerId());
        return organization != null && organizations.has(player, organization, OrganizationPermission.CLAIM_MANAGE);
    }

    public boolean can(Player player, Claim claim, ClaimPermission permission) {
        if (claim == null) {
            return true;
        }
        if (player.hasPermission("gardencore.claim.admin")) {
            return true;
        }

        boolean manager = canManage(player, claim);
        if (!manager) {
            AccessDecision delegated = delegatedAccess(player, claim.id(), permission);
            if (delegated == AccessDecision.ALLOW) {
                return true;
            }
            if (delegated == AccessDecision.DENY) {
                return false;
            }
        }

        ClaimSubject subject = manager ? ClaimSubject.OWNER : ClaimSubject.PUBLIC;
        Claim current = claim;
        Set<UUID> visited = new HashSet<>();
        while (current != null && visited.add(current.id())) {
            PermissionValue value = current.permission(subject, permission);
            if (value == PermissionValue.ALLOW) {
                return true;
            }
            if (value == PermissionValue.DENY) {
                return false;
            }
            current = current.parentId() == null ? null : claims.get(current.parentId());
        }

        if (subject == ClaimSubject.OWNER) {
            return true;
        }
        return plugin.getConfig().getBoolean("claims.server-defaults.public."
                + permission.name().toLowerCase(Locale.ROOT), false);
    }

    public Optional<String> transferBlockReason(UUID claimId) {
        RegisteredServiceProvider<ClaimTransferPolicy> registration =
                Bukkit.getServicesManager().getRegistration(ClaimTransferPolicy.class);
        if (registration == null || registration.getProvider() == null) {
            return Optional.empty();
        }
        try {
            Optional<String> result = registration.getProvider().blockReason(claimId);
            return result == null ? Optional.empty() : result;
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Claim transfer policy failed for " + claimId + ": " + exception.getMessage());
            return Optional.of("This claim cannot be transferred right now.");
        }
    }

    private AccessDecision delegatedAccess(Player player, UUID claimId, ClaimPermission permission) {
        RegisteredServiceProvider<ClaimAccessPolicy> registration =
                Bukkit.getServicesManager().getRegistration(ClaimAccessPolicy.class);
        if (registration == null || registration.getProvider() == null) {
            return AccessDecision.INHERIT;
        }
        try {
            ClaimAccessAction action = ClaimAccessAction.valueOf(permission.name());
            AccessDecision decision = registration.getProvider().decide(player, claimId, action);
            return decision == null ? AccessDecision.INHERIT : decision;
        } catch (RuntimeException exception) {
            return AccessDecision.INHERIT;
        }
    }

    public void cyclePublicPermission(Claim claim, ClaimPermission permission) throws SQLException {
        PermissionValue next = claim.permission(ClaimSubject.PUBLIC, permission).next();
        claim.setPermission(ClaimSubject.PUBLIC, permission, next);
        savePermissions(claim);
    }

    private void applyDefaultPermissions(Claim claim) {
        for (ClaimPermission permission : ClaimPermission.values()) {
            claim.setPermission(ClaimSubject.OWNER, permission, PermissionValue.ALLOW);
            if (claim.parentId() != null) {
                claim.setPermission(ClaimSubject.PUBLIC, permission, PermissionValue.INHERIT);
                continue;
            }
            String path = "claims.default-permissions.public." + permission.name().toLowerCase(Locale.ROOT);
            String configured = plugin.getConfig().getString(path, "DENY");
            PermissionValue value;
            try {
                value = PermissionValue.valueOf(configured.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                value = PermissionValue.DENY;
            }
            claim.setPermission(ClaimSubject.PUBLIC, permission, value);
        }
    }

    private int ownershipLimit(ClaimType type, ClaimOwnerType ownerType) {
        if (ownerType == ClaimOwnerType.GOVERNMENT) return -1;
        if (ownerType == ClaimOwnerType.COMPANY) {
            return switch (type) {
                case PROPERTY -> plugin.getConfig().getInt("claims.company-limits.properties", 50);
                case UNIT -> plugin.getConfig().getInt("claims.company-limits.units", 100);
                default -> 0;
            };
        }
        return switch (type) {
            case HOME -> plugin.getConfig().getInt("claims.player-limits.homes", 5);
            case PROPERTY -> plugin.getConfig().getInt("claims.player-limits.properties", 25);
            case UNIT -> plugin.getConfig().getInt("claims.player-limits.units", 50);
            case TERRITORY -> plugin.getConfig().getInt("claims.player-limits.territories", 1);
            case DISTRICT, PROTECTED -> -1;
        };
    }

    private boolean countTowardSameLimit(ClaimType requested, ClaimType existing) {
        return requested == existing;
    }

    public long usedHomeBlocks(UUID playerId) {
        return claims.values().stream()
                .filter(claim -> claim.type() == ClaimType.HOME)
                .filter(claim -> claim.ownerType() == ClaimOwnerType.PLAYER && claim.ownerId().equals(playerId))
                .mapToLong(claim -> claim.geometry().blockAreaEstimate())
                .sum();
    }

    public boolean insideTerritory(Claim claim) {
        return claim != null && claims.values().stream()
                .filter(other -> other.type() == ClaimType.TERRITORY)
                .anyMatch(other -> other.geometry().containsGeometry(claim.geometry()));
    }

    public void deleteInactiveHome(Claim home) throws SQLException {
        if (home == null || home.type() != ClaimType.HOME) return;
        for (Claim child : claims.values().stream().filter(other -> home.id().equals(other.parentId())).toList()) {
            repository.updateParent(child.id(), null);
            child.setParentId(null);
        }
        repository.delete(home.id());
        claims.remove(home.id());
    }

    private boolean isAncestor(Claim possibleAncestor, Claim claim) {
        UUID parent = claim.parentId();
        Set<UUID> visited = new HashSet<>();
        while (parent != null && visited.add(parent)) {
            if (parent.equals(possibleAncestor.id())) {
                return true;
            }
            Claim next = claims.get(parent);
            parent = next == null ? null : next.parentId();
        }
        return false;
    }

    private boolean isSimplePolygon(List<ClaimPoint> points) {
        int n = points.size();
        if (n < 3) {
            return false;
        }
        Set<Long> unique = new HashSet<>();
        for (ClaimPoint point : points) {
            if (!unique.add(point.packed())) {
                return false;
            }
        }

        for (int i = 0; i < n; i++) {
            ClaimPoint a1 = points.get(i);
            ClaimPoint a2 = points.get((i + 1) % n);
            for (int j = i + 1; j < n; j++) {
                if (Math.abs(i - j) <= 1 || (i == 0 && j == n - 1)) {
                    continue;
                }
                ClaimPoint b1 = points.get(j);
                ClaimPoint b2 = points.get((j + 1) % n);
                if (segmentsIntersect(a1, a2, b1, b2)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean segmentsIntersect(ClaimPoint a, ClaimPoint b, ClaimPoint c, ClaimPoint d) {
        long o1 = orientation(a, b, c);
        long o2 = orientation(a, b, d);
        long o3 = orientation(c, d, a);
        long o4 = orientation(c, d, b);
        return (o1 == 0 && onSegment(c, a, b)) || (o2 == 0 && onSegment(d, a, b))
                || (o3 == 0 && onSegment(a, c, d)) || (o4 == 0 && onSegment(b, c, d))
                || ((o1 > 0) != (o2 > 0) && (o3 > 0) != (o4 > 0));
    }

    private long orientation(ClaimPoint a, ClaimPoint b, ClaimPoint c) {
        return (long) (b.x() - a.x()) * (c.z() - a.z()) - (long) (b.z() - a.z()) * (c.x() - a.x());
    }

    private boolean onSegment(ClaimPoint p, ClaimPoint a, ClaimPoint b) {
        return p.x() >= Math.min(a.x(), b.x()) && p.x() <= Math.max(a.x(), b.x())
                && p.z() >= Math.min(a.z(), b.z()) && p.z() <= Math.max(a.z(), b.z());
    }
}
