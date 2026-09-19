package com.herasgarden.gardencore.database;

import com.herasgarden.gardencore.GardenCore;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

public final class DatabaseManager implements AutoCloseable {
    private static final int SCHEMA_VERSION = 5;

    private final GardenCore plugin;
    private HikariDataSource dataSource;
    private String type;

    public DatabaseManager(GardenCore plugin) {
        this.plugin = plugin;
    }

    public void initialize() throws SQLException {
        type = plugin.getConfig().getString("database.type", "sqlite").toLowerCase(Locale.ROOT);
        HikariConfig config = new HikariConfig();
        config.setPoolName("GardenCore");
        config.setConnectionTimeout(plugin.getConfig().getLong("database.pool.connection-timeout-ms", 5000L));
        config.setValidationTimeout(3000L);

        if (type.equals("mariadb") || type.equals("mysql")) {
            String host = plugin.getConfig().getString("database.mariadb.host", "127.0.0.1");
            int port = plugin.getConfig().getInt("database.mariadb.port", 3306);
            String database = plugin.getConfig().getString("database.mariadb.database", "gardencore");
            String username = plugin.getConfig().getString("database.mariadb.username", "root");
            String password = plugin.getConfig().getString("database.mariadb.password", "");
            boolean ssl = plugin.getConfig().getBoolean("database.mariadb.ssl", false);

            config.setDriverClassName("org.mariadb.jdbc.Driver");
            config.setJdbcUrl("jdbc:mariadb://" + host + ":" + port + "/" + database
                    + "?useSsl=" + ssl + "&useUnicode=true&characterEncoding=utf8");
            config.setUsername(username);
            config.setPassword(password);
            config.setMaximumPoolSize(Math.max(2, plugin.getConfig().getInt("database.pool.maximum-size", 5)));
            config.setMinimumIdle(1);
        } else {
            type = "sqlite";
            File file = new File(plugin.getDataFolder(), plugin.getConfig().getString("database.sqlite-file", "gardencore.db"));
            config.setDriverClassName("org.sqlite.JDBC");
            config.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            config.setMaximumPoolSize(1);
            config.setMinimumIdle(1);
            config.setConnectionInitSql("PRAGMA foreign_keys=ON");
        }

        dataSource = new HikariDataSource(config);
        migrate();
    }

