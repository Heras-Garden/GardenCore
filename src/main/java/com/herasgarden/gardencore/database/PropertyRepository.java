package com.herasgarden.gardencore.database;

import com.herasgarden.gardencore.claim.ClaimOwnerType;
import com.herasgarden.gardencore.property.BlockPosition;
import com.herasgarden.gardencore.property.PropertySignLink;
import com.herasgarden.gardencore.property.SqlProperty;

import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class PropertyRepository {
    private final DatabaseManager database;

    public PropertyRepository(DatabaseManager database) {
        this.database = database;
    }

    public List<SqlProperty> loadProperties() throws SQLException {
        List<SqlProperty> properties = new ArrayList<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM gc_properties ORDER BY created_at ASC");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                properties.add(new SqlProperty(
                        UUID.fromString(result.getString("property_uuid")),
                        UUID.fromString(result.getString("claim_uuid")),
                        result.getString("scope_key"),
                        result.getString("road"),
                        result.getString("number"),
                        result.getString("unit_label"),
                        result.getLong("price"),
                        result.getInt("for_sale") != 0,
                        Instant.ofEpochMilli(result.getLong("created_at"))
                ));
            }
        }
        return properties;
    }

    public List<PropertySignLink> loadSigns() throws SQLException {
        List<PropertySignLink> signs = new ArrayList<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM gc_property_signs");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                BlockPosition position = new BlockPosition(
                        UUID.fromString(result.getString("world_uuid")),
                        result.getString("world_name"),
                        result.getInt("x"), result.getInt("y"), result.getInt("z"));
                signs.add(new PropertySignLink(position,
                        UUID.fromString(result.getString("property_uuid")),
                        UUID.fromString(result.getString("created_by")),
                        result.getString("sign_style")));
            }
        }
        return signs;
    }

    public Map<UUID, BlockPosition> loadMailboxes() throws SQLException {
        Map<UUID, BlockPosition> mailboxes = new HashMap<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM gc_property_mailboxes");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                UUID propertyId = UUID.fromString(result.getString("property_uuid"));
                mailboxes.put(propertyId, new BlockPosition(
                        UUID.fromString(result.getString("world_uuid")),
                        result.getString("world_name"),
                        result.getInt("x"), result.getInt("y"), result.getInt("z")));
            }
        }
        return mailboxes;
    }

    public void insert(SqlProperty property) throws SQLException {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO gc_properties (property_uuid, claim_uuid, scope_key, road, road_key, number, number_key, "
                             + "unit_label, unit_key, price, for_sale, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, property.id().toString());
            statement.setString(2, property.claimId().toString());
            statement.setString(3, property.scopeKey());
            statement.setString(4, property.road());
            statement.setString(5, SqlProperty.normalize(property.road()));
            statement.setString(6, property.number());
            statement.setString(7, SqlProperty.normalize(property.number()));
            statement.setString(8, property.unit());
            statement.setString(9, SqlProperty.normalize(property.unit()));
            statement.setLong(10, property.price());
            statement.setInt(11, property.forSale() ? 1 : 0);
            statement.setLong(12, property.createdAt().toEpochMilli());
            statement.executeUpdate();
        }
    }

    public void updateAddress(SqlProperty property, String road, String number, String unit) throws SQLException {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE gc_properties SET road = ?, road_key = ?, number = ?, number_key = ?, unit_label = ?, unit_key = ? "
                             + "WHERE property_uuid = ?")) {
            statement.setString(1, road);
            statement.setString(2, SqlProperty.normalize(road));
            statement.setString(3, number);
            statement.setString(4, SqlProperty.normalize(number));
            statement.setString(5, unit);
            statement.setString(6, SqlProperty.normalize(unit));
            statement.setString(7, property.id().toString());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Property address update affected no rows for " + property.id());
            }
        }
    }

    public void setSale(UUID propertyId, long price, boolean forSale) throws SQLException {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE gc_properties SET price = ?, for_sale = ? WHERE property_uuid = ?")) {
            statement.setLong(1, price);
            statement.setInt(2, forSale ? 1 : 0);
            statement.setString(3, propertyId.toString());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Property sale update affected no rows for " + propertyId);
            }
        }
    }

    public void bindSign(PropertySignLink sign) throws SQLException {
        try (Connection connection = database.connection()) {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM gc_property_signs WHERE world_uuid = ? AND x = ? AND y = ? AND z = ?")) {
                delete.setString(1, sign.position().worldId().toString());
                delete.setInt(2, sign.position().x());
                delete.setInt(3, sign.position().y());
                delete.setInt(4, sign.position().z());
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO gc_property_signs (world_uuid, world_name, x, y, z, property_uuid, created_by, sign_style) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                insert.setString(1, sign.position().worldId().toString());
                insert.setString(2, sign.position().worldName());
                insert.setInt(3, sign.position().x());
                insert.setInt(4, sign.position().y());
                insert.setInt(5, sign.position().z());
                insert.setString(6, sign.propertyId().toString());
                insert.setString(7, sign.createdBy().toString());
                insert.setString(8, sign.style());
                insert.executeUpdate();
            }
        }
    }

    public void removeSign(BlockPosition position) throws SQLException {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM gc_property_signs WHERE world_uuid = ? AND x = ? AND y = ? AND z = ?")) {
            statement.setString(1, position.worldId().toString());
            statement.setInt(2, position.x());
            statement.setInt(3, position.y());
            statement.setInt(4, position.z());
            statement.executeUpdate();
        }
    }

    public void setMailbox(UUID propertyId, BlockPosition position) throws SQLException {
        try (Connection connection = database.connection()) {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM gc_property_mailboxes WHERE property_uuid = ?")) {
                delete.setString(1, propertyId.toString());
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO gc_property_mailboxes (property_uuid, world_uuid, world_name, x, y, z) VALUES (?, ?, ?, ?, ?, ?)")) {
                insert.setString(1, propertyId.toString());
                insert.setString(2, position.worldId().toString());
                insert.setString(3, position.worldName());
                insert.setInt(4, position.x());
                insert.setInt(5, position.y());
                insert.setInt(6, position.z());
                insert.executeUpdate();
            }
        }
    }

    public void deletePropertyAndClaim(UUID propertyId, UUID claimId) throws SQLException {
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                deleteWhere(connection, "gc_property_mailboxes", "property_uuid", propertyId);
                deleteWhere(connection, "gc_property_signs", "property_uuid", propertyId);
                deleteWhere(connection, "gc_properties", "property_uuid", propertyId);
                deleteWhere(connection, "gc_territories", "claim_uuid", claimId);
                deleteWhere(connection, "gc_claim_permissions", "claim_uuid", claimId);
                deleteWhere(connection, "gc_claim_vertices", "claim_uuid", claimId);
                deleteWhere(connection, "gc_claims", "claim_uuid", claimId);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void deleteWhere(Connection connection, String table, String column, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + table + " WHERE " + column + " = ?")) {
            statement.setString(1, id.toString());
            statement.executeUpdate();
        }
    }

    public void completeSale(SqlProperty property, ClaimOwnerType newOwnerType, UUID newOwnerId) throws SQLException {
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement claimUpdate = connection.prepareStatement(
                        "UPDATE gc_claims SET owner_type = ?, owner_uuid = ? WHERE claim_uuid = ?")) {
                    claimUpdate.setString(1, newOwnerType.name());
                    claimUpdate.setString(2, newOwnerId.toString());
                    claimUpdate.setString(3, property.claimId().toString());
                    if (claimUpdate.executeUpdate() != 1) {
                        throw new SQLException("Claim transfer failed for property " + property.id());
                    }
                }
                try (PreparedStatement propertyUpdate = connection.prepareStatement(
                        "UPDATE gc_properties SET for_sale = 0 WHERE property_uuid = ? AND for_sale = 1")) {
                    propertyUpdate.setString(1, property.id().toString());
                    if (propertyUpdate.executeUpdate() != 1) {
                        throw new SQLException("Property is no longer available for sale.");
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

    public void rollbackSale(SqlProperty property, ClaimOwnerType previousType, UUID previousOwner) throws SQLException {
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement claimUpdate = connection.prepareStatement(
                        "UPDATE gc_claims SET owner_type = ?, owner_uuid = ? WHERE claim_uuid = ?")) {
                    claimUpdate.setString(1, previousType.name());
                    claimUpdate.setString(2, previousOwner.toString());
                    claimUpdate.setString(3, property.claimId().toString());
                    claimUpdate.executeUpdate();
                }
                try (PreparedStatement propertyUpdate = connection.prepareStatement(
                        "UPDATE gc_properties SET for_sale = 1 WHERE property_uuid = ?")) {
                    propertyUpdate.setString(1, property.id().toString());
                    propertyUpdate.executeUpdate();
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
}
