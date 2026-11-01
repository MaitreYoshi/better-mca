package com.maitreyoshi.mcavie.work.tasks;

import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkManager;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class FisherTask extends AbstractJobTask {
    private static final int SCAN_RANGE = 12;
    private static final int SCAN_HEIGHT = 3;
    private static final int MIN_WATER_AROUND = 5;
    private static final int SCAN_COOLDOWN = 200;
    private static final int CATCH_MIN = 200;
    private static final int CATCH_RANDOM = 200;
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
    protected boolean canWork(ServerWorld world, VillagerEntityMCA villager) {
        if (spot != null && isStillValid(world, villager)) {
            return true;
        }
        spot = null;
        water = null;
        if (world.getTime() < nextScan) {
            return false;
        }
        nextScan = world.getTime() + SCAN_COOLDOWN;
        findSpot(world, villager);
        return spot != null;
    }

    @Override
    protected boolean keepWorking(ServerWorld world, VillagerEntityMCA villager) {
        if (spot == null) {
            return false;
        }
        if (villager.age % 40 == 0 && !isStillValid(world, villager)) {
            spot = null;
            water = null;
            return false;
        }
        return true;
    }

    @Override
    protected void run(ServerWorld world, VillagerEntityMCA villager, long time) {
        if (catchTimer <= 0) {
            catchTimer = CATCH_MIN + world.random.nextInt(CATCH_RANDOM);
        }
        cast = false;
        reach.reset();
        holdTool(villager);
    }

    @Override
    protected void keepRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        if (spot == null || water == null) {
            return;
        }
        if (villager.squaredDistanceTo(Vec3d.ofBottomCenter(spot)) > ARRIVED_SQ) {
            villager.moveTowards(spot);
            if (reach.giveUp(world, spot)) {
                spot = null;
                water = null;
            }
            return;
        }
        reach.reset();
        villager.getNavigation().stop();
        villager.lookAt(water);
        holdTool(villager);
        if (!cast) {
            villager.swingHand(Hand.MAIN_HAND);
            cast = true;
        }
        if (--catchTimer > 0) {
            return;
        }
        catchTimer = CATCH_MIN + world.random.nextInt(CATCH_RANDOM);
        catchFish(world, villager);
    }

    @Override
    protected void finishRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        cast = false;
    }

    private void catchFish(ServerWorld world, VillagerEntityMCA villager) {
        LootTable table = world.getServer().getLootManager().getLootTable(LootTables.FISHING_GAMEPLAY);
        LootContextParameterSet params = new LootContextParameterSet.Builder(world)
                .add(LootContextParameters.ORIGIN, Vec3d.ofCenter(water))
                .add(LootContextParameters.TOOL, new ItemStack(Items.FISHING_ROD))
                .luck(0F)
                .build(LootContextTypes.FISHING);
        List<ItemStack> loot = table.generateLoot(params);
        for (ItemStack stack : loot) {
            if (!stack.isEmpty()) {
                store(villager, stack);
            }
        }
        villager.swingHand(Hand.MAIN_HAND);
        gainExperience(villager, 1);
        world.spawnParticles(ParticleTypes.SPLASH, water.getX() + 0.5, water.getY() + 1.0, water.getZ() + 0.5, 8, 0.25, 0.05, 0.25, 0.1);
        world.playSound(null, water, SoundEvents.ENTITY_FISHING_BOBBER_SPLASH, SoundCategory.NEUTRAL, 0.4F, 1.0F);
    }

    private boolean isStillValid(ServerWorld world, VillagerEntityMCA villager) {
        return spot != null && water != null
                && isLoaded(world, spot) && isLoaded(world, water)
                && inZone(villager, spot)
                && isStandable(world, spot)
                && isGoodWater(world, water);
    }

    private void findSpot(ServerWorld world, VillagerEntityMCA villager) {
        reach.prune(world);
        WorkManager.Zone zone = zone(villager);
        BlockPos origin = zone.center();
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        double best = Double.MAX_VALUE;
        for (int dx = -SCAN_RANGE; dx <= SCAN_RANGE; dx++) {
            for (int dz = -SCAN_RANGE; dz <= SCAN_RANGE; dz++) {
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                if (!world.isChunkLoaded(ChunkSectionPos.getSectionCoord(x), ChunkSectionPos.getSectionCoord(z))) {
                    continue;
                }
                for (int dy = -SCAN_HEIGHT; dy <= SCAN_HEIGHT; dy++) {
                    cursor.set(x, origin.getY() + dy, z);
                    if (!zone.contains(cursor)) {
                        continue;
                    }
                    double dist = cursor.getSquaredDistance(villager.getPos());
                    if (dist >= best || !isStandable(world, cursor) || reach.isBlacklisted(world, cursor)) {
                        continue;
                    }
                    // eau au niveau du sol, ou un bloc plus bas (berge surélevée)
                    candidates:
                    for (int drop = 1; drop <= 2; drop++) {
                        for (Direction dir : Direction.Type.HORIZONTAL) {
                            BlockPos candidate = cursor.down(drop).offset(dir);
                            if (isGoodWater(world, candidate)) {
                                best = dist;
                                spot = cursor.toImmutable();
                                water = candidate;
                                break candidates;
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean isStandable(ServerWorld world, BlockPos pos) {
        BlockPos ground = pos.down();
        return world.getBlockState(ground).isSideSolidFullSquare(world, ground, Direction.UP)
                && world.getBlockState(pos).isAir()
                && world.getBlockState(pos.up()).isAir();
    }

    private static boolean isGoodWater(ServerWorld world, BlockPos pos) {
        if (!isLoaded(world, pos)) {
            return false;
        }
        BlockState state = world.getBlockState(pos);
        if (!state.isOf(Blocks.WATER) || !state.getFluidState().isStill() || !world.getBlockState(pos.up()).isAir()) {
            return false;
        }
        int count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos around = pos.add(dx, 0, dz);
                if (isLoaded(world, around) && world.getBlockState(around).isOf(Blocks.WATER)) {
                    count++;
                }
            }
        }
        return count >= MIN_WATER_AROUND;
    }
}
