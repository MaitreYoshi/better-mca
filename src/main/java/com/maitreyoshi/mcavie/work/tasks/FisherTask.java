package com.maitreyoshi.mcavie.work.tasks;

import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkGesture;
import com.maitreyoshi.mcavie.work.WorkManager;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import java.util.List;

public class FisherTask extends AbstractJobTask {
    private static final int SCAN_RANGE = 12;
    private static final int SCAN_HEIGHT = 3;
    private static final int MIN_WATER_AROUND = 5;
    private static final int SCAN_COOLDOWN = 200;
    private static final int CATCH_MIN = 200;
    private static final int CATCH_RANDOM = 200;
    private static final int BITE_TICKS = 30;
    private static final double ARRIVED_SQ = 2.25;
    private static final int REACH_TICKS = 200;
    private static final long BLACKLIST_TICKS = 2400;

    private BlockPos spot;
    private BlockPos water;
    private long nextScan;
    private int catchTimer;
    private boolean cast;
    private final Reach<BlockPos> reach = new Reach<>(REACH_TICKS, BLACKLIST_TICKS);

    public FisherTask() {
        super(Job.FISHER);
    }

    @Override
    protected boolean canWork(ServerLevel world, VillagerEntityMCA villager) {
        if (spot != null && isStillValid(world, villager)) {
            return true;
        }
        spot = null;
        water = null;
        if (world.getGameTime() < nextScan) {
            return false;
        }
        nextScan = world.getGameTime() + SCAN_COOLDOWN;
        findSpot(world, villager);
        return spot != null;
    }

    @Override
    protected boolean keepWorking(ServerLevel world, VillagerEntityMCA villager) {
        if (spot == null) {
            return false;
        }
        if (villager.tickCount % 40 == 0 && !isStillValid(world, villager)) {
            spot = null;
            water = null;
            return false;
        }
        return true;
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        if (catchTimer <= 0) {
            catchTimer = CATCH_MIN + world.getRandom().nextInt(CATCH_RANDOM);
        }
        cast = false;
        reach.reset();
        holdTool(villager);
    }

    @Override
    protected void tick(ServerLevel world, VillagerEntityMCA villager, long time) {
        if (spot == null || water == null) {
            return;
        }
        if (villager.distanceToSqr(Vec3.atBottomCenterOf(spot)) > ARRIVED_SQ) {
            walkTo(villager, spot);
            if (reach.giveUp(world, spot)) {
                spot = null;
                water = null;
            }
            return;
        }
        reach.reset();
        arrive(villager, WorkGesture.FISH);
        villager.lookAt(water);
        holdTool(villager);
        double wx = water.getX() + 0.5;
        double wy = water.getY() + 1.0;
        double wz = water.getZ() + 0.5;
        if (!cast) {
            villager.swing(InteractionHand.MAIN_HAND);
            world.playSound(null, villager.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5F, 0.6F);
            world.sendParticles(ParticleTypes.SPLASH, wx, wy, wz, 6, 0.2, 0.05, 0.2, 0.1);
            cast = true;
        }
        if (catchTimer % 30 == 0) {
            world.sendParticles(ParticleTypes.FISHING, wx, wy, wz, 2, 0.1, 0.0, 0.1, 0.0);
        }
        if (catchTimer == BITE_TICKS) {
            world.playSound(null, water, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.25F, 1.2F);
        }
        if (catchTimer < BITE_TICKS && catchTimer % 4 == 0) {
            world.sendParticles(ParticleTypes.FISHING, wx, wy, wz, 3, 0.15, 0.0, 0.15, 0.02);
            world.sendParticles(ParticleTypes.BUBBLE, wx, wy - 0.3, wz, 2, 0.15, 0.1, 0.15, 0.05);
        }
        if (--catchTimer > 0) {
            return;
        }
        world.playSound(null, villager.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 0.5F, 1.0F);
        cast = false;
        catchTimer = CATCH_MIN + world.getRandom().nextInt(CATCH_RANDOM);
        catchFish(world, villager);
    }

    @Override
    protected void stop(ServerLevel world, VillagerEntityMCA villager, long time) {
        super.stop(world, villager, time);
        cast = false;
    }

    private void catchFish(ServerLevel world, VillagerEntityMCA villager) {
        LootTable table = world.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
        LootParams params = new LootParams.Builder(world)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(water))
                .withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD))
                .withLuck(0F)
                .create(LootContextParamSets.FISHING);
        List<ItemStack> loot = table.getRandomItems(params);
        for (ItemStack stack : loot) {
            if (!stack.isEmpty()) {
                store(villager, stack);
            }
        }
        villager.swing(InteractionHand.MAIN_HAND);
        gainExperience(villager, 1);
        world.sendParticles(ParticleTypes.SPLASH, water.getX() + 0.5, water.getY() + 1.0, water.getZ() + 0.5, 8, 0.25, 0.05, 0.25, 0.1);
        world.playSound(null, water, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.4F, 1.0F);
    }

    private boolean isStillValid(ServerLevel world, VillagerEntityMCA villager) {
        return spot != null && water != null
                && isLoaded(world, spot) && isLoaded(world, water)
                && inZone(villager, spot)
                && isStandable(world, spot)
                && isGoodWater(world, water);
    }

    private void findSpot(ServerLevel world, VillagerEntityMCA villager) {
        reach.prune(world);
        WorkManager.Zone zone = zone(villager);
        BlockPos origin = zone.center();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        double best = Double.MAX_VALUE;
        for (int dx = -SCAN_RANGE; dx <= SCAN_RANGE; dx++) {
            for (int dz = -SCAN_RANGE; dz <= SCAN_RANGE; dz++) {
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                if (!world.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
                    continue;
                }
                for (int dy = -SCAN_HEIGHT; dy <= SCAN_HEIGHT; dy++) {
                    cursor.set(x, origin.getY() + dy, z);
                    if (!zone.contains(cursor)) {
                        continue;
                    }
                    double dist = cursor.distToCenterSqr(villager.position());
                    if (dist >= best || !isStandable(world, cursor) || reach.isBlacklisted(world, cursor)) {
                        continue;
                    }
                    candidates:
                    for (int drop = 1; drop <= 2; drop++) {
                        for (Direction dir : Direction.Plane.HORIZONTAL) {
                            BlockPos candidate = cursor.below(drop).relative(dir);
                            if (isGoodWater(world, candidate)) {
                                best = dist;
                                spot = cursor.immutable();
                                water = candidate;
                                break candidates;
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean isStandable(ServerLevel world, BlockPos pos) {
        BlockPos ground = pos.below();
        return world.getBlockState(ground).isFaceSturdy(world, ground, Direction.UP)
                && world.getBlockState(pos).isAir()
                && world.getBlockState(pos.above()).isAir();
    }

    private static boolean isGoodWater(ServerLevel world, BlockPos pos) {
        if (!isLoaded(world, pos)) {
            return false;
        }
        BlockState state = world.getBlockState(pos);
        if (!state.is(Blocks.WATER) || !state.getFluidState().isSource() || !world.getBlockState(pos.above()).isAir()) {
            return false;
        }
        int count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos around = pos.offset(dx, 0, dz);
                if (isLoaded(world, around) && world.getBlockState(around).is(Blocks.WATER)) {
                    count++;
                }
            }
        }
        return count >= MIN_WATER_AROUND;
    }
}
