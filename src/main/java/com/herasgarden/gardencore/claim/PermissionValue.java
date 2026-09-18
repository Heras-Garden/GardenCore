package com.herasgarden.gardencore.claim;

public enum PermissionValue {
    INHERIT,
    ALLOW,
    DENY;

    public PermissionValue next() {
        return switch (this) {
            case INHERIT -> ALLOW;
            case ALLOW -> DENY;
            case DENY -> INHERIT;
        };
    }
}
