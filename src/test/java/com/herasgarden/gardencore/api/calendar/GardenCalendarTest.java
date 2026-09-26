package com.herasgarden.gardencore.api.calendar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GardenCalendarTest {
    @Test void sundayStartsTheGardenWeek() {
        assertEquals(GardenCalendar.Weekday.SUNDAY, GardenCalendar.Weekday.forDay(0));
        assertEquals(GardenCalendar.Weekday.SATURDAY, GardenCalendar.Weekday.forDay(6));
        assertEquals(GardenCalendar.Weekday.SUNDAY, GardenCalendar.Weekday.forDay(7));
    }
    @Test void negativeDaysWrapWithoutChangingWeekOrder() {
        assertEquals(GardenCalendar.Weekday.SATURDAY, GardenCalendar.Weekday.forDay(-1));
    }
    @Test void snapshotFormatsTwentyFourHourTime() {
        var s = new GardenCalendar.CalendarSnapshot(12, 1265, GardenCalendar.Weekday.FRIDAY);
        assertEquals("Day 12 · Friday · 21:05", s.formattedTime(true));
    }
    @Test void snapshotFormatsMidnightInTwelveHourTime() {
        var s = new GardenCalendar.CalendarSnapshot(2, 5, GardenCalendar.Weekday.TUESDAY);
        assertEquals("Day 2 · Tuesday · 12:05 AM", s.formattedTime(false));
    }
    @Test void snapshotFormatsNoonInTwelveHourTime() {
        var s = new GardenCalendar.CalendarSnapshot(2, 720, GardenCalendar.Weekday.TUESDAY);
        assertEquals("Day 2 · Tuesday · 12:00 PM", s.formattedTime(false));
    }
    @Test void snapshotFormatsAfternoonInTwelveHourTime() {
        var s = new GardenCalendar.CalendarSnapshot(2, 1069, GardenCalendar.Weekday.TUESDAY);
        assertEquals("Day 2 · Tuesday · 5:49 PM", s.formattedTime(false));
    }
    @Test void defaultFormattingRemainsTwentyFourHour() {
        var s = new GardenCalendar.CalendarSnapshot(3, 545, GardenCalendar.Weekday.WEDNESDAY);
        assertEquals("Day 3 · Wednesday · 09:05", s.formattedTime());
    }
}
