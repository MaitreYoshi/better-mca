package com.maitreyoshi.mcavie.life;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public final class TombstonePlacement {
    private static final int HORIZONTAL_RANGE = 8;
    private static final int VERTICAL_RANGE = 4;

    private TombstonePlacement() {
    }

    public static Optional<BlockPos> findSpot(ServerLevel world, BlockPos deathPos) {
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                deathPos.offset(-HORIZONTAL_RANGE, -VERTICAL_RANGE, -HORIZONTAL_RANGE),
                deathPos.offset(HORIZONTAL_RANGE, VERTICAL_RANGE, HORIZONTAL_RANGE))) {
            candidates.add(pos.immutable());
        }
        candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(deathPos)));

        Optional<BlockPos> grounded = candidates.stream().filter(pos -> isFree(world, pos) && hasFloor(world, pos)).findFirst();
        return grounded.isPresent() ? grounded : candidates.stream().filter(pos -> isFree(world, pos)).findFirst();
    }

    public static Optional<BlockPos> surface(ServerLevel world, BlockPos deathPos) {
        BlockPos top = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, deathPos);
        for (int dy = 0; dy < 16 && world.isInWorldBounds(top.above(dy)); dy++) {
            if (isFree(world, top.above(dy))) {
                return Optional.of(top.above(dy));
            }
        }
        return Optional.empty();
    }

    private static boolean isFree(ServerLevel world, BlockPos pos) {
        if (!world.isInWorldBounds(pos)) {
            return false;
        }
        BlockState state = world.getBlockState(pos);
        return (state.isAir() || state.canBeReplaced()) && state.getFluidState().isEmpty();
    }

    private static boolean hasFloor(ServerLevel world, BlockPos pos) {
        BlockPos below = pos.below();
        return world.getBlockState(below).isFaceSturdy(world, below, Direction.UP);
    }
}
