package com.herasgarden.gardencore.claim;

public record ClaimValidation(boolean valid, String reason) {
    public static ClaimValidation ok() {
        return new ClaimValidation(true, "");
    }

    public static ClaimValidation invalid(String reason) {
        return new ClaimValidation(false, reason);
    }
}
