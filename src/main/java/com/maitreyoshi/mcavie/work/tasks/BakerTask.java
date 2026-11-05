package com.maitreyoshi.mcavie.work.tasks;

import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkGesture;
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
    private static final int WHEAT_PER_TRIP = 24;
    private static final double SUPPLIER_SQ = 64 * 64;
    private static final double REACH_SQ = 6.25;
    private static final int REACH_TICKS = 200;
    private static final long BLACKLIST_TICKS = 2400;

    private long nextScan;
    private boolean lastScan;
    private int bakeTicks;
    private int breads;
    private boolean holdingWheat;
    private BlockPos supplier;
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
        if (!lastScan && chest != null) {
            supplier = findSupplier(world, villager);
            lastScan = supplier != null;
        }
        return lastScan;
    }

    @Override
    protected boolean keepWorking(ServerWorld world, VillagerEntityMCA villager) {
        if (supplier != null) {
            return true;
        }
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
        if (supplier != null) {
            fetchWheat(world, villager);
            return;
        }
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
        arrive(villager, WorkGesture.KNEAD);
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
        supplier = null;
        bakeTicks = 0;
        breads = 0;
        nextScan = 0;
    }

    // four vide : il va chercher le blé rangé par le fermier le plus proche, puis le dépose dans son four
    private BlockPos findSupplier(ServerWorld world, VillagerEntityMCA villager) {
        net.minecraft.util.Identifier dimension = world.getRegistryKey().getValue();
        BlockPos best = null;
        double bestDistance = SUPPLIER_SQ;
        for (com.maitreyoshi.mcavie.work.WorkRegistry.Record record
                : com.maitreyoshi.mcavie.work.WorkRegistry.get(world.getServer()).all()) {
            if (record.job != Job.FARMER || record.depot == null || reach.isBlacklisted(world, record.depot)
                    || (record.depotDimension != null && !record.depotDimension.equals(dimension))) {
                continue;
            }
            double distance = villager.squaredDistanceTo(Vec3d.ofCenter(record.depot));
            Inventory chest = distance < bestDistance ? inventoryAt(world, record.depot) : null;
            if (chest != null && countWheat(chest) >= WHEAT_PER_BREAD) {
                best = record.depot;
                bestDistance = distance;
            }
        }
        return best;
    }

    private void fetchWheat(ServerWorld world, VillagerEntityMCA villager) {
        if (villager.squaredDistanceTo(Vec3d.ofCenter(supplier)) > REACH_SQ) {
            walkTo(villager, supplier);
            if (reach.giveUp(world, supplier)) {
                supplier = null;
            }
            return;
        }
        reach.reset();
        arrive(villager, WorkGesture.NONE);
        villager.lookAt(supplier);
        villager.swingHand(Hand.MAIN_HAND);
        Inventory chest = inventoryAt(world, supplier);
        if (chest != null) {
            int take = Math.min(WHEAT_PER_TRIP, countWheat(chest)) / WHEAT_PER_BREAD * WHEAT_PER_BREAD;
            int left = take;
            for (int i = 0; i < chest.size() && left > 0; i++) {
                ItemStack stack = chest.getStack(i);
                if (stack.isOf(Items.WHEAT)) {
                    int n = Math.min(left, stack.getCount());
                    stack.decrement(n);
                    left -= n;
                }
            }
            chest.markDirty();
            if (take - left > 0) {
                store(villager, new ItemStack(Items.WHEAT, take - left), false);
            }
        }
        supplier = null;
        nextScan = 0;
    }

    private static Inventory inventoryAt(ServerWorld world, BlockPos pos) {
        if (!isLoaded(world, pos)) {
            return null;
        }
        net.minecraft.block.BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof net.minecraft.block.ChestBlock chest) {
            return net.minecraft.block.ChestBlock.getInventory(chest, state, world, pos, true);
        }
        return world.getBlockEntity(pos) instanceof Inventory inventory ? inventory : null;
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
