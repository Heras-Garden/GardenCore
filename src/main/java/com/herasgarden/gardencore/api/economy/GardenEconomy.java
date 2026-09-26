package com.herasgarden.gardencore.api.economy;

import java.sql.Connection;
import java.sql.SQLException;
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

    /**
     * Mutates the Garden ledger using a caller-owned SQL transaction.
     * Implementations must not commit, roll back, or close this connection.
     */
    boolean withdraw(Connection connection, UUID playerUuid, long amount) throws SQLException;

    boolean deposit(Connection connection, UUID playerUuid, long amount) throws SQLException;

    boolean transfer(Connection connection, UUID fromUuid, UUID toUuid, long amount) throws SQLException;

    String symbol();
}
