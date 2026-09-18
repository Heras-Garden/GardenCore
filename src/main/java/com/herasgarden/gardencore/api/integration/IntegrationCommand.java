package com.herasgarden.gardencore.api.integration;

import java.util.UUID;

public record IntegrationCommand(
        UUID id,
        IntegrationCommandType type,
        String aggregateType,
        String aggregateId,
        String payload,
        long createdAt,
        int attempts
) {
}
