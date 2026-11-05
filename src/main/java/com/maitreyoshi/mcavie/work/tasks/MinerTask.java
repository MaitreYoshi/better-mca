package com.maitreyoshi.mcavie.work.tasks;

import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkGesture;
import com.maitreyoshi.mcavie.work.WorkManager;
import com.maitreyoshi.mcavie.work.WorkRegistry;
import com.maitreyoshi.mcavie.work.WorkStatus;
import com.maitreyoshi.mcavie.work.block.WorkstationBlock;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.LightType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MinerTask extends AbstractJobTask {
    private static final int DEPTH = 12;
    private static final int MAX_MAIN = 32;
    private static final int BRANCH_EVERY = 3;
    private static final int BRANCH_LENGTH = 8;
    private static final int TORCH_EVERY = 8;
    private static final float MAX_HARDNESS = 50F;
    private static final double WORK_DISTANCE_SQ = 12.25;
    private static final int[] TICKS_PER_BLOCK = {50, 42, 34, 26, 18};
    private static final int REACH_TICKS = 240;
    private static final long BLACKLIST_TICKS = 600;
    private static final int RETRY_TICKS = 100;
    private static final long ORE_SCAN_INTERVAL = 200;
    private static final List<TagKey<net.minecraft.block.Block>> ORES = List.of(BlockTags.COAL_ORES, BlockTags.IRON_ORES, BlockTags.COPPER_ORES,
            BlockTags.GOLD_ORES, BlockTags.REDSTONE_ORES, BlockTags.LAPIS_ORES, BlockTags.DIAMOND_ORES, BlockTags.EMERALD_ORES);

    private record Segment(List<BlockPos> blocks, boolean vital) {
    }

    private final Reach<BlockPos> reach = new Reach<>(REACH_TICKS, BLACKLIST_TICKS);
    private final List<Segment> plan = new ArrayList<>();
    private final Set<BlockPos> feet = new HashSet<>();
    private final Set<BlockPos> torchSpots = new HashSet<>();
    private final Set<BlockPos> ores = new LinkedHashSet<>();
    @Nullable
    private BlockPos planCart;
    @Nullable
    private Direction planDirection;
    private long nextOreScan;
    private long nextTry;
    @Nullable
    private BlockPos target;
    private int mineTicks;
    private int mineTotal;

    public MinerTask() {
        super(Job.MINER);
    }

    @Override
    protected boolean canWork(ServerWorld world, VillagerEntityMCA villager) {
        if (world.getTime() < nextTry) {
            return false;
        }
        if (updatePlan(world, villager)) {
            if (world.getTime() >= nextOreScan) {
                nextOreScan = world.getTime() + ORE_SCAN_INTERVAL;
                scanOres(world, villager);
            }
            if (findTarget(world, villager) != null) {
                return true;
            }
        }
        nextTry = world.getTime() + RETRY_TICKS;
        return false;
    }

    @Override
    protected boolean keepWorking(ServerWorld world, VillagerEntityMCA villager) {
        return target != null;
    }

    @Override
    @Nullable
    protected WorkStatus idleStatus(ServerWorld world, VillagerEntityMCA villager) {
        return depotPos(villager) != null && bestPickaxe(world, villager, null) == null ? WorkStatus.NO_TOOL : null;
    }

    @Override
    protected void holdTool(VillagerEntityMCA villager) {
        ItemStack pickaxe = villager.getWorld() instanceof ServerWorld world ? bestPickaxe(world, villager, null) : null;
        if (pickaxe == null) {
            super.holdTool(villager);
        } else {
            WorkManager.giveTool(villager, pickaxe.copyWithCount(1));
        }
    }

    @Nullable
    private static ItemStack bestPickaxe(ServerWorld world, VillagerEntityMCA villager, @Nullable BlockState state) {
        Inventory cart = depotInventory(world, villager);
        BlockState reference = state == null ? Blocks.STONE.getDefaultState() : state;
        ItemStack best = null;
        float bestSpeed = 0F;
        for (int i = 0; cart != null && i < cart.size(); i++) {
            ItemStack stack = cart.getStack(i);
            if (stack.isEmpty() || !stack.isIn(ItemTags.PICKAXES)
                    || (state != null && state.isToolRequired() && !stack.isSuitableFor(state))) {
                continue;
            }
            float speed = stack.getMiningSpeedMultiplier(reference);
            if (best == null || speed > bestSpeed) {
                best = stack;
                bestSpeed = speed;
            }
        }
        return best;
    }

    @Override
    protected boolean altersBlocks() {
        return true;
    }

    @Override
    protected void run(ServerWorld world, VillagerEntityMCA villager, long time) {
        holdTool(villager);
        selectTarget(world, villager);
    }

    @Override
    protected void finishRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        super.finishRunning(world, villager, time);
        clearBreaking(world, villager, target);
        target = null;
        reach.reset();
        mineTicks = 0;
    }

    @Override
    protected void keepRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        if (target == null) {
            return;
        }
        if (!canDig(world, villager, target)) {
            selectTarget(world, villager);
            return;
        }
        holdTool(villager);
        if (!inReach(villager, target)) {
            if (mineTicks > 0) {
                clearBreaking(world, villager, target);
                mineTicks = 0;
            }
            walkTo(villager, standPos(world, target));
            if (reach.giveUp(world, target)) {
                selectTarget(world, villager);
            }
            return;
        }
        arrive(villager, WorkGesture.MINE);
        villager.lookAt(target);
        if (mineTicks == 0) {
            mineTotal = mineDuration(world, villager, target);
        }
        mineTicks++;
        hitBlock(world, villager, target, mineTicks, mineTotal);
        if (mineTicks < mineTotal) {
            return;
        }
        BlockPos done = target;
        clearBreaking(world, villager, done);
        mine(world, villager, done);
        ores.remove(done);
        for (Direction direction : Direction.values()) {
            addOre(world, villager, done.offset(direction));
        }
        placeTorch(world, villager, done);
        selectTarget(world, villager);
    }

    private void mine(ServerWorld world, VillagerEntityMCA villager, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!mayAlter(world, pos) || !world.breakBlock(pos, false, villager)) {
            return;
        }
        Inventory cart = depotInventory(world, villager);
        ItemStack pickaxe = bestPickaxe(world, villager, state);
        List<ItemStack> drops = pickaxe == null ? List.of() : blockDrops(world, villager, pos, state, pickaxe);
        if (pickaxe != null && pickaxe.damage(1, world.random, null)) {
            pickaxe.decrement(1);
            world.playSound(null, villager.getBlockPos(), SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.NEUTRAL, 0.8F, 1.0F);
        }
        for (ItemStack drop : drops) {
            if (cart == null) {
                store(villager, drop);
                continue;
            }
            addBonus(villager, drop);
            ItemStack rest = DepositTask.insert(cart, drop);
            if (!rest.isEmpty()) {
                store(villager, rest, false);
            }
        }
        if (cart != null) {
            cart.markDirty();
        }
        gainExperience(villager, isOre(state) ? 2 : 1);
    }

    private void placeTorch(ServerWorld world, VillagerEntityMCA villager, BlockPos pos) {
        if (!torchSpots.contains(pos) || !world.getBlockState(pos).isAir()
                || world.getLightLevel(LightType.BLOCK, pos) >= 8 || !Blocks.TORCH.getDefaultState().canPlaceAt(world, pos)) {
            return;
        }
        Inventory cart = depotInventory(world, villager);
        for (int i = 0; cart != null && i < cart.size(); i++) {
            ItemStack stack = cart.getStack(i);
            if (stack.isOf(Items.TORCH)) {
                stack.decrement(1);
                cart.markDirty();
                world.setBlockState(pos, Blocks.TORCH.getDefaultState());
                world.playSound(null, pos, SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 0.6F, 1.0F);
                return;
            }
        }
    }

    private int mineDuration(ServerWorld world, VillagerEntityMCA villager, BlockPos pos) {
        WorkRegistry.Record record = WorkManager.findRecord(villager);
        int level = record == null ? 1 : record.level;
        int perBlock = TICKS_PER_BLOCK[Math.max(0, Math.min(TICKS_PER_BLOCK.length - 1, level - 1))];
        BlockState state = world.getBlockState(pos);
        float hardness = Math.max(0.5F, state.getHardness(world, pos));
        ItemStack pickaxe = bestPickaxe(world, villager, state);
        float speed = pickaxe == null ? 1F : pickaxe.getMiningSpeedMultiplier(state);
        int efficiency = pickaxe == null ? 0 : EnchantmentHelper.getLevel(Enchantments.EFFICIENCY, pickaxe);
        if (speed > 1F && efficiency > 0) {
            speed += efficiency * efficiency + 1;
        }
        return Math.max(10, Math.round(perBlock * hardness / 1.5F * 6F / Math.max(1F, speed)));
    }

    private static boolean inReach(VillagerEntityMCA villager, BlockPos pos) {
        double dx = villager.getX() - (pos.getX() + 0.5);
        double dz = villager.getZ() - (pos.getZ() + 0.5);
        double dy = villager.getY() - pos.getY();
        return dx * dx + dz * dz <= WORK_DISTANCE_SQ && dy >= -3 && dy <= 3;
    }

    private BlockPos standPos(ServerWorld world, BlockPos goal) {
        BlockPos best = goal;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : feet) {
            double distance = pos.getSquaredDistance(goal);
            if (distance < bestDistance && isLoaded(world, pos) && isPassable(world, pos) && isPassable(world, pos.up())
                    && hasFloor(world, pos)) {
                bestDistance = distance;
                best = pos;
            }
        }
        return best;
    }

    private void selectTarget(ServerWorld world, VillagerEntityMCA villager) {
        clearBreaking(world, villager, target);
        target = findTarget(world, villager);
        reach.reset();
        mineTicks = 0;
    }

    @Nullable
    private BlockPos findTarget(ServerWorld world, VillagerEntityMCA villager) {
        reach.prune(world);
        BlockPos from = villager.getBlockPos();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        Iterator<BlockPos> it = ores.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            if (!isOre(world.getBlockState(pos)) || !canDig(world, villager, pos)) {
                it.remove();
                continue;
            }
            double distance = from.getSquaredDistance(pos);
            if (!reach.isBlacklisted(world, pos) && distance < bestDistance) {
                bestDistance = distance;
                best = pos;
            }
        }
        return best != null ? best : nextPlanned(world, villager);
    }

    @Nullable
    private BlockPos nextPlanned(ServerWorld world, VillagerEntityMCA villager) {
        for (Segment segment : plan) {
            boolean blocked = false;
            for (BlockPos pos : segment.blocks()) {
                if (isPassable(world, pos)) {
                    if (feet.contains(pos) && !hasFloor(world, pos)) {
                        blocked = true;
                        break;
                    }
                    continue;
                }
                if (!canDig(world, villager, pos)) {
                    blocked = true;
                    break;
                }
                if (reach.isBlacklisted(world, pos)) {
                    if (segment.vital()) {
                        return null;
                    }
                    blocked = true;
                    break;
                }
                return pos;
            }
            if (blocked && segment.vital()) {
                return null;
            }
        }
        return null;
    }

    private boolean updatePlan(ServerWorld world, VillagerEntityMCA villager) {
        BlockPos cart = depotPos(villager);
        if (cart == null || !isLoaded(world, cart)) {
            return false;
        }
        BlockState state = world.getBlockState(cart);
        if (!state.contains(WorkstationBlock.FACING)) {
            return false;
        }
        Direction direction = state.get(WorkstationBlock.FACING).getOpposite();
        if (cart.equals(planCart) && direction == planDirection) {
            return !plan.isEmpty();
        }
        planCart = cart;
        planDirection = direction;
        plan.clear();
        feet.clear();
        torchSpots.clear();
        ores.clear();
        buildPlan(cart, direction);
        return true;
    }

    private void buildPlan(BlockPos cart, Direction forward) {
        Direction left = forward.rotateYCounterclockwise();
        Direction right = forward.rotateYClockwise();
        int y = cart.getY();
        List<BlockPos> stairs = new ArrayList<>();
        for (int i = 0; i <= DEPTH; i++) {
            BlockPos floor = cart.offset(forward, 1 + i).withY(y - i);
            for (int h = i == 0 ? 1 : 2; h >= 0; h--) {
                stairs.add(floor.up(h));
            }
            feet.add(floor);
            if (i == DEPTH / 2) {
                torchSpots.add(floor);
            }
        }
        plan.add(new Segment(stairs, true));
        int bottom = y - DEPTH;
        List<BlockPos> main = new ArrayList<>();
        for (int j = 0; j < MAX_MAIN; j++) {
            BlockPos floor = cart.offset(forward, DEPTH + 2 + j).withY(bottom);
            main.add(floor.up());
            main.add(floor);
            feet.add(floor);
            if (j % TORCH_EVERY == 0) {
                torchSpots.add(floor);
            }
            if (j % BRANCH_EVERY == 1) {
                plan.add(new Segment(main, true));
                main = new ArrayList<>();
                plan.add(branch(floor, left));
                plan.add(branch(floor, right));
            }
        }
        if (!main.isEmpty()) {
            plan.add(new Segment(main, true));
        }
    }

    private Segment branch(BlockPos start, Direction side) {
        List<BlockPos> blocks = new ArrayList<>();
        for (int k = 1; k <= BRANCH_LENGTH; k++) {
            BlockPos floor = start.offset(side, k);
            blocks.add(floor.up());
            blocks.add(floor);
            feet.add(floor);
            if (k == BRANCH_LENGTH / 2 + 1) {
                torchSpots.add(floor);
            }
        }
        return new Segment(blocks, false);
    }

    private void scanOres(ServerWorld world, VillagerEntityMCA villager) {
        for (Segment segment : plan) {
            for (BlockPos pos : segment.blocks()) {
                if (!isLoaded(world, pos) || !isPassable(world, pos)) {
                    continue;
                }
                for (Direction direction : Direction.values()) {
                    addOre(world, villager, pos.offset(direction));
                }
            }
        }
    }

    private void addOre(ServerWorld world, VillagerEntityMCA villager, BlockPos pos) {
        if (!ores.contains(pos) && isLoaded(world, pos) && isOre(world.getBlockState(pos)) && canDig(world, villager, pos)) {
            ores.add(pos.toImmutable());
        }
    }

    private boolean canDig(ServerWorld world, VillagerEntityMCA villager, BlockPos pos) {
        if (!isLoaded(world, pos) || !inMineZone(villager, pos) || pos.equals(villager.getBlockPos().down())
                || pos.equals(planCart) || pos.getY() <= world.getBottomY() + 1) {
            return false;
        }
        BlockState state = world.getBlockState(pos);
        if (isPassable(world, pos) || state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
            return false;
        }
        float hardness = state.getHardness(world, pos);
        if (hardness < 0 || hardness > MAX_HARDNESS || bestPickaxe(world, villager, state) == null) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            BlockPos side = pos.offset(direction);
            if (!isLoaded(world, side) || !world.getFluidState(side).isEmpty()) {
                return false;
            }
        }
        return mayAlter(world, pos);
    }

    private static boolean inMineZone(VillagerEntityMCA villager, BlockPos pos) {
        WorkManager.Zone zone = WorkManager.getZone(villager);
        BlockPos center = zone.center();
        long dx = pos.getX() - center.getX();
        long dz = pos.getZ() - center.getZ();
        return dx * dx + dz * dz <= (long) zone.radius() * zone.radius();
    }

    private static boolean isPassable(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.getFluidState().isEmpty() && state.getCollisionShape(world, pos).isEmpty();
    }

    private static boolean hasFloor(ServerWorld world, BlockPos pos) {
        BlockPos below = pos.down();
        return world.getFluidState(below).isEmpty() && !world.getBlockState(below).getCollisionShape(world, below).isEmpty();
    }

    private static boolean isOre(BlockState state) {
        for (TagKey<net.minecraft.block.Block> tag : ORES) {
            if (state.isIn(tag)) {
                return true;
            }
        }
        return false;
    }
}