    public Connection connection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("GardenCore database is not initialized.");
        }
        return dataSource.getConnection();
    }

    public String type() {
        return type;
    }

    private void migrate() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS gc_schema_version ("
                    + "id INTEGER PRIMARY KEY, version INTEGER NOT NULL)");
        }

        int current = 0;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT version FROM gc_schema_version WHERE id = 1");
             ResultSet result = statement.executeQuery()) {
            if (result.next()) {
                current = result.getInt(1);
            }
        }

        if (current < 1) {
            migrateToV1();
            setVersion(1);
            current = 1;
        }
        if (current < 2) {
            migrateToV2();
            setVersion(2);
            current = 2;
        }
        if (current < 3) {
            migrateToV3();
            setVersion(3);
            current = 3;
        }
        if (current < 4) {
            migrateToV4();
            setVersion(4);
            current = 4;
        }
        if (current < 5) {
            migrateToV5();
            setVersion(5);
            current = 5;
        }

        if (current > SCHEMA_VERSION) {
            plugin.getLogger().warning("Database schema " + current + " is newer than this GardenCore build ("
                    + SCHEMA_VERSION + ").");
        }
    }

    private void migrateToV1() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS gc_claims ("
                    + "claim_uuid VARCHAR(36) PRIMARY KEY,"
                    + "claim_type VARCHAR(32) NOT NULL,"
                    + "owner_type VARCHAR(16) NOT NULL,"
                    + "owner_uuid VARCHAR(36) NOT NULL,"
                    + "parent_uuid VARCHAR(36) NULL,"
                    + "name VARCHAR(128) NULL,"
                    + "world_uuid VARCHAR(36) NOT NULL,"
                    + "world_name VARCHAR(128) NOT NULL,"
                    + "min_y INTEGER NOT NULL,"
                    + "max_y INTEGER NOT NULL,"
                    + "full_height INTEGER NOT NULL,"
                    + "created_by VARCHAR(36) NOT NULL,"
                    + "created_at BIGINT NOT NULL) ");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS gc_claim_vertices ("
                    + "claim_uuid VARCHAR(36) NOT NULL,"
                    + "vertex_index INTEGER NOT NULL,"
                    + "x INTEGER NOT NULL,"
                    + "z INTEGER NOT NULL,"
                    + "PRIMARY KEY (claim_uuid, vertex_index))");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS gc_claim_permissions ("
                    + "claim_uuid VARCHAR(36) NOT NULL,"
                    + "subject VARCHAR(24) NOT NULL,"
                    + "permission VARCHAR(40) NOT NULL,"
                    + "value VARCHAR(16) NOT NULL,"
                    + "PRIMARY KEY (claim_uuid, subject, permission))");
        }
    }


    private void migrateToV2() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS gc_properties ("
                    + "property_uuid VARCHAR(36) PRIMARY KEY,"
                    + "claim_uuid VARCHAR(36) NOT NULL UNIQUE,"
                    + "scope_key VARCHAR(64) NOT NULL,"
                    + "road VARCHAR(96) NOT NULL,"
                    + "road_key VARCHAR(96) NOT NULL,"
                    + "number VARCHAR(32) NOT NULL,"
                    + "number_key VARCHAR(32) NOT NULL,"
                    + "unit_label VARCHAR(32) NULL,"
                    + "unit_key VARCHAR(32) NOT NULL,"
                    + "price BIGINT NOT NULL,"
                    + "for_sale INTEGER NOT NULL,"
                    + "created_at BIGINT NOT NULL)");

            statement.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS idx_gc_property_address "
                    + "ON gc_properties (scope_key, road_key, number_key, unit_key)");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS gc_property_signs ("
                    + "world_uuid VARCHAR(36) NOT NULL,"
                    + "world_name VARCHAR(128) NOT NULL,"
                    + "x INTEGER NOT NULL,"
                    + "y INTEGER NOT NULL,"
                    + "z INTEGER NOT NULL,"
                    + "property_uuid VARCHAR(36) NOT NULL,"
                    + "created_by VARCHAR(36) NOT NULL,"
                    + "sign_style VARCHAR(24) NOT NULL,"
                    + "PRIMARY KEY (world_uuid, x, y, z))");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS gc_property_mailboxes ("
                    + "property_uuid VARCHAR(36) PRIMARY KEY,"
                    + "world_uuid VARCHAR(36) NOT NULL,"
                    + "world_name VARCHAR(128) NOT NULL,"
                    + "x INTEGER NOT NULL,"
                    + "y INTEGER NOT NULL,"
                    + "z INTEGER NOT NULL)");
        }
    }


    private void migrateToV3() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS gc_organizations ("
                    + "org_uuid VARCHAR(36) PRIMARY KEY,"
                    + "org_type VARCHAR(16) NOT NULL,"
                    + "name VARCHAR(48) NOT NULL,"
                    + "name_key VARCHAR(48) NOT NULL,"
                    + "founder_uuid VARCHAR(36) NOT NULL,"
                    + "treasury BIGINT NOT NULL DEFAULT 0,"
                    + "created_at BIGINT NOT NULL)");

            statement.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS idx_gc_org_name "
                    + "ON gc_organizations (name_key)");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS gc_org_roles ("
                    + "org_uuid VARCHAR(36) NOT NULL,"
                    + "role_key VARCHAR(48) NOT NULL,"
                    + "display_name VARCHAR(64) NOT NULL,"
                    + "government_position INTEGER NOT NULL DEFAULT 0,"
                    + "salary BIGINT NOT NULL DEFAULT 0,"
                    + "sort_order INTEGER NOT NULL DEFAULT 0,"
                    + "PRIMARY KEY (org_uuid, role_key))");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS gc_org_role_permissions ("
                    + "org_uuid VARCHAR(36) NOT NULL,"
                    + "role_key VARCHAR(48) NOT NULL,"
                    + "permission VARCHAR(48) NOT NULL,"
                    + "PRIMARY KEY (org_uuid, role_key, permission))");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS gc_org_members ("
                    + "org_uuid VARCHAR(36) NOT NULL,"
                    + "player_uuid VARCHAR(36) NOT NULL,"
                    + "role_key VARCHAR(48) NOT NULL,"
                    + "joined_at BIGINT NOT NULL,"
                    + "PRIMARY KEY (org_uuid, player_uuid))");

            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_gc_org_members_player "
                    + "ON gc_org_members (player_uuid)");
        }
    }

    private void migrateToV4() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS gc_territories ("
                    + "claim_uuid VARCHAR(36) PRIMARY KEY,"
                    + "name VARCHAR(128) NOT NULL,"
                    + "name_key VARCHAR(128) NOT NULL,"
                    + "flag_data TEXT NOT NULL,"
                    + "created_at BIGINT NOT NULL)");
            statement.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS idx_gc_territory_name "
                    + "ON gc_territories (name_key)");
        }
    }

    private void migrateToV5() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            ensureColumn(connection, "gc_claims", "claim_tag", "ALTER TABLE gc_claims ADD COLUMN claim_tag VARCHAR(32) NULL");
            ensureColumn(connection, "gc_claims", "government_type", "ALTER TABLE gc_claims ADD COLUMN government_type VARCHAR(32) NULL");

            statement.executeUpdate("UPDATE gc_claims SET claim_tag = 'SHOP', claim_type = 'PROPERTY' WHERE claim_type = 'SHOP'");
            statement.executeUpdate("UPDATE gc_claims SET claim_tag = 'FARM', claim_type = 'PROPERTY' WHERE claim_type = 'FARM'");
            statement.executeUpdate("UPDATE gc_claims SET claim_tag = 'BUILDING', claim_type = 'PROPERTY' WHERE claim_type = 'BUILDING'");
            statement.executeUpdate("UPDATE gc_claims SET claim_tag = 'APARTMENT', claim_type = 'UNIT' WHERE claim_type = 'APARTMENT'");
            statement.executeUpdate("UPDATE gc_claims SET claim_tag = 'HOTEL_ROOM', claim_type = 'UNIT' WHERE claim_type = 'HOTEL_ROOM'");
            statement.executeUpdate("UPDATE gc_claims SET claim_tag = 'VENUE', claim_type = 'PROPERTY' WHERE claim_type = 'VENUE'");
            statement.executeUpdate("UPDATE gc_claims SET claim_tag = 'HARBOR', claim_type = 'PROPERTY' WHERE claim_type = 'HARBOR'");
            statement.executeUpdate("UPDATE gc_claims SET claim_tag = 'RAIL_STATION', claim_type = 'PROPERTY' WHERE claim_type = 'RAIL_STATION'");
            statement.executeUpdate("UPDATE gc_claims SET claim_tag = 'PACKING_STATION', claim_type = 'PROPERTY' WHERE claim_type = 'PACKING_STATION'");
            statement.executeUpdate("UPDATE gc_claims SET claim_tag = 'MULE_STATION', claim_type = 'PROPERTY' WHERE claim_type = 'MULE_STATION'");
            statement.executeUpdate("UPDATE gc_claims SET claim_tag = 'CIVIC', claim_type = 'PROPERTY' WHERE claim_type = 'GOVERNMENT'");
            statement.executeUpdate("UPDATE gc_claims SET claim_tag = 'COMPANY', claim_type = 'PROPERTY' WHERE claim_type = 'COMPANY'");
            statement.executeUpdate("UPDATE gc_claims SET claim_type = 'DISTRICT' WHERE claim_type = 'CITY'");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS gc_player_claim_profiles ("
                    + "player_uuid VARCHAR(36) PRIMARY KEY,"
                    + "earned_blocks BIGINT NOT NULL DEFAULT 0,"
                    + "purchased_blocks BIGINT NOT NULL DEFAULT 0,"
                    + "play_minutes BIGINT NOT NULL DEFAULT 0,"
                    + "earning_locked INTEGER NOT NULL DEFAULT 0,"
                    + "last_seen BIGINT NOT NULL,"
                    + "updated_at BIGINT NOT NULL)");
        }
    }

    private void ensureColumn(Connection connection, String table, String column, String ddl) throws SQLException {
        try (ResultSet result = connection.getMetaData().getColumns(null, null, table, column)) {
            if (result.next()) return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(ddl);
        }
    }

    private void setVersion(int version) throws SQLException {
        try (Connection connection = connection()) {
            boolean exists;
            try (PreparedStatement check = connection.prepareStatement("SELECT 1 FROM gc_schema_version WHERE id = 1")) {
                try (ResultSet result = check.executeQuery()) {
                    exists = result.next();
                }
            }
            if (exists) {
                try (PreparedStatement update = connection.prepareStatement("UPDATE gc_schema_version SET version = ? WHERE id = 1")) {
                    update.setInt(1, version);
                    update.executeUpdate();
                }
            } else {
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO gc_schema_version (id, version) VALUES (1, ?)")) {
                    insert.setInt(1, version);
                    insert.executeUpdate();
                }
            }
        }
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }
}
