package com.herasgarden.gardencore.claim;

public enum ClaimType {
    PROPERTY,
    TERRITORY,
    CITY,
    DISTRICT,
    GOVERNMENT,
    COMPANY,
    SHOP,
    FARM,
    BUILDING,
    APARTMENT,
    HOTEL_ROOM,
    VENUE,
    HARBOR,
    RAIL_STATION,
    PACKING_STATION,
    MULE_STATION,
    PROTECTED;

    public boolean defaultsToPolygon() {
        return this == TERRITORY || this == CITY || this == DISTRICT || this == APARTMENT;
    }

    public boolean defaultsToFullHeight() {
        return switch (this) {
            case APARTMENT, HOTEL_ROOM -> false;
            default -> true;
        };
    }
}
