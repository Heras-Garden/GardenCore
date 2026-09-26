package com.herasgarden.gardencore.platform.calendar;

import com.herasgarden.gardencore.api.calendar.GardenCalendar;
import com.herasgarden.gardencore.api.storage.GardenStorage;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SqlGardenCalendar implements GardenCalendar {
    private final JavaPlugin plugin;
    private final GardenStorage storage;
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> hudPreferences = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> time24Preferences = new ConcurrentHashMap<>();

    private volatile long gardenDay;
    private volatile long lastFullTime;
    private volatile int minuteOfDay;

    public SqlGardenCalendar(JavaPlugin plugin, GardenStorage storage) throws SQLException {
        this.plugin = plugin;
        this.storage = storage;
        load();
        loadPreferences();
    }

    private void load() throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT garden_day,last_full_time,minute_of_day FROM gc_calendar_state WHERE id = 1");
             ResultSet result = statement.executeQuery()) {
            if (result.next()) {
                gardenDay = result.getLong("garden_day");
                lastFullTime = result.getLong("last_full_time");
                minuteOfDay = result.getInt("minute_of_day");
                return;
            }
        }
        gardenDay = 0L;
        lastFullTime = 0L;
        minuteOfDay = 360;
        persist();
    }

    public synchronized void updateFromWorld(long fullTime, long time) {
        long previousWorldDay = midnightDayIndex(lastFullTime);
        long currentWorldDay = midnightDayIndex(fullTime);
        if (lastFullTime > 0L && currentWorldDay > previousWorldDay) {
            gardenDay += currentWorldDay - previousWorldDay;
        }
        lastFullTime = fullTime;
        minuteOfDay = (int) (Math.floorMod(time + 6000L, 24000L) * 1440L / 24000L);
    }

    private long midnightDayIndex(long fullTime) {
        return Math.floorDiv(fullTime + 6000L, 24000L);
    }

    public synchronized void persistNow() throws SQLException {
        persist();
    }

    private void persist() throws SQLException {
        try (Connection connection = storage.connection()) {
            int changed;
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE gc_calendar_state SET garden_day=?,last_full_time=?,minute_of_day=?,updated_at=? WHERE id=1")) {
                update.setLong(1, gardenDay);
                update.setLong(2, lastFullTime);
                update.setInt(3, minuteOfDay);
                update.setLong(4, System.currentTimeMillis());
                changed = update.executeUpdate();
            }
            if (changed == 0) {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO gc_calendar_state (id,garden_day,last_full_time,minute_of_day,updated_at) VALUES (1,?,?,?,?)")) {
                    insert.setLong(1, gardenDay);
                    insert.setLong(2, lastFullTime);
                    insert.setInt(3, minuteOfDay);
                    insert.setLong(4, System.currentTimeMillis());
                    insert.executeUpdate();
                }
            }
        }
    }

    @Override
    public CalendarSnapshot snapshot() {
        return new CalendarSnapshot(gardenDay, minuteOfDay, Weekday.forDay(gardenDay));
    }

    private void loadPreferences() throws SQLException {
        hudPreferences.clear();
        time24Preferences.clear();
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT player_uuid,enabled,time_format FROM gc_calendar_hud");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                UUID playerId = UUID.fromString(result.getString("player_uuid"));
                hudPreferences.put(playerId, result.getInt("enabled") != 0);
                time24Preferences.put(playerId, !"12H".equalsIgnoreCase(result.getString("time_format")));
            }
        }
    }

    @Override
    public boolean hudEnabled(UUID playerId) {
        return hudPreferences.getOrDefault(playerId, false);
    }

    @Override
    public void setHudEnabled(UUID playerId, boolean enabled) throws SQLException {
        savePreference(playerId, enabled, uses24HourTime(playerId));
        hudPreferences.put(playerId, enabled);
        if (!enabled) {
            BossBar bar = bars.remove(playerId);
            Player player = plugin.getServer().getPlayer(playerId);
            if (bar != null && player != null) player.hideBossBar(bar);
        }
    }

    @Override
    public boolean uses24HourTime(UUID playerId) {
        return time24Preferences.getOrDefault(playerId, true);
    }

    @Override
    public void setUses24HourTime(UUID playerId, boolean enabled) throws SQLException {
        savePreference(playerId, hudEnabled(playerId), enabled);
        time24Preferences.put(playerId, enabled);
    }

    private void savePreference(UUID playerId, boolean hudEnabled, boolean use24Hour) throws SQLException {
        try (Connection connection = storage.connection()) {
            int changed;
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE gc_calendar_hud SET enabled=?,time_format=?,updated_at=? WHERE player_uuid=?")) {
                update.setInt(1, hudEnabled ? 1 : 0);
                update.setString(2, use24Hour ? "24H" : "12H");
                update.setLong(3, System.currentTimeMillis());
                update.setString(4, playerId.toString());
                changed = update.executeUpdate();
            }
            if (changed == 0) {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO gc_calendar_hud (player_uuid,enabled,time_format,updated_at) VALUES (?,?,?,?)")) {
                    insert.setString(1, playerId.toString());
                    insert.setInt(2, hudEnabled ? 1 : 0);
                    insert.setString(3, use24Hour ? "24H" : "12H");
                    insert.setLong(4, System.currentTimeMillis());
                    insert.executeUpdate();
                }
            }
        }
    }

    public void refreshHud(Collection<? extends Player> players) {
        CalendarSnapshot snapshot = snapshot();
        for (Player player : players) {
            boolean enabled = hudEnabled(player.getUniqueId());
            if (!enabled) {
                BossBar existing = bars.remove(player.getUniqueId());
                if (existing != null) player.hideBossBar(existing);
                continue;
            }
            BossBar bar = bars.computeIfAbsent(player.getUniqueId(), ignored -> {
                BossBar created = BossBar.bossBar(Component.empty(), 1.0f, BossBar.Color.PINK, BossBar.Overlay.PROGRESS);
                player.showBossBar(created);
                return created;
            });
            float progress = Math.max(0.01f, Math.min(1.0f, snapshot.minuteOfDay() / 1440.0f));
            bar.name(Component.text(snapshot.formattedTime(uses24HourTime(player.getUniqueId()))));
            bar.progress(progress);
        }
    }

    public void hideAll() {
        for (Map.Entry<UUID, BossBar> entry : bars.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null) player.hideBossBar(entry.getValue());
        }
        bars.clear();
    }
}
