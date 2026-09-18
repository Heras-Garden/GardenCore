package com.herasgarden.gardencore.platform;

import com.herasgarden.gardencore.database.DatabaseManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Transitional schema installer for the 0.3 platform APIs. These tables are
 * additive and safe to create alongside the existing GardenCore schema while
 * land features are extracted into GardenLands.
 */
public final class PlatformSchema {
    private PlatformSchema() {
    }

    public static void ensure(DatabaseManager database) throws SQLException {
        try (Connection connection = database.connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS gc_orders ("
                    + "order_uuid VARCHAR(36) PRIMARY KEY,"
                    + "order_type VARCHAR(40) NOT NULL,"
                    + "order_state VARCHAR(40) NOT NULL,"
                    + "buyer_uuid VARCHAR(36) NOT NULL,"
                    + "seller_kind VARCHAR(32) NULL,"
                    + "seller_id VARCHAR(128) NULL,"
                    + "amount BIGINT NOT NULL,"
                    + "domain_key VARCHAR(64) NOT NULL,"
                    + "domain_ref VARCHAR(128) NOT NULL,"
                    + "metadata TEXT NULL,"
                    + "created_at BIGINT NOT NULL,"
                    + "updated_at BIGINT NOT NULL)");

            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_gc_orders_buyer "
                    + "ON gc_orders (buyer_uuid, created_at)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_gc_orders_domain "
                    + "ON gc_orders (domain_key, domain_ref)");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS gc_order_journal ("
                    + "event_uuid VARCHAR(36) PRIMARY KEY,"
                    + "order_uuid VARCHAR(36) NOT NULL,"
                    + "from_state VARCHAR(40) NULL,"
                    + "to_state VARCHAR(40) NOT NULL,"
                    + "detail TEXT NULL,"
                    + "created_at BIGINT NOT NULL)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_gc_order_journal_order "
                    + "ON gc_order_journal (order_uuid, created_at)");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS gc_integration_outbox ("
                    + "event_uuid VARCHAR(36) PRIMARY KEY,"
                    + "event_type VARCHAR(64) NOT NULL,"
                    + "aggregate_type VARCHAR(64) NOT NULL,"
                    + "aggregate_id VARCHAR(128) NOT NULL,"
                    + "payload TEXT NULL,"
                    + "created_at BIGINT NOT NULL,"
                    + "claimed_at BIGINT NULL,"
                    + "processed_at BIGINT NULL,"
                    + "attempts INTEGER NOT NULL DEFAULT 0,"
                    + "last_error TEXT NULL)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_gc_outbox_pending "
                    + "ON gc_integration_outbox (processed_at, created_at)");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS gc_integration_commands ("
                    + "command_uuid VARCHAR(36) PRIMARY KEY,"
                    + "command_type VARCHAR(64) NOT NULL,"
                    + "aggregate_type VARCHAR(64) NOT NULL,"
                    + "aggregate_id VARCHAR(128) NOT NULL,"
                    + "payload TEXT NULL,"
                    + "created_at BIGINT NOT NULL,"
                    + "claimed_at BIGINT NULL,"
                    + "processed_at BIGINT NULL,"
                    + "attempts INTEGER NOT NULL DEFAULT 0,"
                    + "last_error TEXT NULL)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_gc_commands_pending "
                    + "ON gc_integration_commands (processed_at, created_at)");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS gc_minecraft_links ("
                    + "player_uuid VARCHAR(36) PRIMARY KEY,"
                    + "discord_id VARCHAR(20) NOT NULL UNIQUE,"
                    + "player_name VARCHAR(32) NOT NULL,"
                    + "linked_at BIGINT NOT NULL,"
                    + "updated_at BIGINT NOT NULL)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_gc_minecraft_links_discord "
                    + "ON gc_minecraft_links (discord_id)");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS gc_minecraft_link_codes ("
                    + "code_hash VARCHAR(64) PRIMARY KEY,"
                    + "player_uuid VARCHAR(36) NOT NULL,"
                    + "player_name VARCHAR(32) NOT NULL,"
                    + "created_at BIGINT NOT NULL,"
                    + "expires_at BIGINT NOT NULL,"
                    + "consumed_at BIGINT NULL)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_gc_minecraft_link_codes_player "
                    + "ON gc_minecraft_link_codes (player_uuid, consumed_at, expires_at)");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS gc_player_balances ("
                    + "player_uuid VARCHAR(36) PRIMARY KEY,"
                    + "balance BIGINT NOT NULL DEFAULT 0,"
                    + "updated_at BIGINT NOT NULL)");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS gc_platform_meta ("
                    + "meta_key VARCHAR(96) PRIMARY KEY,"
                    + "meta_value TEXT NULL,"
                    + "updated_at BIGINT NOT NULL)");
        }
    }
}
