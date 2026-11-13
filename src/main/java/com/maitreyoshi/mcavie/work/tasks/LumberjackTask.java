package com.maitreyoshi.mcavie.work.tasks;

import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkGesture;
import com.maitreyoshi.mcavie.work.PlacedLogs;
import com.maitreyoshi.mcavie.work.WorkManager;
import com.maitreyoshi.mcavie.work.WorkRegistry;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
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
    private static final int MAX_LEAVES = 400;
    private static final int RETRY_TICKS = 20;
    private static final int SCAN_JITTER = 100;

    private final List<BlockPos> candidates = new ArrayList<>();
    private final ZoneScan scan = new ZoneScan(SCAN_HEIGHT);
    private final Reach<BlockPos> reach = new Reach<>(REACH_TICKS, BLACKLIST_TICKS);
    private final Reach<java.util.UUID> litterBlacklist = new Reach<>(REACH_TICKS, BLACKLIST_TICKS);
    private long nextScan = -1;
    private long nextTry;

    private BlockPos target;
    private java.util.UUID pickup;
    private int chopTicks;
    private int chopTotal;

    public LumberjackTask() {
        super(Job.LUMBERJACK, MAX_CHOP_TICKS + 300);
    }

    @Override
    protected boolean canWork(ServerLevel world, VillagerEntityMCA villager) {
        if (world.getGameTime() < nextTry) {
            return false;
        }
        refreshScan(world, villager);
        if ((bestAxe(world, villager) != null && findTarget(world, villager) != null) || findPickup(world, villager) != null) {
            return true;
        }
        nextTry = world.getGameTime() + RETRY_TICKS;
        return false;
    }

    @Override
    protected boolean keepWorking(ServerLevel world, VillagerEntityMCA villager) {
        return target != null || pickup != null;
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
        pickup = null;
        reach.reset();
        chopTicks = 0;
    }

    // il ne coupe qu'avec une hache rangée dans son billot, qui s'use à chaque bûche
    @Override
    @org.jetbrains.annotations.Nullable
    protected com.maitreyoshi.mcavie.work.WorkStatus idleStatus(ServerLevel world, VillagerEntityMCA villager) {
        return depotPos(villager) != null && bestAxe(world, villager) == null ? com.maitreyoshi.mcavie.work.WorkStatus.NO_TOOL : null;
    }

    @Override
    protected void holdTool(VillagerEntityMCA villager) {
        ItemStack axe = villager.level() instanceof ServerLevel world ? bestAxe(world, villager) : null;
        if (axe == null) {
            super.holdTool(villager);
        } else {
            WorkManager.giveTool(villager, axe.copyWithCount(1));
        }
    }

    @org.jetbrains.annotations.Nullable
    private static ItemStack bestAxe(ServerLevel world, VillagerEntityMCA villager) {
        net.minecraft.world.Container block = depotInventory(world, villager);
        BlockState log = net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState();
        ItemStack best = null;
        for (int i = 0; block != null && i < block.getContainerSize(); i++) {
            ItemStack stack = block.getItem(i);
            if (!stack.isEmpty() && stack.is(ItemTags.AXES)
                    && (best == null || stack.getDestroySpeed(log) > best.getDestroySpeed(log))) {
                best = stack;
            }
        }
        return best;
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
            if (pickup != null) {
                keepPicking(world, villager);
            }
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
        arrive(villager, WorkGesture.CHOP);
        villager.lookAt(target.above());

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

    private static boolean nearTrunk(VillagerEntityMCA villager, BlockPos base) {
        double dx = villager.getX() - (base.getX() + 0.5);
        double dz = villager.getZ() - (base.getZ() + 0.5);
        double dy = villager.getY() - base.getY();
        return dx * dx + dz * dz <= WORK_DISTANCE_SQ && dy >= -2 && dy <= 4;
    }

    private void selectTarget(ServerLevel world, VillagerEntityMCA villager) {
        clearBreaking(world, villager, target);
        target = bestAxe(world, villager) == null ? null : findTarget(world, villager);
        pickup = target == null ? findPickup(world, villager) : null;
        reach.reset();
        chopTicks = 0;
    }

    // pousses et bâtons tombés des feuilles : il les ramasse et replante les pousses là où elles sont tombées
    private static boolean isLitter(ItemStack stack) {
        return stack.is(ItemTags.SAPLINGS) || stack.is(Items.STICK);
    }

    private java.util.UUID findPickup(ServerLevel world, VillagerEntityMCA villager) {
        WorkManager.Zone zone = zone(villager);
        int radius = zone.radius();
        AABB box = new AABB(zone.center()).inflate(radius, radius, radius);
        return world.getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && isLitter(e.getItem())
                        && zone.contains(e.blockPosition()) && !litterBlacklist.isBlacklisted(world, e.getUUID()))
                .stream().min(java.util.Comparator.comparingDouble(villager::distanceToSqr))
                .map(ItemEntity::getUUID).orElse(null);
    }

    private void keepPicking(ServerLevel world, VillagerEntityMCA villager) {
        if (!(world.getEntity(pickup) instanceof ItemEntity item) || !item.isAlive() || !inZone(villager, item.blockPosition())) {
            pickup = findPickup(world, villager);
            reach.reset();
            return;
        }
        if (villager.distanceToSqr(item) > 4.0) {
            walkTo(villager, item.blockPosition());
            if (litterBlacklist.giveUp(world, item.getUUID())) {
                pickup = findPickup(world, villager);
            }
            return;
        }
        litterBlacklist.reset();
        arrive(villager, WorkGesture.NONE);
        BlockPos spot = item.blockPosition();
        ItemStack stack = item.getItem().copy();
        item.discard();
        villager.swing(InteractionHand.MAIN_HAND);
        if (stack.getItem() instanceof BlockItem blockItem && stack.is(ItemTags.SAPLINGS) && canPlantAt(world, spot)) {
            BlockState sapling = blockItem.getBlock().defaultBlockState();
            if (sapling.canSurvive(world, spot)) {
                world.setBlock(spot, sapling, Block.UPDATE_ALL);
                stack.shrink(1);
            }
        }
        if (!stack.isEmpty()) {
            store(villager, stack, false);
        }
        pickup = findPickup(world, villager);
    }

    // laisse de la place entre les arbres : rien de ligneux ni de pousse à moins de 2 blocs
    private static boolean canPlantAt(ServerLevel world, BlockPos pos) {
        if (!world.getBlockState(pos).isAir() || !mayAlter(world, pos) || !isSoil(world.getBlockState(pos.below()))) {
            return false;
        }
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-2, 0, -2), pos.offset(2, 1, 2))) {
            BlockState state = world.getBlockState(near);
            if (isLog(state) || state.is(BlockTags.SAPLINGS)) {
                return false;
            }
        }
        return true;
    }

    private BlockPos findTarget(ServerLevel world, VillagerEntityMCA villager) {
        reach.prune(world);
        WorkManager.Zone zone = zone(villager);

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos from = villager.blockPosition();

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
            WorkManager.Zone zone = zone(villager);
            PlacedLogs.get(world).removeIf(packed -> {
                BlockPos pos = BlockPos.of(packed);
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

    private boolean isTrunkBase(ServerLevel world, BlockPos pos) {
        if (!isLoaded(world, pos)) {
            return false;
        }
        return isNaturalLog(world.getBlockState(pos)) && isSoil(world.getBlockState(pos.below()))
                && !PlacedLogs.get(world).contains(pos);
    }

    public static boolean isLog(BlockState state) {
        if (state.is(BlockTags.LOGS)) {
            return true;
        }
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return state.getBlock() instanceof RotatedPillarBlock && (path.endsWith("_log") || path.endsWith("_stem") || path.startsWith("log_"));
    }

    private static boolean isLeaves(BlockState state) {
        if (state.is(BlockTags.LEAVES) || state.getBlock() instanceof LeavesBlock) {
            return true;
        }
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.endsWith("_leaves") || path.startsWith("leaves_");
    }

    private static boolean isSoil(BlockState state) {
        if (state.is(BlockTags.DIRT)) {
            return true;
        }
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
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
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return !path.startsWith("stripped_") && !path.endsWith("_wood") && !path.endsWith("_hyphae");
    }

    private boolean hasNaturalLeaves(ServerLevel world, BlockPos base) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
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

    private void chop(ServerLevel world, VillagerEntityMCA villager, BlockPos base) {
        Block baseBlock = world.getBlockState(base).getBlock();
        String family = family(baseBlock);
        String namespace = BuiltInRegistries.BLOCK.getKey(baseBlock).getNamespace();

        Set<BlockPos> logs = treeLogs(world, villager, base, family);
        for (BlockPos pos : logs) {
            BlockState state = world.getBlockState(pos);
            ItemStack axe = bestAxe(world, villager);
            if (axe == null) {
                break;
            }
            if (!mayAlter(world, pos) || !world.destroyBlock(pos, false, villager)) {
                continue;
            }
            for (ItemStack drop : blockDrops(world, villager, pos, state, axe)) {
                store(villager, drop);
            }
            if (axe.hurt(1, world.random, null)) {
                axe.shrink(1);
                world.playSound(null, villager.blockPosition(), net.minecraft.sounds.SoundEvents.ITEM_BREAK, net.minecraft.sounds.SoundSource.NEUTRAL, 0.8F, 1.0F);
            }
            net.minecraft.world.Container block = depotInventory(world, villager);
            if (block != null) {
                block.setChanged();
            }
        }
        gainExperience(villager, 1);

        clearLeaves(world, villager, logs);
        collectSaplings(world, villager, base);
        replant(world, villager, base, namespace, family);
    }

    private int chopDuration(ServerLevel world, VillagerEntityMCA villager, BlockPos base) {
        WorkRegistry.Record record = WorkManager.findRecord(villager);
        int level = record == null ? 1 : record.level;
        int perLog = TICKS_PER_LOG[Math.max(0, Math.min(TICKS_PER_LOG.length - 1, level - 1))];
        int size = treeLogs(world, villager, base, family(world.getBlockState(base).getBlock())).size();
        return Math.max(MIN_CHOP_TICKS, Math.min(MAX_CHOP_TICKS, size * perLog));
    }

    private Set<BlockPos> treeLogs(ServerLevel world, VillagerEntityMCA villager, BlockPos base, String family) {
        PlacedLogs placed = PlacedLogs.get(world);
        WorkManager.Zone zone = zone(villager);
        Set<BlockPos> logs = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        climb(world, placed, zone, base, base, family, logs, queue);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos side = base.offset(dx, 0, dz);
                if (!logs.contains(side) && isTreeLog(world, placed, zone, base, side, family)
                        && isSoil(world.getBlockState(side.below()))) {
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
                        BlockPos next = current.offset(dx, dy, dz);
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

    private void climb(ServerLevel world, PlacedLogs placed, WorkManager.Zone zone, BlockPos base, BlockPos start, String family,
                       Set<BlockPos> logs, ArrayDeque<BlockPos> queue) {
        BlockPos pos = start;
        while (logs.size() < MAX_LOGS && !logs.contains(pos) && isTreeLog(world, placed, zone, base, pos, family)) {
            logs.add(pos);
            queue.add(pos);
            pos = pos.above();
        }
    }

    private boolean isTreeLog(ServerLevel world, PlacedLogs placed, WorkManager.Zone zone, BlockPos base, BlockPos pos, String family) {
        if (Math.abs(pos.getX() - base.getX()) > TRUNK_RADIUS || Math.abs(pos.getZ() - base.getZ()) > TRUNK_RADIUS
                || !isLoaded(world, pos) || !zone.contains(pos) || placed.contains(pos)) {
            return false;
        }
        BlockState state = world.getBlockState(pos);
        return isNaturalLog(state) && family(state.getBlock()).equals(family);
    }

    private boolean isBranch(ServerLevel world, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState under = world.getBlockState(below);
        if (!under.isAir() && !isLog(under) && !isLeaves(under) && under.isRedstoneConductor(world, below)) {
            return false;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
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

    // casse aussi le feuillage de l'arbre abattu : pousses, bâtons et pommes vont dans son sac
    private void clearLeaves(ServerLevel world, VillagerEntityMCA villager, Set<BlockPos> logs) {
        Set<BlockPos> leaves = new HashSet<>();
        for (BlockPos log : logs) {
            for (BlockPos near : BlockPos.betweenClosed(log.offset(-LEAF_RADIUS, -1, -LEAF_RADIUS), log.offset(LEAF_RADIUS, LEAF_RADIUS, LEAF_RADIUS))) {
                if (leaves.size() >= MAX_LEAVES) {
                    break;
                }
                if (isLoaded(world, near) && isNaturalLeaf(world.getBlockState(near)) && !nearStandingLog(world, near)) {
                    leaves.add(near.immutable());
                }
            }
        }
        for (BlockPos pos : leaves) {
            BlockState state = world.getBlockState(pos);
            if (!mayAlter(world, pos) || !world.destroyBlock(pos, false, villager)) {
                continue;
            }
            for (ItemStack drop : blockDrops(world, villager, pos, state, ItemStack.EMPTY)) {
                store(villager, drop, false);
            }
        }
    }

    // ne touche pas aux feuilles d'un arbre voisin encore debout
    private static boolean nearStandingLog(ServerLevel world, BlockPos leaf) {
        for (BlockPos near : BlockPos.betweenClosed(leaf.offset(-2, -2, -2), leaf.offset(2, 2, 2))) {
            if (isLoaded(world, near) && isLog(world.getBlockState(near))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNaturalLeaf(BlockState state) {
        return isLeaves(state) && !(state.hasProperty(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.PERSISTENT));
    }

    private String family(Block block) {
        String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
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

    private void collectSaplings(ServerLevel world, VillagerEntityMCA villager, BlockPos base) {
        AABB box = new AABB(base).inflate(SAPLING_RADIUS);
        for (ItemEntity item : world.getEntitiesOfClass(ItemEntity.class, box, e -> isLitter(e.getItem()))) {
            store(villager, item.getItem().copy(), false);
            item.discard();
        }
    }

    private void replant(ServerLevel world, VillagerEntityMCA villager, BlockPos base, String namespace, String family) {
        String name = family.equals("mangrove") ? "mangrove_propagule" : family + "_sapling";
        Item saplingItem = BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath(namespace, name));
        if (saplingItem == Items.AIR) {
            saplingItem = BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("minecraft", name));
        }
        if (!(saplingItem instanceof BlockItem blockItem) || !world.getBlockState(base).isAir() || !mayAlter(world, base)) {
            return;
        }
        BlockState sapling = blockItem.getBlock().defaultBlockState();
        if (!sapling.canSurvive(world, base)) {
            return;
        }

        SimpleContainer inventory = villager.getInventory();
        ItemStack owned = null;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(saplingItem)) {
                owned = stack;
                break;
            }
        }
        if (owned == null) {
            return;
        }
        world.setBlock(base, sapling, Block.UPDATE_ALL);
        owned.shrink(1);
        villager.swing(InteractionHand.MAIN_HAND);
    }
}
