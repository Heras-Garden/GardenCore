package com.herasgarden.gardencore.api.order;

/**
 * Shared lifecycle for purchases and paid actions across Garden plugins.
 * Domain plugins own fulfillment; GardenCore owns the common payment lifecycle.
 */
public enum OrderState {
    DRAFT,
    READY,
    AWAITING_CONFIRMATION,
    PAYMENT_PENDING,
    PAID,
    FULFILLING,
    COMPLETED,
    CANCELLED,
    EXPIRED,
    PAYMENT_FAILED,
    FULFILLMENT_FAILED,
    REFUNDED;

    public boolean terminal() {
        return switch (this) {
            case COMPLETED, CANCELLED, EXPIRED, PAYMENT_FAILED, FULFILLMENT_FAILED, REFUNDED -> true;
            default -> false;
        };
    }
}
