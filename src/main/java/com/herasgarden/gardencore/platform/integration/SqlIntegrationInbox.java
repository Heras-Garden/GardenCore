package com.herasgarden.gardencore.platform.integration;

import com.herasgarden.gardencore.api.integration.IntegrationCommand;
import com.herasgarden.gardencore.api.integration.IntegrationCommandType;
import com.herasgarden.gardencore.api.integration.IntegrationInbox;
import com.herasgarden.gardencore.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class SqlIntegrationInbox implements IntegrationInbox {
    private final DatabaseManager database;

    public SqlIntegrationInbox(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public List<IntegrationCommand> claimPending(Collection<IntegrationCommandType> types, int limit, long staleClaimMillis) throws SQLException {
        if (types == null || types.isEmpty()) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 100));
        long now = System.currentTimeMillis();
        long staleBefore = now - Math.max(30_000L, staleClaimMillis);
        List<UUID> candidates = new ArrayList<>();

        String placeholders = String.join(",", java.util.Collections.nCopies(types.size(), "?"));
        String sql = "SELECT command_uuid FROM gc_integration_commands "
                + "WHERE processed_at IS NULL AND attempts < 8 "
                + "AND (claimed_at IS NULL OR claimed_at < ?) "
                + "AND command_type IN (" + placeholders + ") "
                + "ORDER BY created_at ASC LIMIT " + safeLimit;
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setLong(index++, staleBefore);
            for (IntegrationCommandType type : types) {
                statement.setString(index++, type.name());
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    candidates.add(UUID.fromString(result.getString("command_uuid")));
                }
            }
        }

        List<IntegrationCommand> claimed = new ArrayList<>();
        for (UUID id : candidates) {
            try (Connection connection = database.connection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE gc_integration_commands SET claimed_at = ?, attempts = attempts + 1 "
                                 + "WHERE command_uuid = ? AND processed_at IS NULL "
                                 + "AND (claimed_at IS NULL OR claimed_at < ?)")) {
                statement.setLong(1, now);
                statement.setString(2, id.toString());
                statement.setLong(3, staleBefore);
                if (statement.executeUpdate() != 1) {
                    continue;
                }
            }

            try (Connection connection = database.connection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT command_uuid, command_type, aggregate_type, aggregate_id, payload, created_at, attempts "
                                 + "FROM gc_integration_commands WHERE command_uuid = ?")) {
                statement.setString(1, id.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        claimed.add(new IntegrationCommand(
                                id,
                                IntegrationCommandType.valueOf(result.getString("command_type")),
                                result.getString("aggregate_type"),
                                result.getString("aggregate_id"),
                                result.getString("payload"),
                                result.getLong("created_at"),
                                result.getInt("attempts")
                        ));
                    }
                }
            }
        }
        return List.copyOf(claimed);
    }

    @Override
    public void markProcessed(UUID commandId) throws SQLException {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE gc_integration_commands SET processed_at = ?, claimed_at = NULL, last_error = NULL "
                             + "WHERE command_uuid = ?")) {
            statement.setLong(1, System.currentTimeMillis());
            statement.setString(2, commandId.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public void release(UUID commandId, String error) throws SQLException {
        String detail = error == null ? "Unknown error" : error;
        if (detail.length() > 1000) detail = detail.substring(0, 1000);
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE gc_integration_commands SET claimed_at = NULL, last_error = ? "
                             + "WHERE command_uuid = ? AND processed_at IS NULL")) {
            statement.setString(1, detail);
            statement.setString(2, commandId.toString());
            statement.executeUpdate();
        }
    }
}
