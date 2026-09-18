package com.herasgarden.gardencore.api;

import com.herasgarden.gardencore.api.economy.GardenEconomy;
import com.herasgarden.gardencore.api.integration.IntegrationInbox;
import com.herasgarden.gardencore.api.integration.IntegrationOutbox;
import com.herasgarden.gardencore.api.order.OrderService;
import com.herasgarden.gardencore.api.storage.GardenStorage;

/**
 * Stable entry point exposed by GardenCore to the other Garden plugins.
 * Domain plugins depend on this interface instead of reaching into GardenCore
 * implementation classes.
 */
public interface GardenPlatform {
    OrderService orders();

    IntegrationOutbox integrations();

    IntegrationInbox commands();

    GardenStorage storage();

    GardenEconomy currency();
}
