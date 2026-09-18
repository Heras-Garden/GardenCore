package com.herasgarden.gardencore.api.order;

/**
 * High-level reasons an order exists. Plugins may attach their own domain
 * metadata without changing GardenCore's common payment lifecycle.
 */
public enum OrderType {
    PROPERTY_PURCHASE,
    PROPERTY_RENT,
    SHOP_PURCHASE,
    POSTAGE,
    TRADE_CONTRACT,
    EVENT_TICKET,
    CUSTOM
}
