package com.herasgarden.gardencore.social;

import com.herasgarden.gardencore.GardenCore;
import com.herasgarden.gardencore.api.economy.GardenEconomy;
import com.herasgarden.gardencore.api.social.MarriageDirectory;
import com.herasgarden.gardencore.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class MarriageService implements MarriageDirectory {
    private final GardenCore plugin;
    private final DatabaseManager database;
    private final GardenEconomy economy;
    private final Map<UUID, MarriageRecord> byPlayer = new HashMap<>();

    public MarriageService(GardenCore plugin, DatabaseManager database, GardenEconomy economy) {
        this.plugin = plugin;
        this.database = database;
        this.economy = economy;
    }

    public synchronized void load() throws SQLException {
        byPlayer.clear();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT marriage_uuid, player1_uuid, player2_uuid, married_at FROM gc_marriages");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                MarriageRecord record = new MarriageRecord(
                        UUID.fromString(result.getString("marriage_uuid")),
                        UUID.fromString(result.getString("player1_uuid")),
                        UUID.fromString(result.getString("player2_uuid")),
                        result.getLong("married_at"));
                byPlayer.put(record.player1(), record);
                byPlayer.put(record.player2(), record);
            }
        }
    }

    @Override
    public synchronized Set<UUID> partners(UUID playerId) {
        MarriageRecord record = byPlayer.get(playerId);
        if (record == null) return Set.of();
        UUID partner = record.player1().equals(playerId) ? record.player2() : record.player1();
        return Collections.singleton(partner);
    }

    public synchronized Optional<MarriageRecord> marriage(UUID playerId) {
        return Optional.ofNullable(byPlayer.get(playerId));
    }

    public synchronized boolean married(UUID playerId) {
        return byPlayer.containsKey(playerId);
    }

    public long totalCost() {
        return Math.max(0L, plugin.getConfig().getLong("marriage.cost", 500L));
    }

    public long costPerPlayer() {
        return totalCost() / 2L;
    }

    public GardenEconomy economy() {
        return economy;
    }

    public synchronized MarriageRecord marry(UUID first, UUID second) throws SQLException {
        if (first == null || second == null || first.equals(second)) {
            throw new IllegalArgumentException("A marriage requires two different players.");
        }
        if (byPlayer.containsKey(first) || byPlayer.containsKey(second)) {
            throw new IllegalArgumentException("One of those players is already married.");
        }

        MarriageRecord record = new MarriageRecord(UUID.randomUUID(), first, second, System.currentTimeMillis());
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO gc_marriages (marriage_uuid, player1_uuid, player2_uuid, married_at) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, record.id().toString());
            statement.setString(2, first.toString());
            statement.setString(3, second.toString());
            statement.setLong(4, record.marriedAt());
            statement.executeUpdate();
        }
        byPlayer.put(first, record);
        byPlayer.put(second, record);
        return record;
    }

    public synchronized boolean divorce(UUID playerId) throws SQLException {
        MarriageRecord record = byPlayer.get(playerId);
        if (record == null) return false;

        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM gc_marriages WHERE marriage_uuid = ?")) {
            statement.setString(1, record.id().toString());
            statement.executeUpdate();
        }
        byPlayer.remove(record.player1());
        byPlayer.remove(record.player2());
        return true;
    }

    public record MarriageRecord(UUID id, UUID player1, UUID player2, long marriedAt) {
        public UUID partnerOf(UUID playerId) {
            return player1.equals(playerId) ? player2 : player1;
        }
    }
}
