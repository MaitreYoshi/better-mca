package com.maitreyoshi.mcavie.mixin.village;

import com.maitreyoshi.mcavie.village.BuildingQuality;
import net.mca.entity.ai.brain.tasks.EnterBuildingTask;
import net.mca.server.world.data.Building;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
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
    private void mcavie$standingSpot(Building building, World world, CallbackInfoReturnable<Optional<BlockPos>> cir) {
        if (building.getBuildingType().grouped()) {
            return;
        }
        Random random = world.getRandom();
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
    private static boolean mcavie$canStand(World world, BlockPos pos) {
        if (!world.isChunkLoaded(pos)) {
            return false;
        }
        BlockPos below = pos.down();
        BlockState ground = world.getBlockState(below);
        return !BuildingQuality.isPassable(ground) && !ground.getCollisionShape(world, below).isEmpty()
                && world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()
                && world.getBlockState(pos.up()).getCollisionShape(world, pos.up()).isEmpty()
                && !world.isSkyVisible(pos);
    }
}
