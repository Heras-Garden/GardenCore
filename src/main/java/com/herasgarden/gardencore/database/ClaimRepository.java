package com.herasgarden.gardencore.database;

import com.herasgarden.gardencore.claim.*;

import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class ClaimRepository {
    private final DatabaseManager database;

    public ClaimRepository(DatabaseManager database) {
        this.database = database;
    }

    public List<Claim> loadAll() throws SQLException {
        Map<UUID, BaseClaim> bases = new LinkedHashMap<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM gc_claims ORDER BY created_at ASC");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                UUID id = UUID.fromString(result.getString("claim_uuid"));
                String parent = result.getString("parent_uuid");
                bases.put(id, new BaseClaim(
                        id,
                        ClaimType.valueOf(result.getString("claim_type")),
                        ClaimOwnerType.valueOf(result.getString("owner_type")),
                        UUID.fromString(result.getString("owner_uuid")),
                        parent == null ? null : UUID.fromString(parent),
                        result.getString("name"),
                        UUID.fromString(result.getString("world_uuid")),
                        result.getString("world_name"),
                        result.getInt("min_y"),
                        result.getInt("max_y"),
                        result.getInt("full_height") != 0,
                        UUID.fromString(result.getString("created_by")),
                        Instant.ofEpochMilli(result.getLong("created_at"))
                ));
            }
        }

        Map<UUID, List<ClaimPoint>> vertices = new HashMap<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT claim_uuid, vertex_index, x, z FROM gc_claim_vertices ORDER BY claim_uuid, vertex_index");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                UUID id = UUID.fromString(result.getString("claim_uuid"));
                vertices.computeIfAbsent(id, ignored -> new ArrayList<>())
                        .add(new ClaimPoint(result.getInt("x"), result.getInt("z")));
            }
        }

        Map<UUID, List<StoredPermission>> storedPermissions = new HashMap<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT claim_uuid, subject, permission, value FROM gc_claim_permissions");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                UUID id = UUID.fromString(result.getString("claim_uuid"));
                storedPermissions.computeIfAbsent(id, ignored -> new ArrayList<>())
                        .add(new StoredPermission(
                                ClaimSubject.valueOf(result.getString("subject")),
                                ClaimPermission.valueOf(result.getString("permission")),
                                PermissionValue.valueOf(result.getString("value"))));
            }
        }

        List<Claim> claims = new ArrayList<>();
        for (BaseClaim base : bases.values()) {
            List<ClaimPoint> points = vertices.get(base.id());
            if (points == null || points.size() < 3) {
                continue;
            }
            ClaimGeometry geometry = new ClaimGeometry(base.worldId(), base.worldName(), points,
                    base.minY(), base.maxY(), base.fullHeight());
            Claim claim = new Claim(base.id(), base.type(), base.ownerType(), base.ownerId(), base.parentId(),
                    base.name(), geometry, base.createdBy(), base.createdAt());
            for (StoredPermission permission : storedPermissions.getOrDefault(base.id(), List.of())) {
                claim.setPermission(permission.subject(), permission.permission(), permission.value());
            }
            claims.add(claim);
        }
        return claims;
    }

    public void insert(Claim claim) throws SQLException {
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO gc_claims (claim_uuid, claim_type, owner_type, owner_uuid, parent_uuid, name, "
                                + "world_uuid, world_name, min_y, max_y, full_height, created_by, created_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    statement.setString(1, claim.id().toString());
                    statement.setString(2, claim.type().name());
                    statement.setString(3, claim.ownerType().name());
                    statement.setString(4, claim.ownerId().toString());
                    statement.setString(5, claim.parentId() == null ? null : claim.parentId().toString());
                    statement.setString(6, claim.name());
                    statement.setString(7, claim.geometry().worldId().toString());
                    statement.setString(8, claim.geometry().worldName());
                    statement.setInt(9, claim.geometry().minY());
                    statement.setInt(10, claim.geometry().maxY());
                    statement.setInt(11, claim.geometry().fullHeight() ? 1 : 0);
                    statement.setString(12, claim.createdBy().toString());
                    statement.setLong(13, claim.createdAt().toEpochMilli());
                    statement.executeUpdate();
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO gc_claim_vertices (claim_uuid, vertex_index, x, z) VALUES (?, ?, ?, ?)")) {
                    for (int index = 0; index < claim.geometry().vertices().size(); index++) {
                        ClaimPoint point = claim.geometry().vertices().get(index);
                        statement.setString(1, claim.id().toString());
                        statement.setInt(2, index);
                        statement.setInt(3, point.x());
                        statement.setInt(4, point.z());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }

                savePermissions(connection, claim);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }


    public void updateOwner(UUID claimId, ClaimOwnerType ownerType, UUID ownerId) throws SQLException {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE gc_claims SET owner_type = ?, owner_uuid = ? WHERE claim_uuid = ?")) {
            statement.setString(1, ownerType.name());
            statement.setString(2, ownerId.toString());
            statement.setString(3, claimId.toString());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Claim owner update affected no rows for " + claimId);
            }
        }
    }
    public void updatePermissions(Claim claim) throws SQLException {
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM gc_claim_permissions WHERE claim_uuid = ?")) {
                    delete.setString(1, claim.id().toString());
                    delete.executeUpdate();
                }
                savePermissions(connection, claim);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void delete(UUID id) throws SQLException {
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                deleteByClaim(connection, "gc_claim_permissions", id);
                deleteByClaim(connection, "gc_claim_vertices", id);
                deleteByClaim(connection, "gc_claims", id);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void savePermissions(Connection connection, Claim claim) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO gc_claim_permissions (claim_uuid, subject, permission, value) VALUES (?, ?, ?, ?)")) {
            for (Map.Entry<ClaimSubject, EnumMap<ClaimPermission, PermissionValue>> subjectEntry : claim.permissions().entrySet()) {
                for (Map.Entry<ClaimPermission, PermissionValue> permissionEntry : subjectEntry.getValue().entrySet()) {
                    if (permissionEntry.getValue() == PermissionValue.INHERIT) {
                        continue;
                    }
                    statement.setString(1, claim.id().toString());
                    statement.setString(2, subjectEntry.getKey().name());
                    statement.setString(3, permissionEntry.getKey().name());
                    statement.setString(4, permissionEntry.getValue().name());
                    statement.addBatch();
                }
            }
            statement.executeBatch();
        }
    }

    private void deleteByClaim(Connection connection, String table, UUID id) throws SQLException {
        String key = table.equals("gc_claims") ? "claim_uuid" : "claim_uuid";
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE " + key + " = ?")) {
            statement.setString(1, id.toString());
            statement.executeUpdate();
        }
    }

    private record BaseClaim(UUID id, ClaimType type, ClaimOwnerType ownerType, UUID ownerId, UUID parentId,
                             String name, UUID worldId, String worldName, int minY, int maxY, boolean fullHeight,
                             UUID createdBy, Instant createdAt) {}

    private record StoredPermission(ClaimSubject subject, ClaimPermission permission, PermissionValue value) {}
}
