package com.maitreyoshi.mcavie.work.tasks;

import com.maitreyoshi.mcavie.work.Job;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class BakerTask extends AbstractJobTask {
    private static final int SCAN_INTERVAL = 200;
    private static final int WHEAT_PER_BREAD = 3;
    private static final int BAKE_TICKS = 60;
    private static final int MAX_BREADS = 8;
    private static final double REACH_SQ = 6.25;
    private static final int REACH_TICKS = 200;
    private static final long BLACKLIST_TICKS = 2400;

    private long nextScan;
    private boolean lastScan;
    private int bakeTicks;
    private int breads;
    private boolean holdingWheat;
    private final Reach<BlockPos> reach = new Reach<>(REACH_TICKS, BLACKLIST_TICKS);

    public BakerTask() {
        super(Job.BAKER);
    }

    @Override
    protected boolean canWork(ServerWorld world, VillagerEntityMCA villager) {
        long now = world.getTime();
        if (now < nextScan) {
            return lastScan;
        }
        nextScan = now + SCAN_INTERVAL;
        BlockPos depot = depotPos(villager);
        Inventory chest = depot == null || reach.isBlacklisted(world, depot) ? null : depotInventory(world, villager);
        lastScan = chest != null && countWheat(chest) >= WHEAT_PER_BREAD;
        return lastScan;
    }

    @Override
    protected boolean keepWorking(ServerWorld world, VillagerEntityMCA villager) {
        if (breads >= MAX_BREADS) {
            return false;
        }
        if (holdingWheat) {
            return true;
        }
        BlockPos depot = depotPos(villager);
        if (depot == null || reach.isBlacklisted(world, depot)) {
            return false;
        }
        Inventory chest = depotInventory(world, villager);
        return chest != null && countWheat(chest) >= WHEAT_PER_BREAD;
    }

    @Override
    protected void run(ServerWorld world, VillagerEntityMCA villager, long time) {
        bakeTicks = 0;
        breads = 0;
        holdingWheat = false;
        reach.reset();
        holdTool(villager);
    }

    @Override
    protected void keepRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        BlockPos depot = depotPos(villager);
        if (depot == null) {
            return;
        }
        if (villager.squaredDistanceTo(Vec3d.ofCenter(depot)) > REACH_SQ) {
            walkTo(villager, depot);
            if (reach.giveUp(world, depot)) {
                lastScan = false;
            }
            return;
        }
        reach.reset();
        arrive(villager);
        villager.lookAt(depot);
        holdTool(villager);
        if (!holdingWheat) {
            Inventory chest = depotInventory(world, villager);
            if (chest == null || !takeWheat(chest)) {
                return;
            }
            holdingWheat = true;
            bakeTicks = 0;
        }
        bakeTicks++;
        if (bakeTicks % 10 == 1) {
            villager.swingHand(Hand.MAIN_HAND);
            world.spawnParticles(ParticleTypes.SMOKE, villager.getX(), villager.getBodyY(1.0), villager.getZ(), 4, 0.3, 0.2, 0.3, 0.01);
        }
        if (bakeTicks < BAKE_TICKS) {
            return;
        }
        store(villager, new ItemStack(Items.BREAD));
        gainExperience(villager, 1);
        breads++;
        holdingWheat = false;
        bakeTicks = 0;
    }

    @Override
    protected void finishRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        super.finishRunning(world, villager, time);
        if (holdingWheat) {
            ItemStack back = new ItemStack(Items.WHEAT, WHEAT_PER_BREAD);
            Inventory chest = depotInventory(world, villager);
            if (chest != null) {
                back = DepositTask.insert(chest, back);
                chest.markDirty();
            }
            if (!back.isEmpty()) {
                store(villager, back, false);
            }
        }
        holdingWheat = false;
        bakeTicks = 0;
        breads = 0;
        nextScan = 0;
    }

    private static int countWheat(Inventory chest) {
        int total = 0;
        for (int i = 0; i < chest.size(); i++) {
            ItemStack stack = chest.getStack(i);
            if (stack.isOf(Items.WHEAT)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static boolean takeWheat(Inventory chest) {
        if (countWheat(chest) < WHEAT_PER_BREAD) {
            return false;
        }
        int left = WHEAT_PER_BREAD;
        for (int i = 0; i < chest.size() && left > 0; i++) {
            ItemStack stack = chest.getStack(i);
            if (stack.isOf(Items.WHEAT)) {
                int take = Math.min(left, stack.getCount());
                stack.decrement(take);
                left -= take;
            }
        }
        chest.markDirty();
        return true;
    }
}
