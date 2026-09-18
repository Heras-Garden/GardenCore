package com.herasgarden.gardencore.api.land;

import java.util.UUID;

public record PropertySignBinding(
        PropertyBlockPosition position,
        UUID propertyId,
        UUID createdBy,
        PropertySignKind kind
) {
}
