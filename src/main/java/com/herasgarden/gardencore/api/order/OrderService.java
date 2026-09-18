package com.herasgarden.gardencore.api.order;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * Common transaction lifecycle shared by GardenLands, GardenTrade, GardenPost,
 * GardenCivics, and GardenEvents.
 */
public interface OrderService {
    GardenOrder create(
            OrderType type,
            UUID buyerUuid,
            String sellerKind,
            String sellerId,
            long amount,
            String domainKey,
            String domainRef,
            String metadata
    ) throws SQLException;

    Optional<GardenOrder> find(UUID orderId) throws SQLException;

    GardenOrder transition(UUID orderId, OrderState nextState, String detail) throws SQLException;
}
