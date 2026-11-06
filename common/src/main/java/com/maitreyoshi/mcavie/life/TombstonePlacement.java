package com.maitreyoshi.mcavie.life;

import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class TombstonePlacement {
    private static final int HORIZONTAL_RANGE = 8;
    private static final int VERTICAL_RANGE = 4;

    private TombstonePlacement() {
    }

    public static Optional<BlockPos> findSpot(ServerWorld world, BlockPos deathPos) {
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.iterate(
                deathPos.add(-HORIZONTAL_RANGE, -VERTICAL_RANGE, -HORIZONTAL_RANGE),
                deathPos.add(HORIZONTAL_RANGE, VERTICAL_RANGE, HORIZONTAL_RANGE))) {
            candidates.add(pos.toImmutable());
        }
        candidates.sort(Comparator.comparingDouble(pos -> pos.getSquaredDistance(deathPos)));

        Optional<BlockPos> grounded = candidates.stream().filter(pos -> isFree(world, pos) && hasFloor(world, pos)).findFirst();
        return grounded.isPresent() ? grounded : candidates.stream().filter(pos -> isFree(world, pos)).findFirst();
    }

    private static boolean isFree(ServerWorld world, BlockPos pos) {
        if (!world.isInBuildLimit(pos)) {
            return false;
        }
        BlockState state = world.getBlockState(pos);
        return (state.isAir() || state.isReplaceable()) && state.getFluidState().isEmpty();
    }

    private static boolean hasFloor(ServerWorld world, BlockPos pos) {
        BlockPos below = pos.down();
        return world.getBlockState(below).isSideSolidFullSquare(world, below, Direction.UP);
    }
}
