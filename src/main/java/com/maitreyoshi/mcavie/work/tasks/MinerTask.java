package com.maitreyoshi.mcavie.work.tasks;

import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkGesture;
import com.maitreyoshi.mcavie.work.WorkManager;
import com.maitreyoshi.mcavie.work.WorkRegistry;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.block.BlockState;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// carrière autour du chariot :
// - minerais à découvert d'abord, sous le chariot ou jusqu'à ORE_ABOVE blocs au-dessus ;
// - sinon la pierre à découvert, uniquement sous le niveau du chariot (le village au-dessus n'est pas touché) ;
// - jamais le bloc sous ses pieds ;
// - le butin va directement dans le chariot (son inventaire seulement si le chariot est plein ou absent).
public class MinerTask extends AbstractJobTask {
    private static final long SCAN_INTERVAL = 1200;
    private static final int SCAN_HEIGHT = 12;
    private static final int MAX_DEPTH = 8;
    private static final int ORE_ABOVE = 3;
    private static final double WORK_DISTANCE_SQ = 12.25;
    // durée d'un bloc de pierre selon le niveau (apprenti → maître), multipliée par la dureté
    private static final int[] TICKS_PER_BLOCK = {50, 42, 34, 26, 18};
    private static final int REACH_TICKS = 240;
    private static final long BLACKLIST_TICKS = 2400;
    private static final int RETRY_TICKS = 20;
    private static final int SCAN_JITTER = 100;
    private static final Direction[] EXPOSED_STONE = {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
    private static final List<TagKey<net.minecraft.block.Block>> ORES = List.of(BlockTags.COAL_ORES, BlockTags.IRON_ORES, BlockTags.COPPER_ORES,
            BlockTags.GOLD_ORES, BlockTags.REDSTONE_ORES, BlockTags.LAPIS_ORES, BlockTags.DIAMOND_ORES, BlockTags.EMERALD_ORES);

    private final ZoneScan scan = new ZoneScan(SCAN_HEIGHT);
    private final List<BlockPos> candidates = new ArrayList<>();
    private final Reach<BlockPos> reach = new Reach<>(REACH_TICKS, BLACKLIST_TICKS);
    private long nextScan = -1;
    private long nextTry;
    @Nullable
    private BlockPos target;
    private int mineTicks;
    private int mineTotal;
    // niveau de référence : celui du chariot, ou le centre de la zone sans chariot
    private int baseY;

    public MinerTask() {
        super(Job.MINER);
    }

    @Override
    protected boolean canWork(ServerWorld world, VillagerEntityMCA villager) {
        if (world.getTime() < nextTry) {
            return false;
        }
        baseY = baseLevel(villager);
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
    protected boolean isSearching() {
        return scan.running();
    }

    @Override
    protected boolean altersBlocks() {
        return true;
    }

    @Override
    protected void run(ServerWorld world, VillagerEntityMCA villager, long time) {
        baseY = baseLevel(villager);
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
        if (!isMineable(world, villager, target)) {
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
        candidates.remove(done);
        // les blocs dégagés autour deviennent la suite de la carrière
        for (Direction direction : Direction.values()) {
            BlockPos next = done.offset(direction);
            if (!candidates.contains(next) && isMineable(world, villager, next)) {
                candidates.add(next);
            }
        }
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

    // le minerai le plus proche s'il y en a un d'accessible, sinon la pierre la plus proche
    @Nullable
    private BlockPos findTarget(ServerWorld world, VillagerEntityMCA villager) {
        reach.prune(world);
        BlockPos from = villager.getBlockPos();
        BlockPos bestOre = null;
        BlockPos bestStone = null;
        double oreDistance = Double.MAX_VALUE;
        double stoneDistance = Double.MAX_VALUE;
        Iterator<BlockPos> it = candidates.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            if (!isMineable(world, villager, pos)) {
                it.remove();
                continue;
            }
            if (reach.isBlacklisted(world, pos)) {
                continue;
            }
            double distance = from.getSquaredDistance(pos);
            if (isOre(world.getBlockState(pos))) {
                if (distance < oreDistance) {
                    oreDistance = distance;
                    bestOre = pos;
                }
            } else if (distance < stoneDistance) {
                stoneDistance = distance;
                bestStone = pos;
            }
        }
        return bestOre != null ? bestOre : bestStone;
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
        if (scan.step(world, pos -> isMineable(world, villager, pos))) {
            candidates.clear();
            candidates.addAll(scan.result());
            nextScan = now + SCAN_INTERVAL;
        }
    }

    private static int baseLevel(VillagerEntityMCA villager) {
        BlockPos cart = depotPos(villager);
        if (cart != null && WorkManager.isInZone(villager, cart)) {
            return cart.getY();
        }
        return WorkManager.getCenter(villager).getY();
    }

    private boolean isMineable(ServerWorld world, VillagerEntityMCA villager, BlockPos pos) {
        if (pos.getY() < baseY - MAX_DEPTH || pos.getY() > baseY + ORE_ABOVE || !isLoaded(world, pos)
                || !inZone(villager, pos) || pos.equals(villager.getBlockPos().down())) {
            return false;
        }
        BlockState state = world.getBlockState(pos);
        boolean ore = isOre(state);
        if (!ore && (pos.getY() >= baseY || !state.isIn(BlockTags.BASE_STONE_OVERWORLD))) {
            return false;
        }
        // à découvert, sinon il creuserait dans le vide ; un minerai peut aussi être visible par-dessous (plafond de grotte)
        for (Direction direction : ore ? Direction.values() : EXPOSED_STONE) {
            BlockPos side = pos.offset(direction);
            if (isLoaded(world, side) && world.getBlockState(side).isAir()) {
                return mayAlter(world, pos);
            }
        }
        return false;
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
