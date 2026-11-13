package com.maitreyoshi.mcavie.work.tasks;

import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkGesture;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

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
    protected boolean canWork(ServerLevel world, VillagerEntityMCA villager) {
        long now = world.getGameTime();
        if (now < nextScan) {
            return lastScan;
        }
        nextScan = now + SCAN_INTERVAL;
        BlockPos depot = depotPos(villager);
        Container chest = depot == null || reach.isBlacklisted(world, depot) ? null : depotInventory(world, villager);
        lastScan = chest != null && countWheat(chest) >= WHEAT_PER_BREAD;
        if (!lastScan && chest != null) {
            supplier = findSupplier(world, villager);
            lastScan = supplier != null;
        }
        return lastScan;
    }

    @Override
    protected boolean keepWorking(ServerLevel world, VillagerEntityMCA villager) {
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
        Container chest = depotInventory(world, villager);
        return chest != null && countWheat(chest) >= WHEAT_PER_BREAD;
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        bakeTicks = 0;
        breads = 0;
        holdingWheat = false;
        reach.reset();
        holdTool(villager);
    }

    @Override
    protected void tick(ServerLevel world, VillagerEntityMCA villager, long time) {
        if (supplier != null) {
            fetchWheat(world, villager);
            return;
        }
        BlockPos depot = depotPos(villager);
        if (depot == null) {
            return;
        }
        if (villager.distanceToSqr(Vec3.atCenterOf(depot)) > REACH_SQ) {
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
            Container chest = depotInventory(world, villager);
            if (chest == null || !takeWheat(chest)) {
                return;
            }
            holdingWheat = true;
            bakeTicks = 0;
        }
        bakeTicks++;
        if (bakeTicks % 10 == 1) {
            villager.swing(InteractionHand.MAIN_HAND);
            world.sendParticles(ParticleTypes.SMOKE, villager.getX(), villager.getY(1.0), villager.getZ(), 4, 0.3, 0.2, 0.3, 0.01);
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
    protected void stop(ServerLevel world, VillagerEntityMCA villager, long time) {
        super.stop(world, villager, time);
        if (holdingWheat) {
            ItemStack back = new ItemStack(Items.WHEAT, WHEAT_PER_BREAD);
            Container chest = depotInventory(world, villager);
            if (chest != null) {
                back = DepositTask.insert(chest, back);
                chest.setChanged();
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
    private BlockPos findSupplier(ServerLevel world, VillagerEntityMCA villager) {
        net.minecraft.resources.Identifier dimension = world.dimension().identifier();
        BlockPos best = null;
        double bestDistance = SUPPLIER_SQ;
        for (com.maitreyoshi.mcavie.work.WorkRegistry.Record record
                : com.maitreyoshi.mcavie.work.WorkRegistry.get(world.getServer()).all()) {
            if (record.job != Job.FARMER || record.depot == null || reach.isBlacklisted(world, record.depot)
                    || (record.depotDimension != null && !record.depotDimension.equals(dimension))) {
                continue;
            }
            double distance = villager.distanceToSqr(Vec3.atCenterOf(record.depot));
            Container chest = distance < bestDistance ? inventoryAt(world, record.depot) : null;
            if (chest != null && countWheat(chest) >= WHEAT_PER_BREAD) {
                best = record.depot;
                bestDistance = distance;
            }
        }
        return best;
    }

    private void fetchWheat(ServerLevel world, VillagerEntityMCA villager) {
        if (villager.distanceToSqr(Vec3.atCenterOf(supplier)) > REACH_SQ) {
            walkTo(villager, supplier);
            if (reach.giveUp(world, supplier)) {
                supplier = null;
            }
            return;
        }
        reach.reset();
        arrive(villager, WorkGesture.NONE);
        villager.lookAt(supplier);
        villager.swing(InteractionHand.MAIN_HAND);
        Container chest = inventoryAt(world, supplier);
        if (chest != null) {
            int take = Math.min(WHEAT_PER_TRIP, countWheat(chest)) / WHEAT_PER_BREAD * WHEAT_PER_BREAD;
            int left = take;
            for (int i = 0; i < chest.getContainerSize() && left > 0; i++) {
                ItemStack stack = chest.getItem(i);
                if (stack.is(Items.WHEAT)) {
                    int n = Math.min(left, stack.getCount());
                    stack.shrink(n);
                    left -= n;
                }
            }
            chest.setChanged();
            if (take - left > 0) {
                store(villager, new ItemStack(Items.WHEAT, take - left), false);
            }
        }
        supplier = null;
        nextScan = 0;
    }

    private static Container inventoryAt(ServerLevel world, BlockPos pos) {
        if (!isLoaded(world, pos)) {
            return null;
        }
        net.minecraft.world.level.block.state.BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock chest) {
            return net.minecraft.world.level.block.ChestBlock.getContainer(chest, state, world, pos, true);
        }
        return world.getBlockEntity(pos) instanceof Container inventory ? inventory : null;
    }

    private static int countWheat(Container chest) {
        int total = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack stack = chest.getItem(i);
            if (stack.is(Items.WHEAT)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static boolean takeWheat(Container chest) {
        if (countWheat(chest) < WHEAT_PER_BREAD) {
            return false;
        }
        int left = WHEAT_PER_BREAD;
        for (int i = 0; i < chest.getContainerSize() && left > 0; i++) {
            ItemStack stack = chest.getItem(i);
            if (stack.is(Items.WHEAT)) {
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
        chest.setChanged();
        return true;
    }
}
