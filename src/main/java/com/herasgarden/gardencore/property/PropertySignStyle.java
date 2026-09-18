package com.herasgarden.gardencore.property;

import java.util.Locale;

public enum PropertySignStyle {
    PROPERTY_MAILBOX,
    APARTMENT_UNIT,
    APARTMENT_MAILBOX;

    public static PropertySignStyle from(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("ADDRESS")) {
            return PROPERTY_MAILBOX;
        }
        try {
            return valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return PROPERTY_MAILBOX;
        }
    }
}
