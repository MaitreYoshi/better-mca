package com.maitreyoshi.mcavie.calendar;

import com.maitreyoshi.mcavie.compat.Saved;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.ArrayList;
import java.util.List;

public class CalendarState extends SavedData {
    private static final String DATA_ID = "mca_vie_calendar";

    private final List<WorldCalendar.Segment> segments = new ArrayList<>();

    public static CalendarState get(MinecraftServer server) {
        return Saved.get(server.overworld(), CalendarState::fromNbt, CalendarState::new, CalendarState::save, DATA_ID);
    }

    private static CalendarState fromNbt(CompoundTag nbt) {
        CalendarState state = new CalendarState();
        ListTag list = nbt.getListOrEmpty("segments");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompoundOrEmpty(i);
            double speed = entry.getDoubleOr("speed", 0d);
            if (speed > 0) {
                // anciens mondes : segments en jours entiers ; désormais en ticks pour garder la date exacte au changement de vitesse
                long game = entry.contains("gameTick") ? entry.getLongOr("gameTick", 0L) : entry.getLongOr("game", 0L) * WorldCalendar.TICKS_PER_DAY;
                long calendar = entry.contains("calendarTick") ? entry.getLongOr("calendarTick", 0L) : entry.getLongOr("calendar", 0L) * WorldCalendar.TICKS_PER_DAY;
                state.segments.add(new WorldCalendar.Segment(game, calendar, speed));
            }
        }
        return state;
    }

        public CompoundTag save(CompoundTag nbt) {
        ListTag list = new ListTag();
        for (WorldCalendar.Segment segment : segments) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("gameTick", segment.gameStart());
            entry.putLong("calendarTick", segment.calendarStart());
            // compatibilité si le monde est rouvert avec une version précédente de l'addon
            entry.putLong("game", Math.floorDiv(segment.gameStart(), WorldCalendar.TICKS_PER_DAY));
            entry.putLong("calendar", Math.floorDiv(segment.calendarStart(), WorldCalendar.TICKS_PER_DAY));
            entry.putDouble("speed", segment.speed());
            list.add(entry);
        }
        nbt.put("segments", list);
        return nbt;
    }

    public List<WorldCalendar.Segment> segments() {
        return List.copyOf(segments);
    }

    public void setSegments(List<WorldCalendar.Segment> value) {
        segments.clear();
        segments.addAll(value);
        setDirty();
    }
}
