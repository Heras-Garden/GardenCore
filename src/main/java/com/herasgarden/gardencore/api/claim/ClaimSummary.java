package com.herasgarden.gardencore.api.claim;

import java.util.UUID;

public record ClaimSummary(
        UUID id,
        String type,
        String tag,
        String name,
        String ownerType,
        UUID ownerId,
        UUID parentId,
        UUID worldId,
        int minY,
        int maxY,
        boolean fullHeight
) {
}
