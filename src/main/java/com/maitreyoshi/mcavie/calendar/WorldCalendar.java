package com.maitreyoshi.mcavie.calendar;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.compat.ServerLifecycleEvents;
import com.maitreyoshi.mcavie.compat.ServerWorldEvents;
import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import com.maitreyoshi.mcavie.compat.ServerPlayConnectionEvents;
import com.maitreyoshi.mcavie.compat.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class WorldCalendar {
    public static final long TICKS_PER_DAY = 24000L;
    public static final double DAYS_PER_YEAR = 365.2425;
    public static final Identifier SEGMENTS_PACKET = MCAVie.id("calendar_segments");
    private static final long TICKS_OFFSET = 6000L;
    private static final LocalDate FIRST_DAY = LocalDate.of(1, 1, 1);
    private static final List<Segment> DEFAULT = List.of(new Segment(0, 0, 1));

    public static TimeSpeedRule TIME_SPEED;

    private static volatile MinecraftServer server;
    private static volatile List<Segment> serverSegments = DEFAULT;
    private static volatile List<Segment> clientSegments = DEFAULT;

    private WorldCalendar() {
    }

    public static void register() {
        TIME_SPEED.register(TimeSpeed.DAY, WorldCalendar::onSpeedChanged);
        ServerWorldEvents.LOAD.register((s, world) -> {
            if (world.getRegistryKey() == World.OVERWORLD) {
                server = s;
                CalendarState state = CalendarState.get(s);
                if (state.segments().isEmpty()) {
                    state.setSegments(List.of(new Segment(0, 0, TIME_SPEED.get(s.getGameRules()).calendarDaysPerGameDay())));
                }
                serverSegments = state.segments();
                double speed = serverSegments.get(serverSegments.size() - 1).speed();
                if (TIME_SPEED.get(s.getGameRules()).calendarDaysPerGameDay() != speed) {
                    for (TimeSpeed value : TimeSpeed.values()) {
                        if (value.calendarDaysPerGameDay() == speed) {
                            TIME_SPEED.set(s.getGameRules(), value, s);
                        }
                    }
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            server = null;
            serverSegments = DEFAULT;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> sendSegments(handler.player));
    }

    private static void onSpeedChanged(MinecraftServer s, TimeSpeed value) {
        if (s == null || s.getOverworld() == null) {
            return;
        }
        CalendarState state = CalendarState.get(s);
        List<Segment> segments = new ArrayList<>(state.segments().isEmpty() ? DEFAULT : state.segments());
        double speed = value.calendarDaysPerGameDay();
        Segment last = segments.get(segments.size() - 1);
        if (last.speed() == speed) {
            return;
        }
        state.setSegments(withSpeed(segments, s.getOverworld().getTimeOfDay(), speed));
        server = s;
        serverSegments = state.segments();
        s.getPlayerManager().getPlayerList().forEach(WorldCalendar::sendSegments);
    }

    // ancrage à l'instant exact : la date affichée et les âges ne bougent pas, seul le rythme change ensuite
    static List<Segment> withSpeed(List<Segment> current, long timeOfDay, double speed) {
        List<Segment> segments = new ArrayList<>(current);
        long now = Math.max(0, timeOfDay + TICKS_OFFSET);
        long calendarNow = calendarTicks(segments, now);
        while (segments.size() > 1 && segments.get(segments.size() - 1).gameStart() >= now) {
            segments.remove(segments.size() - 1);
        }
        if (segments.get(segments.size() - 1).gameStart() >= now) {
            segments.clear();
        }
        segments.add(new Segment(now, calendarNow, speed));
        return segments;
    }

    private static void sendSegments(ServerPlayerEntity player) {
        List<Segment> segments = serverSegments;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(segments.size());
        for (Segment segment : segments) {
            buf.writeLong(segment.gameStart());
            buf.writeLong(segment.calendarStart());
            buf.writeDouble(segment.speed());
        }
        ServerPlayNetworking.send(player, SEGMENTS_PACKET, buf);
    }

    public static List<Segment> readSegments(PacketByteBuf buf) {
        int size = Math.min(buf.readVarInt(), 4096);
        List<Segment> segments = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            long game = buf.readLong();
            long calendar = buf.readLong();
            double speed = buf.readDouble();
            if (speed > 0) {
                segments.add(new Segment(game, calendar, speed));
            }
        }
        return segments.isEmpty() ? DEFAULT : List.copyOf(segments);
    }

    public static void setClientSegments(List<Segment> segments) {
        clientSegments = segments;
    }

    public static void resetClientSegments() {
        clientSegments = DEFAULT;
    }

    private static List<Segment> active() {
        MinecraftServer s = server;
        return s != null && (s.isDedicated() || s.isOnThread()) ? serverSegments : clientSegments;
    }

    // segments en ticks (jeu décalé de TICKS_OFFSET → calendrier) : un changement de vitesse en cours de journée reste exact
    private static Segment segmentAt(List<Segment> segments, long gameTick) {
        for (int i = segments.size() - 1; i > 0; i--) {
            if (segments.get(i).gameStart() <= gameTick) {
                return segments.get(i);
            }
        }
        return segments.get(0);
    }

    private static long calendarTicks(List<Segment> segments, long gameTick) {
        Segment s = segmentAt(segments, gameTick);
        return s.calendarStart() + (long) Math.floor((gameTick - s.gameStart()) * s.speed());
    }

    public static long calendarDay(long gameDay) {
        return Math.floorDiv(calendarTicks(active(), gameDay * TICKS_PER_DAY), TICKS_PER_DAY);
    }

    private static long flowingCalendarTicks(long gameDay, long tickOfDay) {
        return calendarTicks(active(), gameDay * TICKS_PER_DAY + tickOfDay);
    }

    public static long gameDayOf(long calendarDay) {
        List<Segment> segments = active();
        long target = calendarDay * TICKS_PER_DAY;
        Segment s = segments.get(0);
        for (int i = segments.size() - 1; i > 0; i--) {
            if (segments.get(i).calendarStart() <= target) {
                s = segments.get(i);
                break;
            }
        }
        return Math.floorDiv(s.gameStart() + (long) Math.floor((target - s.calendarStart()) / s.speed()), TICKS_PER_DAY);
    }

    public static long gameDaysFor(long fromGameDay, long calendarDays) {
        long target = calendarDay(fromGameDay) + calendarDays;
        long end = gameDayOf(target);
        if (calendarDay(end) < target) {
            end++;
        }
        return Math.max(1, end - fromGameDay);
    }

    public static double calendarDaysPerGameDay(long gameDay) {
        return segmentAt(active(), gameDay * TICKS_PER_DAY).speed();
    }

    public static double calendarDaysBetween(long fromGameDay, long toGameDay) {
        return (double) calendarDay(toGameDay) - (double) calendarDay(fromGameDay);
    }

    public static double yearsBetween(long fromGameDay, long toGameDay) {
        return calendarDaysBetween(fromGameDay, toGameDay) / DAYS_PER_YEAR;
    }

    // jour de la semaine de travail : suit les jours de jeu, quelle que soit la vitesse du calendrier
    // (sinon à « 1 jour = 1 semaine » le calendrier reste bloqué sur le même jour, parfois un dimanche)
    public static java.time.DayOfWeek rhythmDay(CalendarDate date) {
        return FIRST_DAY.getDayOfWeek().plus(date.dayIndex());
    }

    public static CalendarDate now(World world) {
        return fromTicks(world.getTimeOfDay());
    }

    public static CalendarDate fromTicks(long ticks) {
        return fromTicks(ticks, false);
    }

    public static CalendarDate flowingNow(World world) {
        return fromTicks(world.getTimeOfDay(), true);
    }

    public static CalendarDate flowingFromTicks(long ticks) {
        return fromTicks(ticks, true);
    }

    private static CalendarDate fromTicks(long ticks, boolean flowing) {
        long shifted = Math.max(0, ticks + TICKS_OFFSET);
        long dayIndex = shifted / TICKS_PER_DAY;
        long tickOfDay = shifted % TICKS_PER_DAY;
        LocalDate date;
        if (flowing) {
            long calendarTicks = flowingCalendarTicks(dayIndex, tickOfDay);
            date = FIRST_DAY.plusDays(Math.floorDiv(calendarTicks, TICKS_PER_DAY));
            tickOfDay = Math.floorMod(calendarTicks, TICKS_PER_DAY);
        } else {
            date = dateOfDay(dayIndex);
        }
        int minuteOfDay = (int) (tickOfDay * 3 / 50);
        return new CalendarDate(
                date.getYear(),
                date.getMonthValue(),
                date.getDayOfMonth(),
                date.getDayOfWeek(),
                minuteOfDay / 60,
                minuteOfDay % 60,
                dayIndex
        );
    }

    public static long exactCalendarDay(World world) {
        long shifted = Math.max(0, world.getTimeOfDay() + TICKS_OFFSET);
        return Math.floorDiv(flowingCalendarTicks(shifted / TICKS_PER_DAY, shifted % TICKS_PER_DAY), TICKS_PER_DAY);
    }

    public static long spreadCalendarDay(long gameDay, long seed) {
        long start = calendarDay(gameDay);
        long span = calendarDay(gameDay + 1) - start;
        return span <= 1 ? start : start + Math.floorMod(seed, span);
    }

    public static LocalDate dateOfCalendarDay(long calendarDay) {
        return FIRST_DAY.plusDays(calendarDay);
    }

    public static int displayYearOfCalendarDay(long calendarDay) {
        int year = dateOfCalendarDay(calendarDay).getYear();
        return year <= 0 ? year - 1 : year;
    }

    public static LocalDate dateOfDay(long dayIndex) {
        return FIRST_DAY.plusDays(calendarDay(dayIndex));
    }

    public static int displayYearOfDay(long dayIndex) {
        int year = dateOfDay(dayIndex).getYear();
        return year <= 0 ? year - 1 : year;
    }

    public record Segment(long gameStart, long calendarStart, double speed) {
    }

    public record CalendarDate(int year, int month, int day, DayOfWeek dayOfWeek, int hour, int minute, long dayIndex) {
    }
}
