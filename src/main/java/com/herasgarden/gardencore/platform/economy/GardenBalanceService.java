package com.herasgarden.gardencore.platform.economy;

import com.herasgarden.gardencore.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Integer Obol ledger owned by GardenCore. This is the canonical balance store
 * once EssentialsX economy is removed.
 */
public final class GardenBalanceService {
    private final DatabaseManager database;
    private final long startingBalance;

    public GardenBalanceService(DatabaseManager database, long startingBalance) {
        this.database = database;
        this.startingBalance = Math.max(0L, startingBalance);
    }

    public long balance(UUID playerId) throws SQLException {
        ensureAccount(playerId);
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT balance FROM gc_player_balances WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong("balance") : 0L;
            }
        }
    }

    public boolean has(UUID playerId, long amount) throws SQLException {
        return amount >= 0 && balance(playerId) >= amount;
    }

    public boolean deposit(UUID playerId, long amount) throws SQLException {
        if (amount < 0) return false;
        ensureAccount(playerId);
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE gc_player_balances SET balance = balance + ?, updated_at = ? WHERE player_uuid = ?")) {
            statement.setLong(1, amount);
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, playerId.toString());
            return statement.executeUpdate() == 1;
        }
    }

    public boolean withdraw(UUID playerId, long amount) throws SQLException {
        if (amount < 0) return false;
        ensureAccount(playerId);
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE gc_player_balances SET balance = balance - ?, updated_at = ? "
                             + "WHERE player_uuid = ? AND balance >= ?")) {
            statement.setLong(1, amount);
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, playerId.toString());
            statement.setLong(4, amount);
            return statement.executeUpdate() == 1;
        }
    }

    public void set(UUID playerId, long amount) throws SQLException {
        long safe = Math.max(0L, amount);
        ensureAccount(playerId);
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE gc_player_balances SET balance = ?, updated_at = ? WHERE player_uuid = ?")) {
            statement.setLong(1, safe);
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, playerId.toString());
            statement.executeUpdate();
        }
    }

    public boolean migrationComplete() throws SQLException {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT meta_value FROM gc_platform_meta WHERE meta_key = 'economy.vault-migration'")) {
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getString("meta_value") != null
                        && result.getString("meta_value").toLowerCase(java.util.Locale.ROOT).startsWith("complete");
            }
        }
    }

    public void markMigrationComplete(String providerName) throws SQLException {
        String value = "complete:" + (providerName == null ? "unknown" : providerName);
        long now = System.currentTimeMillis();
        try (Connection connection = database.connection()) {
            int changed;
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE gc_platform_meta SET meta_value = ?, updated_at = ? "
                            + "WHERE meta_key = 'economy.vault-migration'")) {
                update.setString(1, value);
                update.setLong(2, now);
                changed = update.executeUpdate();
            }
            if (changed == 0) {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO gc_platform_meta (meta_key, meta_value, updated_at) VALUES (?, ?, ?)")) {
                    insert.setString(1, "economy.vault-migration");
                    insert.setString(2, value);
                    insert.setLong(3, now);
                    insert.executeUpdate();
                }
            }
        }
    }

    private void ensureAccount(UUID playerId) throws SQLException {
        try (Connection connection = database.connection();
             PreparedStatement query = connection.prepareStatement(
                     "SELECT 1 FROM gc_player_balances WHERE player_uuid = ?")) {
            query.setString(1, playerId.toString());
            try (ResultSet result = query.executeQuery()) {
                if (result.next()) return;
            }
        }

        try (Connection connection = database.connection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO gc_player_balances (player_uuid, balance, updated_at) VALUES (?, ?, ?)")) {
            insert.setString(1, playerId.toString());
            insert.setLong(2, startingBalance);
            insert.setLong(3, System.currentTimeMillis());
            try {
                insert.executeUpdate();
            } catch (SQLException duplicate) {
                // Another operation may have created the account between the
                // existence check and insert. Re-read on the caller path.
            }
        }
    }
}
