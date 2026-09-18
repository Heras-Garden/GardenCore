package com.herasgarden.gardencore.api.storage;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Shared database access for Garden domain plugins.
 *
 * GardenCore owns the connection pool. Domain plugins own their own tables and
 * migrations, but obtain connections through this interface so the entire
 * Garden suite uses the same configured database and pool.
 */
public interface GardenStorage {
    Connection connection() throws SQLException;

    /** "sqlite" or "mariadb". */
    String dialect();
}
