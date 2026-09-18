package com.herasgarden.gardencore.api.order;

import java.util.UUID;

/** Immutable shared order snapshot. */
public record GardenOrder(
        UUID id,
        OrderType type,
        OrderState state,
        UUID buyerUuid,
        String sellerKind,
        String sellerId,
        long amount,
        String domainKey,
        String domainRef,
        String metadata,
        long createdAt,
        long updatedAt
) {
}
