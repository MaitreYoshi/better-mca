package com.maitreyoshi.mcavie.calendar;

import com.maitreyoshi.mcavie.MCAVie;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.gamerule.v1.CustomGameRuleCategory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.fabricmc.fabric.api.gamerule.v1.rule.EnumRule;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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

    public static GameRules.Key<EnumRule<TimeSpeed>> TIME_SPEED;

    private static volatile MinecraftServer server;
    private static volatile List<Segment> serverSegments = DEFAULT;
    private static volatile List<Segment> clientSegments = DEFAULT;

    private WorldCalendar() {
    }

    public static void register() {
        TIME_SPEED = GameRuleRegistry.register("mcaVieTimeSpeed",
                new CustomGameRuleCategory(MCAVie.id("calendar"), Text.translatable("gamerule.category.mca_vie")),
                GameRuleFactory.createEnumRule(TimeSpeed.DAY, WorldCalendar::onSpeedChanged));
        ServerWorldEvents.LOAD.register((s, world) -> {
            if (world.getRegistryKey() == World.OVERWORLD) {
                server = s;
                CalendarState state = CalendarState.get(s);
                if (state.segments().isEmpty()) {
                    state.setSegments(List.of(new Segment(0, 0, s.getGameRules().get(TIME_SPEED).get().calendarDaysPerGameDay())));
                }
                serverSegments = state.segments();
                // Les anciennes valeurs (jour, mois…) ne se relisent plus : on recale la règle sur la vitesse réellement enregistrée.
                double speed = serverSegments.get(serverSegments.size() - 1).speed();
                EnumRule<TimeSpeed> rule = s.getGameRules().get(TIME_SPEED);
                if (rule.get().calendarDaysPerGameDay() != speed) {
                    for (TimeSpeed value : TimeSpeed.values()) {
                        if (value.calendarDaysPerGameDay() == speed) {
                            rule.set(value, s);
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

    private static void onSpeedChanged(MinecraftServer s, EnumRule<TimeSpeed> rule) {
        if (s == null || s.getOverworld() == null) {
            return;
        }
        CalendarState state = CalendarState.get(s);
        List<Segment> segments = new ArrayList<>(state.segments().isEmpty() ? DEFAULT : state.segments());
        double speed = rule.get().calendarDaysPerGameDay();
        Segment last = segments.get(segments.size() - 1);
        if (last.speed() == speed) {
            return;
        }
        long today = fromTicks(s.getOverworld().getTimeOfDay()).dayIndex();
        long calendarToday = calendarDay(segments, today);
        if (last.gameStart() >= today) {
            segments.remove(segments.size() - 1);
        }
        segments.add(new Segment(today, calendarToday, speed));
        state.setSegments(segments);
        server = s;
        serverSegments = state.segments();
        s.getPlayerManager().getPlayerList().forEach(WorldCalendar::sendSegments);
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

    private static Segment segmentAt(List<Segment> segments, long gameDay) {
        for (int i = segments.size() - 1; i > 0; i--) {
            if (segments.get(i).gameStart() <= gameDay) {
                return segments.get(i);
            }
        }
        return segments.get(0);
    }

    private static long calendarDay(List<Segment> segments, long gameDay) {
        Segment s = segmentAt(segments, gameDay);
        return s.calendarStart() + (long) Math.floor((gameDay - s.gameStart()) * s.speed());
    }

    public static long calendarDay(long gameDay) {
        return calendarDay(active(), gameDay);
    }

    private static long flowingCalendarTicks(long gameDay, long tickOfDay) {
        Segment s = segmentAt(active(), gameDay);
        return s.calendarStart() * TICKS_PER_DAY + (long) Math.floor(((gameDay - s.gameStart()) * (double) TICKS_PER_DAY + tickOfDay) * s.speed());
    }

    public static long gameDayOf(long calendarDay) {
        List<Segment> segments = active();
        Segment s = segments.get(0);
        for (int i = segments.size() - 1; i > 0; i--) {
            if (segments.get(i).calendarStart() <= calendarDay) {
                s = segments.get(i);
                break;
            }
        }
        return s.gameStart() + (long) Math.floor((calendarDay - s.calendarStart()) / s.speed());
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
        return segmentAt(active(), gameDay).speed();
    }

    public static double calendarDaysBetween(long fromGameDay, long toGameDay) {
        return (double) calendarDay(toGameDay) - (double) calendarDay(fromGameDay);
    }

    public static double yearsBetween(long fromGameDay, long toGameDay) {
        return calendarDaysBetween(fromGameDay, toGameDay) / DAYS_PER_YEAR;
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

    /** Jour du calendrier affiché par la montre en ce moment, même quand une journée Minecraft vaut un mois ou plus. */
    public static long exactCalendarDay(World world) {
        long shifted = Math.max(0, world.getTimeOfDay() + TICKS_OFFSET);
        return Math.floorDiv(flowingCalendarTicks(shifted / TICKS_PER_DAY, shifted % TICKS_PER_DAY), TICKS_PER_DAY);
    }

    /**
     * Jour du calendrier pour un événement dont on ne connaît que la journée Minecraft : un jour stable (tiré de
     * la graine) parmi ceux que cette journée couvre, au lieu de toujours le premier.
     */
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
