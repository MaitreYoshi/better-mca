package com.maitreyoshi.mcavie.work;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.function.LongPredicate;

public class PlacedLogs extends PersistentState {
    private static final String DATA_ID = "mca_vie_placed_logs";

    private final LongOpenHashSet positions = new LongOpenHashSet();

    public static PlacedLogs get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(PlacedLogs::fromNbt, PlacedLogs::new, DATA_ID);
    }

    private static PlacedLogs fromNbt(NbtCompound nbt) {
        PlacedLogs registry = new PlacedLogs();
        for (long pos : nbt.getLongArray("positions")) {
            registry.positions.add(pos);
        }
        return registry;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putLongArray("positions", positions.toLongArray());
        return nbt;
    }

    public boolean contains(BlockPos pos) {
        return positions.contains(pos.asLong());
    }

    public void add(BlockPos pos) {
        if (positions.add(pos.asLong())) {
            markDirty();
        }
    }

    public void remove(BlockPos pos) {
        if (positions.remove(pos.asLong())) {
            markDirty();
        }
    }

    public void removeIf(LongPredicate filter) {
        if (positions.removeIf(filter)) {
            markDirty();
        }
    }

    public static void placed(ServerWorld world, BlockPos pos) {
        if (world.getBlockState(pos).isIn(BlockTags.LOGS)) {
            get(world).add(pos);
        }
    }

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world instanceof ServerWorld serverWorld && state.isIn(BlockTags.LOGS)) {
                get(serverWorld).remove(pos);
            }
        });
    }
}
