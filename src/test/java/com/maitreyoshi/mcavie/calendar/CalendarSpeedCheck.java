package com.maitreyoshi.mcavie.calendar;

import com.maitreyoshi.mcavie.life.LifeCycle;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

// gradlew calendarSpeedCheck : changement de vitesse sans saut de date, âges en vitesse « 1 an »
public final class CalendarSpeedCheck {
    private static int failures;

    public static void main(String[] args) {
        long day = WorldCalendar.TICKS_PER_DAY;
        TimeSpeed[] speeds = TimeSpeed.values();
        for (TimeSpeed from : speeds) {
            for (TimeSpeed to : speeds) {
                if (from == to) {
                    continue;
                }
                List<WorldCalendar.Segment> base = List.of(new WorldCalendar.Segment(0, 0, from.calendarDaysPerGameDay()));
                WorldCalendar.setClientSegments(base);
                long t = 37 * day + 13_337;
                LocalDate before = date(t);
                long[] past = new long[38];
                for (int d = 0; d < past.length; d++) {
                    past[d] = WorldCalendar.calendarDay(d);
                }
                WorldCalendar.setClientSegments(WorldCalendar.withSpeed(base, t, to.calendarDaysPerGameDay()));
                LocalDate after = date(t);
                check(before.equals(after), from + "→" + to + " : " + before + " devient " + after);
                for (int d = 0; d < past.length; d++) {
                    check(past[d] == WorldCalendar.calendarDay(d), from + "→" + to + " : jour passé " + d + " modifié");
                }
                long elapsed = ChronoUnit.DAYS.between(after, date(t + day));
                check(Math.abs(elapsed - to.calendarDaysPerGameDay()) <= 1, from + "→" + to + " : +1 jour de jeu = " + elapsed + " jours");
            }
        }

        WorldCalendar.setClientSegments(List.of(new WorldCalendar.Segment(0, 0, TimeSpeed.YEAR.calendarDaysPerGameDay())));
        for (long birth = 0; birth < 50; birth++) {
            for (long today = birth; today < birth + 500; today++) {
                int age = LifeCycle.ageInYears(birth, today);
                check(age == today - birth, "an : né jour " + birth + ", jour " + today + " → " + age + " ans");
            }
        }

        WorldCalendar.setClientSegments(List.of(new WorldCalendar.Segment(0, 0, 1)));
        for (long today = 0; today < 3000; today += 7) {
            int expected = java.time.Period.between(WorldCalendar.dateOfDay(100), WorldCalendar.dateOfDay(today)).getYears();
            check(LifeCycle.ageInYears(100, today) == expected, "jour : âge différent au jour " + today);
        }

        WorldCalendar.resetClientSegments();
        System.out.println(failures == 0 ? "CalendarSpeedCheck OK" : "CalendarSpeedCheck : " + failures + " échec(s)");
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static LocalDate date(long ticks) {
        WorldCalendar.CalendarDate d = WorldCalendar.flowingFromTicks(ticks);
        return LocalDate.of(d.year(), d.month(), d.day());
    }

    private static void check(boolean ok, String message) {
        if (!ok && failures++ < 20) {
            System.out.println("ÉCHEC " + message);
        }
    }
}
