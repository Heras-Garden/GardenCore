package com.herasgarden.gardencore.territory;

import com.herasgarden.gardencore.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class TerritoryRepository {
    private final DatabaseManager database;

    public TerritoryRepository(DatabaseManager database) {
        this.database = database;
    }

    public Set<String> loadNameKeys() throws SQLException {
        Set<String> keys = new HashSet<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("SELECT name_key FROM gc_territories");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                keys.add(result.getString("name_key"));
            }
        }
        return keys;
    }

    public boolean nameExists(String nameKey) throws SQLException {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM gc_territories WHERE name_key = ? LIMIT 1")) {
            statement.setString(1, nameKey);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    public void insert(UUID claimId, String name, String nameKey, String flagData) throws SQLException {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO gc_territories (claim_uuid, name, name_key, flag_data, created_at) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, claimId.toString());
            statement.setString(2, name);
            statement.setString(3, nameKey);
            statement.setString(4, flagData);
            statement.setLong(5, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    public void delete(UUID claimId) throws SQLException {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM gc_territories WHERE claim_uuid = ?")) {
            statement.setString(1, claimId.toString());
            statement.executeUpdate();
        }
    }
}
