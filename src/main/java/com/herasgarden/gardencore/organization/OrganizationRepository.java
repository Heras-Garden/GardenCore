package com.herasgarden.gardencore.organization;

import com.herasgarden.gardencore.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

public final class OrganizationRepository {
    private final DatabaseManager database;

    public OrganizationRepository(DatabaseManager database) {
        this.database = database;
    }

    public List<Organization> loadAll() throws SQLException {
        Map<UUID, Organization> organizations = new LinkedHashMap<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM gc_organizations");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                UUID id = UUID.fromString(result.getString("org_uuid"));
                organizations.put(id, new Organization(
                        id,
                        OrganizationType.valueOf(result.getString("org_type")),
                        result.getString("name"),
                        UUID.fromString(result.getString("founder_uuid")),
                        result.getLong("treasury"),
                        Instant.ofEpochMilli(result.getLong("created_at"))
                ));
            }
        }

        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM gc_org_roles");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                Organization organization = organizations.get(UUID.fromString(result.getString("org_uuid")));
                if (organization == null) {
                    continue;
                }
                String roleKey = result.getString("role_key");
                organization.putRole(new OrganizationRole(
                        roleKey,
                        result.getString("display_name"),
                        result.getInt("government_position") != 0,
                        result.getLong("salary"),
                        loadPermissions(organization.id(), roleKey)
                ));
            }
        }

        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM gc_org_members");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                Organization organization = organizations.get(UUID.fromString(result.getString("org_uuid")));
                if (organization != null) {
                    organization.putMember(UUID.fromString(result.getString("player_uuid")), result.getString("role_key"));
                }
            }
        }
        return new ArrayList<>(organizations.values());
    }

    private Set<OrganizationPermission> loadPermissions(UUID organizationId, String roleKey) throws SQLException {
        EnumSet<OrganizationPermission> permissions = EnumSet.noneOf(OrganizationPermission.class);
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT permission FROM gc_org_role_permissions WHERE org_uuid = ? AND role_key = ?")) {
            statement.setString(1, organizationId.toString());
            statement.setString(2, roleKey);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    try {
                        permissions.add(OrganizationPermission.valueOf(result.getString("permission")));
                    } catch (IllegalArgumentException ignored) {
                        // Unknown permissions are ignored so older/newer builds can coexist during upgrades.
                    }
                }
            }
        }
        return permissions;
    }

    public Organization insert(OrganizationType type, String name, UUID founderId) throws SQLException {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();
        Organization organization = new Organization(id, type, name, founderId, 0L, createdAt);
        EnumSet<OrganizationPermission> ownerPermissions = EnumSet.allOf(OrganizationPermission.class);
        OrganizationRole ownerRole = new OrganizationRole("owner", type == OrganizationType.COMPANY ? "Owner" : "Founder",
                type == OrganizationType.GOVERNMENT, 0L, ownerPermissions);

        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO gc_organizations (org_uuid, org_type, name, name_key, founder_uuid, treasury, created_at) "
                                + "VALUES (?, ?, ?, ?, ?, 0, ?)")) {
                    statement.setString(1, id.toString());
                    statement.setString(2, type.name());
                    statement.setString(3, name);
                    statement.setString(4, Organization.normalize(name));
                    statement.setString(5, founderId.toString());
                    statement.setLong(6, createdAt.toEpochMilli());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO gc_org_roles (org_uuid, role_key, display_name, government_position, salary, sort_order) "
                                + "VALUES (?, ?, ?, ?, ?, ?)")) {
                    statement.setString(1, id.toString());
                    statement.setString(2, ownerRole.key());
                    statement.setString(3, ownerRole.displayName());
                    statement.setInt(4, ownerRole.governmentPosition() ? 1 : 0);
                    statement.setLong(5, ownerRole.salary());
                    statement.setInt(6, 0);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO gc_org_role_permissions (org_uuid, role_key, permission) VALUES (?, ?, ?)")) {
                    for (OrganizationPermission permission : ownerPermissions) {
                        statement.setString(1, id.toString());
                        statement.setString(2, ownerRole.key());
                        statement.setString(3, permission.name());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO gc_org_members (org_uuid, player_uuid, role_key, joined_at) VALUES (?, ?, ?, ?)")) {
                    statement.setString(1, id.toString());
                    statement.setString(2, founderId.toString());
                    statement.setString(3, ownerRole.key());
                    statement.setLong(4, createdAt.toEpochMilli());
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }

        organization.putRole(ownerRole);
        organization.putMember(founderId, ownerRole.key());
        return organization;
    }

    public void upsertRole(UUID organizationId, OrganizationRole role) throws SQLException {
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                int changed;
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE gc_org_roles SET display_name = ?, government_position = ?, salary = ? "
                                + "WHERE org_uuid = ? AND role_key = ?")) {
                    update.setString(1, role.displayName());
                    update.setInt(2, role.governmentPosition() ? 1 : 0);
                    update.setLong(3, role.salary());
                    update.setString(4, organizationId.toString());
                    update.setString(5, role.key());
                    changed = update.executeUpdate();
                }
                if (changed == 0) {
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO gc_org_roles "
                                    + "(org_uuid, role_key, display_name, government_position, salary, sort_order) "
                                    + "VALUES (?, ?, ?, ?, ?, ?)")) {
                        insert.setString(1, organizationId.toString());
                        insert.setString(2, role.key());
                        insert.setString(3, role.displayName());
                        insert.setInt(4, role.governmentPosition() ? 1 : 0);
                        insert.setLong(5, role.salary());
                        insert.setInt(6, 100);
                        insert.executeUpdate();
                    }
                }

                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM gc_org_role_permissions WHERE org_uuid = ? AND role_key = ?")) {
                    delete.setString(1, organizationId.toString());
                    delete.setString(2, role.key());
                    delete.executeUpdate();
                }

                if (!role.permissions().isEmpty()) {
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO gc_org_role_permissions (org_uuid, role_key, permission) VALUES (?, ?, ?)")) {
                        for (OrganizationPermission permission : role.permissions()) {
                            insert.setString(1, organizationId.toString());
                            insert.setString(2, role.key());
                            insert.setString(3, permission.name());
                            insert.addBatch();
                        }
                        insert.executeBatch();
                    }
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void assignMember(UUID organizationId, UUID playerId, String roleKey) throws SQLException {
        long now = System.currentTimeMillis();
        try (Connection connection = database.connection()) {
            int changed;
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE gc_org_members SET role_key = ? WHERE org_uuid = ? AND player_uuid = ?")) {
                update.setString(1, roleKey);
                update.setString(2, organizationId.toString());
                update.setString(3, playerId.toString());
                changed = update.executeUpdate();
            }
            if (changed == 0) {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO gc_org_members (org_uuid, player_uuid, role_key, joined_at) VALUES (?, ?, ?, ?)")) {
                    insert.setString(1, organizationId.toString());
                    insert.setString(2, playerId.toString());
                    insert.setString(3, roleKey);
                    insert.setLong(4, now);
                    insert.executeUpdate();
                }
            }
        }
    }

    public boolean removeMember(UUID organizationId, UUID playerId) throws SQLException {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM gc_org_members WHERE org_uuid = ? AND player_uuid = ?")) {
            statement.setString(1, organizationId.toString());
            statement.setString(2, playerId.toString());
            return statement.executeUpdate() > 0;
        }
    }

    public boolean creditTreasury(UUID organizationId, long amount) throws SQLException {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE gc_organizations SET treasury = treasury + ? WHERE org_uuid = ?")) {
            statement.setLong(1, amount);
            statement.setString(2, organizationId.toString());
            return statement.executeUpdate() == 1;
        }
    }

    public boolean debitTreasury(UUID organizationId, long amount) throws SQLException {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE gc_organizations SET treasury = treasury - ? WHERE org_uuid = ? AND treasury >= ?")) {
            statement.setLong(1, amount);
            statement.setString(2, organizationId.toString());
            statement.setLong(3, amount);
            return statement.executeUpdate() == 1;
        }
    }

}
