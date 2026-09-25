package com.herasgarden.gardencore.api.calendar;

import java.sql.SQLException;
import java.util.UUID;

public interface GardenCalendar {
    CalendarSnapshot snapshot();

    boolean hudEnabled(UUID playerId) throws SQLException;

    void setHudEnabled(UUID playerId, boolean enabled) throws SQLException;

    record CalendarSnapshot(long day, int minuteOfDay, Weekday weekday) {
        public String formattedTime() {
            int hour = Math.floorMod(minuteOfDay, 1440) / 60;
            int minute = Math.floorMod(minuteOfDay, 60);
            return String.format("Day %d · %s · %02d:%02d", day, weekday.displayName(), hour, minute);
        }
    }

    enum Weekday {
        MONDAY("Monday"),
        TUESDAY("Tuesday"),
        WEDNESDAY("Wednesday"),
        THURSDAY("Thursday"),
        FRIDAY("Friday"),
        SATURDAY("Saturday"),
        SUNDAY("Sunday");

        private final String displayName;

        Weekday(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }

        public static Weekday forDay(long day) {
            return values()[Math.floorMod((int) day, values().length)];
        }
    }
}
