package com.maitreyoshi.mcavie.calendar;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.List;

public class CalendarState extends PersistentState {
    private static final String DATA_ID = "mca_vie_calendar";

    private final List<WorldCalendar.Segment> segments = new ArrayList<>();

    public static CalendarState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(CalendarState::fromNbt, CalendarState::new, DATA_ID);
    }

    private static CalendarState fromNbt(NbtCompound nbt) {
        CalendarState state = new CalendarState();
        NbtList list = nbt.getList("segments", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound entry = list.getCompound(i);
            double speed = entry.getDouble("speed");
            if (speed > 0) {
                state.segments.add(new WorldCalendar.Segment(entry.getLong("game"), entry.getLong("calendar"), speed));
            }
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (WorldCalendar.Segment segment : segments) {
            NbtCompound entry = new NbtCompound();
            entry.putLong("game", segment.gameStart());
            entry.putLong("calendar", segment.calendarStart());
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
        markDirty();
    }
}
