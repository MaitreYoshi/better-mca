package com.maitreyoshi.mcavie.calendar;

public enum TimeSpeed {
    JOUR(1),
    SEMAINE(7),
    MOIS(WorldCalendar.DAYS_PER_YEAR / 12),
    SAISON(WorldCalendar.DAYS_PER_YEAR / 4),
    AN(WorldCalendar.DAYS_PER_YEAR);

    private final double calendarDaysPerGameDay;

    TimeSpeed(double calendarDaysPerGameDay) {
        this.calendarDaysPerGameDay = calendarDaysPerGameDay;
    }

    public double calendarDaysPerGameDay() {
        return calendarDaysPerGameDay;
    }
}
