package com.herasgarden.gardencore.claim;

public enum ClaimPermission {
    BUILD("Build"),
    BREAK("Break"),
    DOOR_USE("Doors"),
    CONTAINER_OPEN("Open Chests"),
    CONTAINER_INSERT("Put Items In"),
    CONTAINER_TAKE("Take Items Out"),
    CONTAINER_BREAK("Break Chests");

    private final String displayName;

    ClaimPermission(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
