package com.maitreyoshi.mcavie.work;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.BlockEvent;
import java.util.function.LongPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class PlacedLogs extends SavedData {
    private static final String DATA_ID = "mca_vie_placed_logs";

    private final LongOpenHashSet positions = new LongOpenHashSet();

    public static PlacedLogs get(ServerLevel world) {
        return world.getDataStorage().computeIfAbsent(PlacedLogs::fromNbt, PlacedLogs::new, DATA_ID);
    }

    private static PlacedLogs fromNbt(CompoundTag nbt) {
        PlacedLogs registry = new PlacedLogs();
        for (long pos : nbt.getLongArray("positions")) {
            registry.positions.add(pos);
        }
        return registry;
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        nbt.putLongArray("positions", positions.toLongArray());
        return nbt;
    }

    public boolean contains(BlockPos pos) {
        return positions.contains(pos.asLong());
    }

    public void add(BlockPos pos) {
        if (positions.add(pos.asLong())) {
            setDirty();
        }
    }

    public void remove(BlockPos pos) {
        if (positions.remove(pos.asLong())) {
            setDirty();
        }
    }

    public void removeIf(LongPredicate filter) {
        if (positions.removeIf(filter)) {
            setDirty();
        }
    }

    public static void placed(ServerLevel world, BlockPos pos) {
        if (com.maitreyoshi.mcavie.work.tasks.LumberjackTask.isLog(world.getBlockState(pos))) {
            get(world).add(pos);
        }
    }

    public static void register() {
        BlockEvent.BREAK.register((world, pos, state, player, xp) -> {
            if (world instanceof ServerLevel serverWorld && com.maitreyoshi.mcavie.work.tasks.LumberjackTask.isLog(state)) {
                get(serverWorld).remove(pos);
            }
            return EventResult.pass();
        });
    }
}
