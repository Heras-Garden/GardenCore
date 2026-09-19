package com.herasgarden.gardencore.claim;

import java.util.Locale;

public enum ClaimTag {
    RESIDENTIAL,
    FARM,
    SHOP,
    BUILDING,
    APARTMENT_BUILDING,
    HOTEL,
    VENUE,
    HARBOR,
    RAIL_STATION,
    PACKING_STATION,
    MULE_STATION,
    CIVIC,
    COMPANY,
    APARTMENT,
    HOTEL_ROOM,
    OFFICE,
    SHOP_UNIT,
    STORAGE;

    public boolean supports(ClaimType type) {
        return switch (type) {
            case PROPERTY -> switch (this) {
                case RESIDENTIAL, FARM, SHOP, BUILDING, APARTMENT_BUILDING, HOTEL, VENUE,
                        HARBOR, RAIL_STATION, PACKING_STATION, MULE_STATION, CIVIC, COMPANY -> true;
                default -> false;
            };
            case UNIT -> switch (this) {
                case APARTMENT, HOTEL_ROOM, OFFICE, SHOP_UNIT, STORAGE -> true;
                default -> false;
            };
            default -> false;
        };
    }

    public static ClaimTag parse(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("none")) {
            return null;
        }
        String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return ClaimTag.valueOf(key);
    }

    public static ClaimTag legacyTag(String rawType) {
        if (rawType == null) return null;
        return switch (rawType.trim().toUpperCase(Locale.ROOT)) {
            case "SHOP" -> SHOP;
            case "FARM" -> FARM;
            case "BUILDING" -> BUILDING;
            case "APARTMENT" -> APARTMENT;
            case "HOTEL_ROOM" -> HOTEL_ROOM;
            case "VENUE" -> VENUE;
            case "HARBOR" -> HARBOR;
            case "RAIL_STATION" -> RAIL_STATION;
            case "PACKING_STATION" -> PACKING_STATION;
            case "MULE_STATION" -> MULE_STATION;
            case "GOVERNMENT" -> CIVIC;
            case "COMPANY" -> COMPANY;
            default -> null;
        };
    }
}
