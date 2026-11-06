package com.maitreyoshi.mcavie.calendar;

public enum TimeSpeed {
    DAY(1),
    WEEK(7),
    MONTH(WorldCalendar.DAYS_PER_YEAR / 12),
    SEASON(WorldCalendar.DAYS_PER_YEAR / 4),
    YEAR(WorldCalendar.DAYS_PER_YEAR);

    private final double calendarDaysPerGameDay;

    TimeSpeed(double calendarDaysPerGameDay) {
        this.calendarDaysPerGameDay = calendarDaysPerGameDay;
    }

    public double calendarDaysPerGameDay() {
        return calendarDaysPerGameDay;
    }
}
