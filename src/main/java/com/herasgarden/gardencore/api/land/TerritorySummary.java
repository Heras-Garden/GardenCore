package com.herasgarden.gardencore.api.land;

import java.util.UUID;

public record TerritorySummary(
        UUID claimId,
        String name
) {
}
