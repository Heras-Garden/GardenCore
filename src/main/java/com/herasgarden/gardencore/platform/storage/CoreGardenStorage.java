package com.herasgarden.gardencore.platform.storage;

import com.herasgarden.gardencore.api.storage.GardenStorage;
import com.herasgarden.gardencore.database.DatabaseManager;

import java.sql.Connection;
import java.sql.SQLException;

public final class CoreGardenStorage implements GardenStorage {
    private final DatabaseManager database;

    public CoreGardenStorage(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public Connection connection() throws SQLException {
        return database.connection();
    }

    @Override
    public String dialect() {
        return database.type();
    }
}
