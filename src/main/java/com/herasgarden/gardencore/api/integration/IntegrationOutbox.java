package com.herasgarden.gardencore.api.integration;

import java.sql.SQLException;

/**
 * Durable event outbox. Publishing succeeds only after the event is stored in
 * the Garden database, so Iris can safely consume it later even if Discord is
 * unavailable when the Minecraft action occurs.
 */
public interface IntegrationOutbox {
    IntegrationEvent publish(
            IntegrationEventType type,
            String aggregateType,
            String aggregateId,
            String payload
    ) throws SQLException;
}
