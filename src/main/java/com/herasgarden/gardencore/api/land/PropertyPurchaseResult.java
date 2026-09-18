package com.herasgarden.gardencore.api.land;

public record PropertyPurchaseResult(
        boolean success,
        String message,
        long price
) {
}
