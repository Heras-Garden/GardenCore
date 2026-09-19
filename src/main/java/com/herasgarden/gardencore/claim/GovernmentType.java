package com.herasgarden.gardencore.claim;

import java.util.Locale;

public enum GovernmentType {
    COUNCIL,
    MAYOR,
    MONARCHY,
    DIRECT_DEMOCRACY,
    CUSTOM;

    public static GovernmentType parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (key) {
            case "COUNCIL" -> COUNCIL;
            case "MAYOR", "MAYORAL" -> MAYOR;
            case "MONARCHY", "MONARCH", "KINGDOM" -> MONARCHY;
            case "DIRECT_DEMOCRACY", "DIRECT", "DEMOCRACY", "DIRECT_VOTE" -> DIRECT_DEMOCRACY;
            case "CUSTOM" -> CUSTOM;
            default -> null;
        };
    }

    public String displayName() {
        return switch (this) {
            case COUNCIL -> "Council";
            case MAYOR -> "Mayor";
            case MONARCHY -> "Monarchy";
            case DIRECT_DEMOCRACY -> "Direct Democracy";
            case CUSTOM -> "Custom";
        };
    }
}
