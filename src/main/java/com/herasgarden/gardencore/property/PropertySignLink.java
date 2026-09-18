package com.herasgarden.gardencore.property;

import java.util.UUID;

public record PropertySignLink(BlockPosition position, UUID propertyId, UUID createdBy, String style) {
    public PropertySignStyle signStyle() {
        return PropertySignStyle.from(style);
    }
}
