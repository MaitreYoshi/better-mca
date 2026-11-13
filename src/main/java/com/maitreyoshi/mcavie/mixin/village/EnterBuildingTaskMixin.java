package com.maitreyoshi.mcavie.mixin.village;

import com.maitreyoshi.mcavie.village.BuildingQuality;
import net.conczin.mca.entity.ai.brain.tasks.EnterBuildingTask;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(value = EnterBuildingTask.class, remap = false)
public abstract class EnterBuildingTaskMixin {
    @Unique
    private static final int MCAVIE$ATTEMPTS = 16;

    @Inject(method = "getRandomPositionIn", at = @At("HEAD"), cancellable = true)
    private void mcavie$standingSpot(Building building, Level world, CallbackInfoReturnable<Optional<BlockPos>> cir) {
        if (building.getBuildingType().grouped()) {
            return;
        }
        RandomSource random = world.getRandom();
        BlockPos pos0 = building.getPos0();
        BlockPos pos1 = building.getPos1();
        for (int attempt = 0; attempt < MCAVIE$ATTEMPTS; attempt++) {
            BlockPos pos = new BlockPos(
                    pos0.getX() + random.nextInt(pos1.getX() - pos0.getX() + 1),
                    pos0.getY() + random.nextInt(pos1.getY() - pos0.getY() + 1),
                    pos0.getZ() + random.nextInt(pos1.getZ() - pos0.getZ() + 1));
            if (mcavie$canStand(world, pos)) {
                cir.setReturnValue(Optional.of(pos));
                return;
            }
        }
    }

    @Unique
    private static boolean mcavie$canStand(Level world, BlockPos pos) {
        if (!world.hasChunkAt(pos)) {
            return false;
        }
        BlockPos below = pos.below();
        BlockState ground = world.getBlockState(below);
        return !BuildingQuality.isPassable(ground) && !ground.getCollisionShape(world, below).isEmpty()
                && world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()
                && world.getBlockState(pos.above()).getCollisionShape(world, pos.above()).isEmpty()
                && !world.canSeeSky(pos);
    }
}
