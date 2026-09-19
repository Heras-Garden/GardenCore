package com.herasgarden.gardencore.claim;

import java.util.Locale;

public enum ClaimType {
    HOME,
    PROPERTY,
    UNIT,
    TERRITORY,
    DISTRICT,
    PROTECTED;

    public boolean defaultsToPolygon() {
        return this == UNIT || this == TERRITORY || this == DISTRICT;
    }

    public boolean defaultsToFullHeight() {
        return this != UNIT;
    }

    public static ClaimType fromStorage(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Claim type is missing.");
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "HOME" -> HOME;
            case "PROPERTY", "SHOP", "FARM", "BUILDING", "VENUE", "HARBOR",
                    "RAIL_STATION", "PACKING_STATION", "MULE_STATION", "GOVERNMENT", "COMPANY" -> PROPERTY;
            case "UNIT", "APARTMENT", "HOTEL_ROOM" -> UNIT;
            case "TERRITORY" -> TERRITORY;
            case "DISTRICT", "CITY" -> DISTRICT;
            case "PROTECTED" -> PROTECTED;
            default -> throw new IllegalArgumentException("Unknown stored claim type: " + raw);
        };
    }
}
