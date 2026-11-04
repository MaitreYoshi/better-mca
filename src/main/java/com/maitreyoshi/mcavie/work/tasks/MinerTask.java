package com.maitreyoshi.mcavie.work.tasks;

import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkGesture;
import com.maitreyoshi.mcavie.work.WorkManager;
import com.maitreyoshi.mcavie.work.WorkRegistry;
import com.maitreyoshi.mcavie.work.block.WorkstationBlock;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
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

// mine en galeries derrière le chariot (côté opposé à sa face avant) :
// - un escalier descend de DEPTH blocs, puis une galerie principale de 2 de haut part tout droit,
//   avec une galerie latérale de chaque côté tous les 3 blocs ;
// - un minerai visible depuis les galeries passe avant tout, et il suit le filon ;
// - il s'arrête devant l'eau, la lave, le vide, les blocs durs ou à inventaire, et ne sort pas de sa zone ;
// - torches prises dans le chariot, butin déposé dans le chariot.
// Le plan se déduit du chariot et l'avancement se lit dans le monde : rien à sauvegarder, il reprend où il en était.
public class MinerTask extends AbstractJobTask {
    private static final int DEPTH = 12;
    private static final int MAX_MAIN = 32;
    private static final int BRANCH_EVERY = 3;
    private static final int BRANCH_LENGTH = 8;
    private static final int TORCH_EVERY = 8;
    private static final float MAX_HARDNESS = 10F;
    private static final double WORK_DISTANCE_SQ = 12.25;
    // durée d'un bloc de pierre selon le niveau (apprenti → maître), multipliée par la dureté
    private static final int[] TICKS_PER_BLOCK = {50, 42, 34, 26, 18};
    private static final int REACH_TICKS = 240;
    private static final long BLACKLIST_TICKS = 2400;
    private static final int RETRY_TICKS = 100;
    private static final long ORE_SCAN_INTERVAL = 200;
    private static final List<TagKey<net.minecraft.block.Block>> ORES = List.of(BlockTags.COAL_ORES, BlockTags.IRON_ORES, BlockTags.COPPER_ORES,
            BlockTags.GOLD_ORES, BlockTags.REDSTONE_ORES, BlockTags.LAPIS_ORES, BlockTags.DIAMOND_ORES, BlockTags.EMERALD_ORES);

    // un tronçon du plan : s'il est bloqué, le reste du tronçon est abandonné ; un tronçon vital bloqué arrête la mine
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
            walkTo(villager, target);
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
        // filon : les minerais touchant le bloc creusé passent en tête
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
        for (ItemStack drop : blockDrops(world, villager, pos, state)) {
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

    // torche au sol, à intervalle régulier, si l'endroit est sombre et que le chariot en contient
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
        float hardness = Math.max(0.5F, world.getBlockState(pos).getHardness(world, pos));
        return Math.max(20, Math.round(perBlock * hardness / 1.5F));
    }

    private static boolean inReach(VillagerEntityMCA villager, BlockPos pos) {
        double dx = villager.getX() - (pos.getX() + 0.5);
        double dz = villager.getZ() - (pos.getZ() + 0.5);
        double dy = villager.getY() - pos.getY();
        return dx * dx + dz * dz <= WORK_DISTANCE_SQ && dy >= -3 && dy <= 3;
    }

    private void selectTarget(ServerWorld world, VillagerEntityMCA villager) {
        clearBreaking(world, villager, target);
        target = findTarget(world, villager);
        reach.reset();
        mineTicks = 0;
    }

    // un filon en vue d'abord (le plus proche), sinon le prochain bloc du plan
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
                    // inaccessible pour l'instant : il réessaiera plus tard
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

    // plan recalculé seulement si le chariot ou son orientation changent
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
            // 3 de haut dans l'escalier pour ne pas se cogner en descendant
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

    // minerais visibles depuis les galeries déjà creusées
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

    // bloc qu'il a le droit et la possibilité de creuser sans danger
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
        if (hardness < 0 || hardness > MAX_HARDNESS) {
            return false;
        }
        // ni eau ni lave autour : la creuser ouvrirait une brèche
        for (Direction direction : Direction.values()) {
            BlockPos side = pos.offset(direction);
            if (!isLoaded(world, side) || !world.getFluidState(side).isEmpty()) {
                return false;
            }
        }
        return mayAlter(world, pos);
    }

    // la zone de travail compte à l'horizontale : la mine peut descendre sous le chariot
    private static boolean inMineZone(VillagerEntityMCA villager, BlockPos pos) {
        WorkManager.Zone zone = WorkManager.getZone(villager);
        BlockPos center = zone.center();
        long dx = pos.getX() - center.getX();
        long dz = pos.getZ() - center.getZ();
        return dx * dx + dz * dz <= (long) zone.radius() * zone.radius();
    }

    // de l'air ou une plante, une torche… : rien à creuser
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
