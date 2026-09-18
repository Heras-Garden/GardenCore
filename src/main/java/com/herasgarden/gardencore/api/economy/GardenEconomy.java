package com.herasgarden.gardencore.api.economy;

import java.util.UUID;

/**
 * Currency abstraction for the Garden plugin family.
 * Domain plugins should never talk to Vault directly.
 */
public interface GardenEconomy {
    long balance(UUID playerUuid);

    boolean has(UUID playerUuid, long amount);

    boolean withdraw(UUID playerUuid, long amount);

    boolean deposit(UUID playerUuid, long amount);

    String symbol();
}
