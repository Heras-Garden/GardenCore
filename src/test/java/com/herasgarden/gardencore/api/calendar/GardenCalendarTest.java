package com.herasgarden.gardencore.api.calendar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GardenCalendarTest {
    @Test
    void weekdaysCyclePersistently() {
        assertEquals(GardenCalendar.Weekday.MONDAY, GardenCalendar.Weekday.forDay(0));
        assertEquals(GardenCalendar.Weekday.SUNDAY, GardenCalendar.Weekday.forDay(6));
        assertEquals(GardenCalendar.Weekday.MONDAY, GardenCalendar.Weekday.forDay(7));
        assertEquals(GardenCalendar.Weekday.SUNDAY, GardenCalendar.Weekday.forDay(-1));
    }

    @Test
    void snapshotFormatsGardenTime() {
        var snapshot = new GardenCalendar.CalendarSnapshot(
                12, 9 * 60 + 5, GardenCalendar.Weekday.FRIDAY);
        assertEquals("Day 12 · Friday · 09:05", snapshot.formattedTime());
    }
}
