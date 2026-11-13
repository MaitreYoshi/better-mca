package com.maitreyoshi.mcavie.work.tasks;

import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkGesture;
import com.maitreyoshi.mcavie.work.WorkManager;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemGrownBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
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
    protected boolean canWork(ServerLevel world, VillagerEntityMCA villager) {
        if (world.getGameTime() < nextTry) {
            return false;
        }
        refreshScan(world, villager);
        if (findTarget(world, villager) != null) {
            return true;
        }
        nextTry = world.getGameTime() + RETRY_TICKS;
        return false;
    }

    @Override
    protected boolean keepWorking(ServerLevel world, VillagerEntityMCA villager) {
        return target != null;
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        super.start(world, villager, time);
        holdTool(villager);
        selectTarget(world, villager);
    }

    @Override
    protected void stop(ServerLevel world, VillagerEntityMCA villager, long time) {
        super.stop(world, villager, time);
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
    protected void tick(ServerLevel world, VillagerEntityMCA villager, long time) {
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
        double distance = villager.distanceToSqr(Vec3.atCenterOf(walkPos));
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
            plantOnFarmland(world, villager, done.above());
        }
        candidates.remove(done);
        selectTarget(world, villager);
    }

    private void selectTarget(ServerLevel world, VillagerEntityMCA villager) {
        clearBreaking(world, villager, target);
        BlockPos next = findTarget(world, villager);
        target = next;
        targetKind = next == null ? null : classify(world, next);
        reach.reset();
        workTicks = 0;
    }

    private BlockPos findTarget(ServerLevel world, VillagerEntityMCA villager) {
        reach.prune(world);
        WorkManager.Zone zone = zone(villager);
        boolean hasSeed = bestSeed(villager.getInventory()) != null;

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos from = villager.blockPosition();

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
            double distance = from.distSqr(pos);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos;
            }
        }
        return best;
    }

    private void refreshScan(ServerLevel world, VillagerEntityMCA villager) {
        long now = world.getGameTime();
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

    private Kind classify(ServerLevel world, BlockPos pos) {
        if (!isLoaded(world, pos)) {
            return null;
        }
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) {
            return crop.isMaxAge(state) ? Kind.HARVEST : null;
        }
        if (block instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) >= 3 ? Kind.HARVEST : null;
        }
        if (block instanceof StemGrownBlock) {
            return hasAttachedStem(world, pos) ? Kind.HARVEST : null;
        }
        if (state.is(Blocks.FARMLAND) && world.getBlockState(pos.above()).isAir()) {
            return Kind.PLANT;
        }
        return null;
    }

    private boolean hasAttachedStem(ServerLevel world, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(direction);
            if (!isLoaded(world, side)) {
                continue;
            }
            BlockState neighbor = world.getBlockState(side);
            if (neighbor.getBlock() instanceof AttachedStemBlock
                    && neighbor.getValue(AttachedStemBlock.FACING) == direction.getOpposite()) {
                return true;
            }
        }
        return false;
    }

    private BlockPos walkPos(BlockPos pos, Kind kind) {
        return kind == Kind.PLANT ? pos.above() : pos;
    }

    private void harvest(ServerLevel world, VillagerEntityMCA villager, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (classify(world, pos) != Kind.HARVEST) {
            return;
        }
        boolean replant = !(block instanceof StemGrownBlock);
        if (!mayAlter(world, pos)) {
            return;
        }

        if (!world.destroyBlock(pos, false, villager)) {
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

    private void replant(ServerLevel world, VillagerEntityMCA villager, BlockPos pos, Block block) {
        SimpleContainer inventory = villager.getInventory();
        ItemStack seed = null;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
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

    private void plantOnFarmland(ServerLevel world, VillagerEntityMCA villager, BlockPos pos) {
        ItemStack seed = bestSeed(villager.getInventory());
        if (seed != null && place(world, pos, seed)) {
            gainExperience(villager, 1);
        }
    }

    private boolean place(ServerLevel world, BlockPos pos, ItemStack seed) {
        if (!(seed.getItem() instanceof BlockItem item)) {
            return false;
        }
        BlockState plant = item.getBlock().defaultBlockState();
        if (!world.getBlockState(pos).isAir() || !plant.canSurvive(world, pos) || !mayAlter(world, pos)) {
            return false;
        }
        world.setBlock(pos, plant, Block.UPDATE_ALL);
        seed.shrink(1);
        return true;
    }

    private ItemStack bestSeed(SimpleContainer inventory) {
        ItemStack best = null;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
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
