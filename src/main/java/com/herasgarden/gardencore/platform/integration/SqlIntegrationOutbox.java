package com.herasgarden.gardencore.platform.integration;

import com.herasgarden.gardencore.api.integration.IntegrationEvent;
import com.herasgarden.gardencore.api.integration.IntegrationEventType;
import com.herasgarden.gardencore.api.integration.IntegrationOutbox;
import com.herasgarden.gardencore.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public final class SqlIntegrationOutbox implements IntegrationOutbox {
    private final DatabaseManager database;

    public SqlIntegrationOutbox(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public IntegrationEvent publish(
            IntegrationEventType type,
            String aggregateType,
            String aggregateId,
            String payload
    ) throws SQLException {
        if (type == null) {
            throw new IllegalArgumentException("Integration event type is required.");
        }
        if (aggregateType == null || aggregateType.isBlank()) {
            throw new IllegalArgumentException("Aggregate type is required.");
        }
        if (aggregateId == null || aggregateId.isBlank()) {
            throw new IllegalArgumentException("Aggregate id is required.");
        }

        UUID id = UUID.randomUUID();
        long now = System.currentTimeMillis();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO gc_integration_outbox "
                             + "(event_uuid, event_type, aggregate_type, aggregate_id, payload, created_at, attempts) "
                             + "VALUES (?, ?, ?, ?, ?, ?, 0)")) {
            statement.setString(1, id.toString());
            statement.setString(2, type.name());
            statement.setString(3, aggregateType);
            statement.setString(4, aggregateId);
            statement.setString(5, payload);
            statement.setLong(6, now);
            statement.executeUpdate();
        }

        return new IntegrationEvent(id, type, aggregateType, aggregateId, payload, now);
    }
}
