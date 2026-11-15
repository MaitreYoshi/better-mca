package com.maitreyoshi.mcavie.work.tasks;

import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkGesture;
import com.maitreyoshi.mcavie.work.WorkManager;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.block.AttachedStemBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.GourdBlock;
import net.minecraft.block.NetherWartBlock;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FarmerTask extends AbstractJobTask {
    private static final long SCAN_INTERVAL = 1200;
    private static final int SCAN_HEIGHT = 6;
    private static final double WORK_DISTANCE_SQ = 6.25;
    private static final int WORK_TICKS = 30;
    private static final int REACH_TICKS = 200;
    private static final long BLACKLIST_TICKS = 2400;
    private static final int RETRY_TICKS = 20;
    private static final int SCAN_JITTER = 100;

    private enum Kind {
        HARVEST, PLANT
    }

    private final List<BlockPos> candidates = new ArrayList<>();
    private final ZoneScan scan = new ZoneScan(SCAN_HEIGHT);
    private final Reach<BlockPos> reach = new Reach<>(REACH_TICKS, BLACKLIST_TICKS);
    private long nextScan = -1;
    private long nextTry;

    private BlockPos target;
    private Kind targetKind;
    private int workTicks;

    public FarmerTask() {
        super(Job.FARMER);
    }

    @Override
    protected boolean canWork(ServerWorld world, VillagerEntityMCA villager) {
        if (world.getTime() < nextTry) {
            return false;
        }
        refreshScan(world, villager);
        if (findTarget(world, villager) != null) {
            return true;
        }
        nextTry = world.getTime() + RETRY_TICKS;
        return false;
    }

    @Override
    protected boolean keepWorking(ServerWorld world, VillagerEntityMCA villager) {
        return target != null;
    }

    @Override
    protected void run(ServerWorld world, VillagerEntityMCA villager, long time) {
        super.run(world, villager, time);
        holdTool(villager);
        selectTarget(world, villager);
    }

    @Override
    protected void finishRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        super.finishRunning(world, villager, time);
        clearBreaking(world, villager, target);
        target = null;
        targetKind = null;
        reach.reset();
        workTicks = 0;
    }

    @Override
    protected boolean isSearching() {
        return scan.running();
    }

    @Override
    protected boolean altersBlocks() {
        return true;
    }

    @Override
    protected void keepRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        if (target == null) {
            return;
        }
        Kind kind = classify(world, target);
        if (kind == null || kind != targetKind || !inZone(villager, target)) {
            selectTarget(world, villager);
            return;
        }
        holdTool(villager);

        BlockPos walkPos = walkPos(target, kind);
        double distance = villager.squaredDistanceTo(Vec3d.ofCenter(walkPos));
        if (distance > WORK_DISTANCE_SQ) {
            walkTo(villager, walkPos);
            if (reach.giveUp(world, target)) {
                selectTarget(world, villager);
            }
            return;
        }
        arrive(villager, WorkGesture.HOE);
        villager.lookAt(target);

        workTicks++;
        hitBlock(world, villager, target, workTicks, WORK_TICKS);
        if (workTicks < WORK_TICKS) {
            return;
        }

        BlockPos done = target;
        clearBreaking(world, villager, done);
        if (kind == Kind.HARVEST) {
            harvest(world, villager, done);
        } else {
            plantOnFarmland(world, villager, done.up());
        }
        candidates.remove(done);
        selectTarget(world, villager);
    }

    private void selectTarget(ServerWorld world, VillagerEntityMCA villager) {
        clearBreaking(world, villager, target);
        BlockPos next = findTarget(world, villager);
        target = next;
        targetKind = next == null ? null : classify(world, next);
        reach.reset();
        workTicks = 0;
    }

    private BlockPos findTarget(ServerWorld world, VillagerEntityMCA villager) {
        reach.prune(world);
        WorkManager.Zone zone = zone(villager);
        boolean hasSeed = bestSeed(villager.getInventory()) != null;

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos from = villager.getBlockPos();

        Iterator<BlockPos> it = candidates.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            Kind kind = classify(world, pos);
            if (kind == null) {
                it.remove();
                continue;
            }
            if (reach.isBlacklisted(world, pos) || !zone.contains(pos) || !mayAlter(world, pos)) {
                continue;
            }
            if (kind == Kind.PLANT && !hasSeed) {
                continue;
            }
            double distance = from.getSquaredDistance(pos);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos;
            }
        }
        return best;
    }

    private void refreshScan(ServerWorld world, VillagerEntityMCA villager) {
        long now = world.getTime();
        if (!scan.running()) {
            if (nextScan < 0 || now - nextScan > SCAN_INTERVAL) {
                nextScan = now + world.random.nextInt(SCAN_JITTER);
            }
            if (now < nextScan) {
                return;
            }
            scan.start(zone(villager));
        }
        if (scan.step(world, pos -> classify(world, pos) != null)) {
            candidates.clear();
            candidates.addAll(scan.result());
            nextScan = now + SCAN_INTERVAL;
        }
    }

    private Kind classify(ServerWorld world, BlockPos pos) {
        if (!isLoaded(world, pos)) {
            return null;
        }
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) {
            return crop.isMature(state) ? Kind.HARVEST : null;
        }
        if (block instanceof NetherWartBlock) {
            return state.get(NetherWartBlock.AGE) >= 3 ? Kind.HARVEST : null;
        }
        if (block instanceof GourdBlock) {
            return hasAttachedStem(world, pos) ? Kind.HARVEST : null;
        }
        if (state.isOf(Blocks.FARMLAND) && world.getBlockState(pos.up()).isAir()) {
            return Kind.PLANT;
        }
        return null;
    }

    private boolean hasAttachedStem(ServerWorld world, BlockPos pos) {
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos side = pos.offset(direction);
            if (!isLoaded(world, side)) {
                continue;
            }
            BlockState neighbor = world.getBlockState(side);
            if (neighbor.getBlock() instanceof AttachedStemBlock
                    && neighbor.get(AttachedStemBlock.FACING) == direction.getOpposite()) {
                return true;
            }
        }
        return false;
    }

    private BlockPos walkPos(BlockPos pos, Kind kind) {
        return kind == Kind.PLANT ? pos.up() : pos;
    }

    private void harvest(ServerWorld world, VillagerEntityMCA villager, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (classify(world, pos) != Kind.HARVEST) {
            return;
        }
        boolean replant = !(block instanceof GourdBlock);
        if (!mayAlter(world, pos)) {
            return;
        }

        if (!world.breakBlock(pos, false, villager)) {
            return;
        }
        for (ItemStack drop : blockDrops(world, villager, pos, state)) {
            store(villager, drop);
        }
        gainExperience(villager, 1);

        if (replant) {
            replant(world, villager, pos, block);
        }
    }

    private void replant(ServerWorld world, VillagerEntityMCA villager, BlockPos pos, Block block) {
        SimpleInventory inventory = villager.getInventory();
        ItemStack seed = null;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem item && item.getBlock() == block) {
                seed = stack;
                break;
            }
        }
        if (seed == null) {
            seed = bestSeed(inventory);
        }
        if (seed != null) {
            place(world, pos, seed);
        }
    }

    private void plantOnFarmland(ServerWorld world, VillagerEntityMCA villager, BlockPos pos) {
        ItemStack seed = bestSeed(villager.getInventory());
        if (seed != null && place(world, pos, seed)) {
            gainExperience(villager, 1);
        }
    }

    private boolean place(ServerWorld world, BlockPos pos, ItemStack seed) {
        if (!(seed.getItem() instanceof BlockItem item)) {
            return false;
        }
        BlockState plant = item.getBlock().getDefaultState();
        if (!world.getBlockState(pos).isAir() || !plant.canPlaceAt(world, pos) || !mayAlter(world, pos)) {
            return false;
        }
        world.setBlockState(pos, plant, Block.NOTIFY_ALL);
        seed.decrement(1);
        return true;
    }

    private ItemStack bestSeed(SimpleInventory inventory) {
        ItemStack best = null;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem item)
                    || !(item.getBlock() instanceof CropBlock)) {
                continue;
            }
            if (best == null || stack.getCount() > best.getCount()) {
                best = stack;
            }
        }
        return best;
    }
}
