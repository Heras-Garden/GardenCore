package com.herasgarden.gardencore.api.calendar;

import java.sql.SQLException;
import java.util.UUID;

public interface GardenCalendar {
    CalendarSnapshot snapshot();

    boolean hudEnabled(UUID playerId) throws SQLException;

    void setHudEnabled(UUID playerId, boolean enabled) throws SQLException;

    boolean uses24HourTime(UUID playerId);

    void setUses24HourTime(UUID playerId, boolean enabled) throws SQLException;

    record CalendarSnapshot(long day, int minuteOfDay, Weekday weekday) {
        public String formattedTime() {
            return formattedTime(true);
        }

        public String formattedTime(boolean use24HourTime) {
            int hour24 = Math.floorMod(minuteOfDay, 1440) / 60;
            int minute = Math.floorMod(minuteOfDay, 60);
            if (use24HourTime) {
                return String.format("Day %d · %s · %02d:%02d", day, weekday.displayName(), hour24, minute);
            }
            int hour12 = hour24 % 12;
            if (hour12 == 0) hour12 = 12;
            return String.format("Day %d · %s · %d:%02d %s",
                    day, weekday.displayName(), hour12, minute, hour24 < 12 ? "AM" : "PM");
        }
    }

    enum Weekday {
        SUNDAY("Sunday"),
        MONDAY("Monday"),
        TUESDAY("Tuesday"),
        WEDNESDAY("Wednesday"),
        THURSDAY("Thursday"),
        FRIDAY("Friday"),
        SATURDAY("Saturday");

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
