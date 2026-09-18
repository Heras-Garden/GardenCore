package com.herasgarden.gardencore.api.integration;

import java.util.UUID;

public record IntegrationEvent(
        UUID id,
        IntegrationEventType type,
        String aggregateType,
        String aggregateId,
        String payload,
        long createdAt
) {
}
