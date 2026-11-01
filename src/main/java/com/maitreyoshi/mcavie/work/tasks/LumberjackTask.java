package com.maitreyoshi.mcavie.work.tasks;

import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.PlacedLogs;
import com.maitreyoshi.mcavie.work.WorkManager;
import com.maitreyoshi.mcavie.work.WorkRegistry;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.item.Items;
import net.minecraft.block.PillarBlock;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class LumberjackTask extends AbstractJobTask {
    private static final long SCAN_INTERVAL = 1200;
    private static final int SCAN_HEIGHT = 8;
    private static final double WORK_DISTANCE_SQ = 12.25;
    // durée de coupe par bûche selon le niveau (apprenti → maître), bornée pour les très grands arbres
    private static final int[] TICKS_PER_LOG = {60, 50, 40, 30, 20};
    private static final int MIN_CHOP_TICKS = 60;
    private static final int MAX_CHOP_TICKS = 1100;
    private static final int REACH_TICKS = 240;
    private static final long BLACKLIST_TICKS = 2400;
    private static final int MAX_LOGS = 192;
    private static final int TRUNK_RADIUS = 6;
    private static final int LEAF_RADIUS = 3;
    private static final int LEAF_HEIGHT = 16;
    private static final String[] SOIL_WORDS = {"dirt", "grass", "soil", "podzol", "mycelium", "moss_block", "mud", "loam", "silt"};
    private static final int BRANCH_LEAF_RADIUS = 3;
    private static final int SAPLING_RADIUS = 8;
    private static final int RETRY_TICKS = 20;
    private static final int SCAN_JITTER = 100;

    private final List<BlockPos> candidates = new ArrayList<>();
    private final ZoneScan scan = new ZoneScan(SCAN_HEIGHT);
    private final Reach<BlockPos> reach = new Reach<>(REACH_TICKS, BLACKLIST_TICKS);
    private long nextScan = -1;
    private long nextTry;

    private BlockPos target;
    private int chopTicks;
    private int chopTotal;

    public LumberjackTask() {
        super(Job.LUMBERJACK, MAX_CHOP_TICKS + 300);
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
        reach.reset();
        chopTicks = 0;
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
        if (!isTrunkBase(world, target) || !inZone(villager, target)) {
            selectTarget(world, villager);
            return;
        }
        holdTool(villager);

        if (!nearTrunk(villager, target)) {
            walkTo(villager, target);
            if (reach.giveUp(world, target)) {
                selectTarget(world, villager);
            }
            return;
        }
        arrive(villager);
        villager.lookAt(target.up());

        if (chopTicks == 0) {
            chopTotal = chopDuration(world, villager, target);
        }
        chopTicks++;
        hitBlock(world, villager, target, chopTicks, chopTotal);
        if (chopTicks < chopTotal) {
            return;
        }

        BlockPos base = target;
        clearBreaking(world, villager, base);
        chop(world, villager, base);
        candidates.remove(base);
        selectTarget(world, villager);
    }

    // à portée de hache du tronc, même si des feuilles ou une pente l'empêchent de le toucher
    private static boolean nearTrunk(VillagerEntityMCA villager, BlockPos base) {
        double dx = villager.getX() - (base.getX() + 0.5);
        double dz = villager.getZ() - (base.getZ() + 0.5);
        double dy = villager.getY() - base.getY();
        return dx * dx + dz * dz <= WORK_DISTANCE_SQ && dy >= -2 && dy <= 4;
    }

    private void selectTarget(ServerWorld world, VillagerEntityMCA villager) {
        clearBreaking(world, villager, target);
        target = findTarget(world, villager);
        reach.reset();
        chopTicks = 0;
    }

    private BlockPos findTarget(ServerWorld world, VillagerEntityMCA villager) {
        reach.prune(world);
        WorkManager.Zone zone = zone(villager);

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos from = villager.getBlockPos();

        Iterator<BlockPos> it = candidates.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            if (!isTrunkBase(world, pos)) {
                it.remove();
                continue;
            }
            if (reach.isBlacklisted(world, pos) || !zone.contains(pos) || !mayAlter(world, pos)) {
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
            WorkManager.Zone zone = zone(villager);
            PlacedLogs.get(world).removeIf(packed -> {
                BlockPos pos = BlockPos.fromLong(packed);
                return zone.contains(pos) && isLoaded(world, pos) && !isLog(world.getBlockState(pos));
            });
            scan.start(zone);
        }
        if (scan.step(world, pos -> isTrunkBase(world, pos) && hasNaturalLeaves(world, pos))) {
            candidates.clear();
            candidates.addAll(scan.result());
            nextScan = now + SCAN_INTERVAL;
        }
    }

    private boolean isTrunkBase(ServerWorld world, BlockPos pos) {
        if (!isLoaded(world, pos)) {
            return false;
        }
        return isNaturalLog(world.getBlockState(pos)) && isSoil(world.getBlockState(pos.down()))
                && !PlacedLogs.get(world).contains(pos);
    }

    // arbres des autres mods : tous ne remplissent pas les tags vanilla, on se rabat sur la classe ou le nom du bloc
    public static boolean isLog(BlockState state) {
        if (state.isIn(BlockTags.LOGS)) {
            return true;
        }
        String path = Registries.BLOCK.getId(state.getBlock()).getPath();
        return state.getBlock() instanceof PillarBlock && (path.endsWith("_log") || path.endsWith("_stem") || path.startsWith("log_"));
    }

    private static boolean isLeaves(BlockState state) {
        if (state.isIn(BlockTags.LEAVES) || state.getBlock() instanceof LeavesBlock) {
            return true;
        }
        String path = Registries.BLOCK.getId(state.getBlock()).getPath();
        return path.endsWith("_leaves") || path.startsWith("leaves_");
    }

    private static boolean isSoil(BlockState state) {
        if (state.isIn(BlockTags.DIRT)) {
            return true;
        }
        String path = Registries.BLOCK.getId(state.getBlock()).getPath();
        for (String soil : SOIL_WORDS) {
            if (path.contains(soil)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNaturalLog(BlockState state) {
        if (!isLog(state)) {
            return false;
        }
        String path = Registries.BLOCK.getId(state.getBlock()).getPath();
        return !path.startsWith("stripped_") && !path.endsWith("_wood") && !path.endsWith("_hyphae");
    }

    private boolean hasNaturalLeaves(ServerWorld world, BlockPos base) {
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int dy = 2; dy <= LEAF_HEIGHT; dy++) {
            for (int dx = -LEAF_RADIUS; dx <= LEAF_RADIUS; dx++) {
                for (int dz = -LEAF_RADIUS; dz <= LEAF_RADIUS; dz++) {
                    cursor.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                    if (!isLoaded(world, cursor)) {
                        continue;
                    }
                    if (isNaturalLeaf(world.getBlockState(cursor))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void chop(ServerWorld world, VillagerEntityMCA villager, BlockPos base) {
        Block baseBlock = world.getBlockState(base).getBlock();
        String family = family(baseBlock);
        String namespace = Registries.BLOCK.getId(baseBlock).getNamespace();

        Set<BlockPos> logs = treeLogs(world, villager, base, family);
        for (BlockPos pos : logs) {
            BlockState state = world.getBlockState(pos);
            if (!mayAlter(world, pos) || !world.breakBlock(pos, false, villager)) {
                continue;
            }
            for (ItemStack drop : blockDrops(world, villager, pos, state)) {
                store(villager, drop);
            }
        }
        gainExperience(villager, 1);

        collectSaplings(world, villager, base);
        replant(world, villager, base, namespace, family);
    }

    private int chopDuration(ServerWorld world, VillagerEntityMCA villager, BlockPos base) {
        WorkRegistry.Record record = WorkManager.findRecord(villager);
        int level = record == null ? 1 : record.level;
        int perLog = TICKS_PER_LOG[Math.max(0, Math.min(TICKS_PER_LOG.length - 1, level - 1))];
        int size = treeLogs(world, villager, base, family(world.getBlockState(base).getBlock())).size();
        return Math.max(MIN_CHOP_TICKS, Math.min(MAX_CHOP_TICKS, size * perLog));
    }

    private Set<BlockPos> treeLogs(ServerWorld world, VillagerEntityMCA villager, BlockPos base, String family) {
        PlacedLogs placed = PlacedLogs.get(world);
        WorkManager.Zone zone = zone(villager);
        Set<BlockPos> logs = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        climb(world, placed, zone, base, base, family, logs, queue);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos side = base.add(dx, 0, dz);
                if (!logs.contains(side) && isTreeLog(world, placed, zone, base, side, family)
                        && isSoil(world.getBlockState(side.down()))) {
                    climb(world, placed, zone, base, side, family, logs, queue);
                }
            }
        }
        while (!queue.isEmpty() && logs.size() < MAX_LOGS) {
            BlockPos current = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos next = current.add(dx, dy, dz);
                        if (logs.size() >= MAX_LOGS || logs.contains(next) || next.getY() <= base.getY()
                                || !isTreeLog(world, placed, zone, base, next, family) || !isBranch(world, next)) {
                            continue;
                        }
                        climb(world, placed, zone, base, next, family, logs, queue);
                    }
                }
            }
        }
        return logs;
    }

    private void climb(ServerWorld world, PlacedLogs placed, WorkManager.Zone zone, BlockPos base, BlockPos start, String family,
                       Set<BlockPos> logs, ArrayDeque<BlockPos> queue) {
        BlockPos pos = start;
        while (logs.size() < MAX_LOGS && !logs.contains(pos) && isTreeLog(world, placed, zone, base, pos, family)) {
            logs.add(pos);
            queue.add(pos);
            pos = pos.up();
        }
    }

    private boolean isTreeLog(ServerWorld world, PlacedLogs placed, WorkManager.Zone zone, BlockPos base, BlockPos pos, String family) {
        if (Math.abs(pos.getX() - base.getX()) > TRUNK_RADIUS || Math.abs(pos.getZ() - base.getZ()) > TRUNK_RADIUS
                || !isLoaded(world, pos) || !zone.contains(pos) || placed.contains(pos)) {
            return false;
        }
        BlockState state = world.getBlockState(pos);
        return isNaturalLog(state) && family(state.getBlock()).equals(family);
    }

    private boolean isBranch(ServerWorld world, BlockPos pos) {
        BlockPos below = pos.down();
        BlockState under = world.getBlockState(below);
        if (!under.isAir() && !isLog(under) && !isLeaves(under) && under.isSolidBlock(world, below)) {
            return false;
        }
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int dx = -BRANCH_LEAF_RADIUS; dx <= BRANCH_LEAF_RADIUS; dx++) {
            for (int dy = -BRANCH_LEAF_RADIUS; dy <= BRANCH_LEAF_RADIUS; dy++) {
                for (int dz = -BRANCH_LEAF_RADIUS; dz <= BRANCH_LEAF_RADIUS; dz++) {
                    cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (isLoaded(world, cursor) && isNaturalLeaf(world.getBlockState(cursor))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isNaturalLeaf(BlockState state) {
        return isLeaves(state) && !(state.contains(LeavesBlock.PERSISTENT) && state.get(LeavesBlock.PERSISTENT));
    }

    private String family(Block block) {
        String path = Registries.BLOCK.getId(block).getPath();
        if (path.startsWith("stripped_")) {
            path = path.substring("stripped_".length());
        }
        if (path.endsWith("_log")) {
            path = path.substring(0, path.length() - "_log".length());
        } else if (path.endsWith("_wood")) {
            path = path.substring(0, path.length() - "_wood".length());
        } else if (path.endsWith("_stem")) {
            path = path.substring(0, path.length() - "_stem".length());
        } else if (path.startsWith("log_")) {
            path = path.substring("log_".length());
        }
        return path;
    }

    private void collectSaplings(ServerWorld world, VillagerEntityMCA villager, BlockPos base) {
        Box box = new Box(base).expand(SAPLING_RADIUS);
        for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, box, e -> e.getStack().isIn(ItemTags.SAPLINGS))) {
            store(villager, item.getStack().copy(), false);
            item.discard();
        }
    }

    private void replant(ServerWorld world, VillagerEntityMCA villager, BlockPos base, String namespace, String family) {
        String name = family.equals("mangrove") ? "mangrove_propagule" : family + "_sapling";
        Item saplingItem = Registries.ITEM.get(new Identifier(namespace, name));
        if (saplingItem == Items.AIR) {
            saplingItem = Registries.ITEM.get(new Identifier("minecraft", name));
        }
        if (!(saplingItem instanceof BlockItem blockItem) || !world.getBlockState(base).isAir() || !mayAlter(world, base)) {
            return;
        }
        BlockState sapling = blockItem.getBlock().getDefaultState();
        if (!sapling.canPlaceAt(world, base)) {
            return;
        }

        SimpleInventory inventory = villager.getInventory();
        ItemStack owned = null;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && stack.isOf(saplingItem)) {
                owned = stack;
                break;
            }
        }
        if (owned == null) {
            return;
        }
        world.setBlockState(base, sapling, Block.NOTIFY_ALL);
        owned.decrement(1);
        villager.swingHand(Hand.MAIN_HAND);
    }
}
