package com.maitreyoshi.mcavie.work.tasks;

import com.google.common.collect.ImmutableMap;
import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkManager;
import com.maitreyoshi.mcavie.work.WorkNetwork;
import com.maitreyoshi.mcavie.work.WorkRegistry;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.entity.ItemEntity;
import com.maitreyoshi.mcavie.work.WorkStatus;
import com.maitreyoshi.mcavie.work.WorkGesture;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.MultiTickTask;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class AbstractJobTask extends MultiTickTask<VillagerEntityMCA> {
    protected static final int MAX_RUN_TICKS = 400;

    private final Job job;

    protected AbstractJobTask(Job job) {
        this(job, MAX_RUN_TICKS);
    }

    protected AbstractJobTask(Job job, int maxRunTicks) {
        // pas d'exigence sur WALK_TARGET / LOOK_TARGET : un reste de balade ou de regard bloquait le démarrage
        super(ImmutableMap.of(), maxRunTicks);
        this.job = job;
    }

    @Override
    protected boolean shouldRun(ServerWorld world, VillagerEntityMCA villager) {
        if (!WorkManager.isWorking(villager) || WorkManager.getJob(villager) != job || DepositTask.shouldDeposit(world, villager)) {
            return false;
        }
        if (canWork(world, villager)) {
            return true;
        }
        WorkStatus status;
        if (isSearching()) {
            status = WorkStatus.SEARCHING;
        } else if (altersBlocks() && !world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) {
            status = WorkStatus.NO_GRIEFING;
        } else {
            status = WorkStatus.NOTHING_TO_DO;
        }
        WorkManager.setStatus(villager, status);
        return false;
    }

    @Override
    protected void finishRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        WorkManager.setGesture(villager, WorkGesture.NONE);
        villager.getBrain().forget(MemoryModuleType.WALK_TARGET);
        villager.getBrain().forget(MemoryModuleType.LOOK_TARGET);
    }

    // vrai tant que le métier parcourt encore sa zone (le scan est étalé sur plusieurs ticks)
    protected boolean isSearching() {
        return false;
    }

    protected boolean altersBlocks() {
        return false;
    }

    // ne relance pas le pathfinding à chaque tick si la destination n'a pas changé
    static void walkTo(VillagerEntityMCA villager, BlockPos pos) {
        WorkManager.setStatus(villager, WorkStatus.TRAVELING);
        WorkManager.setGesture(villager, WorkGesture.NONE);
        Optional<WalkTarget> current = villager.getBrain().getOptionalMemory(MemoryModuleType.WALK_TARGET);
        if (current.isEmpty() || !current.get().getLookTarget().getBlockPos().equals(pos)) {
            villager.moveTowards(pos);
        }
    }

    static void arrive(VillagerEntityMCA villager, WorkStatus status) {
        WorkManager.setStatus(villager, status);
        villager.getBrain().forget(MemoryModuleType.WALK_TARGET);
        villager.getNavigation().stop();
    }

    // arrivé au travail : il s'arrête et joue le geste de son métier
    protected static void arrive(VillagerEntityMCA villager, WorkGesture gesture) {
        arrive(villager, WorkStatus.WORKING);
        WorkManager.setGesture(villager, gesture);
    }

    // coup d'outil visible : bras, fissures sur le bloc, son et éclats du bloc
    protected static void hitBlock(ServerWorld world, VillagerEntityMCA villager, BlockPos pos, int ticks, int total) {
        if (ticks % 5 != 1) {
            return;
        }
        villager.swingHand(Hand.MAIN_HAND);
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        world.setBlockBreakingInfo(villager.getId(), pos, Math.min(9, ticks * 10 / Math.max(1, total)));
        BlockSoundGroup sounds = state.getSoundGroup();
        world.playSound(null, pos, sounds.getHitSound(), SoundCategory.NEUTRAL, (sounds.getVolume() + 1.0F) / 4.0F, sounds.getPitch() * 0.8F);
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
                pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
    }

    protected static void clearBreaking(ServerWorld world, VillagerEntityMCA villager, @Nullable BlockPos pos) {
        if (pos != null) {
            world.setBlockBreakingInfo(villager.getId(), pos, -1);
        }
    }

    @Override
    protected boolean shouldKeepRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        return WorkManager.isWorking(villager) && WorkManager.getJob(villager) == job
                && !DepositTask.isBlocked(world, villager) && keepWorking(world, villager);
    }

    protected abstract boolean canWork(ServerWorld world, VillagerEntityMCA villager);

    protected abstract boolean keepWorking(ServerWorld world, VillagerEntityMCA villager);

    protected Job job() {
        return job;
    }

    protected BlockPos center(VillagerEntityMCA villager) {
        return WorkManager.getCenter(villager);
    }

    protected int radius(VillagerEntityMCA villager) {
        return zone(villager).radius();
    }

    protected WorkManager.Zone zone(VillagerEntityMCA villager) {
        return WorkManager.getZone(villager);
    }

    protected boolean inZone(VillagerEntityMCA villager, BlockPos pos) {
        return WorkManager.isInZone(villager, pos);
    }

    protected void store(VillagerEntityMCA villager, ItemStack stack) {
        store(villager, stack, true);
    }

    protected void store(VillagerEntityMCA villager, ItemStack stack, boolean bonus) {
        WorkRegistry.Record record = WorkManager.findRecord(villager);
        int level = record == null ? 1 : record.level;
        if (bonus && !stack.isEmpty() && stack.getCount() < stack.getMaxCount() && villager.getRandom().nextFloat() < (level - 1) * 0.1f) {
            stack.increment(1);
        }
        ItemStack rest = villager.getInventory().addStack(stack);
        if (!rest.isEmpty()) {
            villager.getWorld().spawnEntity(new ItemEntity(villager.getWorld(), villager.getX(), villager.getY() + 0.5, villager.getZ(), rest));
        }
    }

    protected void holdTool(VillagerEntityMCA villager) {
        WorkManager.giveTool(villager, job);
    }

    protected void gainExperience(VillagerEntityMCA villager, int amount) {
        WorkManager.addExperience(villager, amount);
    }

    protected List<ItemStack> blockDrops(ServerWorld world, VillagerEntityMCA villager, BlockPos pos, BlockState state) {
        LootContextParameterSet.Builder builder = new LootContextParameterSet.Builder(world)
                .add(LootContextParameters.ORIGIN, Vec3d.ofCenter(pos))
                .add(LootContextParameters.TOOL, job.tool())
                .add(LootContextParameters.THIS_ENTITY, villager)
                .add(LootContextParameters.BLOCK_STATE, state)
                .luck(0);
        return world.getServer().getLootManager()
                .getLootTable(state.getBlock().getLootTableId())
                .generateLoot(builder.build(LootContextTypes.BLOCK));
    }

    @Nullable
    protected static BlockPos depotPos(VillagerEntityMCA villager) {
        WorkRegistry.Record record = WorkManager.findRecord(villager);
        return record == null ? null : record.depot;
    }

    @Nullable
    static Inventory depotInventory(ServerWorld world, VillagerEntityMCA villager) {
        WorkRegistry.Record record = WorkManager.findRecord(villager);
        BlockPos depot = record == null ? null : record.depot;
        if (depot == null || !isLoaded(world, depot)
                || (record.depotDimension != null && !record.depotDimension.equals(world.getRegistryKey().getValue()))) {
            return null;
        }
        BlockState state = world.getBlockState(depot);
        if (state.getBlock() instanceof ChestBlock chestBlock) {
            return ChestBlock.getInventory(chestBlock, state, world, depot, true);
        }
        if (world.getBlockEntity(depot) instanceof Inventory inventory) {
            return inventory;
        }
        record.depot = null;
        record.depotDimension = null;
        WorkManager.markDirty(villager);
        WorkNetwork.sendJobInfo(villager);
        return null;
    }

    protected static final class Reach<K> {
        private final int maxTicks;
        private final long blacklistTicks;
        private final Map<K, Long> blacklist;
        private int ticks;

        Reach(int maxTicks, long blacklistTicks) {
            this(maxTicks, blacklistTicks, new HashMap<>());
        }

        Reach(int maxTicks, long blacklistTicks, Map<K, Long> blacklist) {
            this.maxTicks = maxTicks;
            this.blacklistTicks = blacklistTicks;
            this.blacklist = blacklist;
        }

        void reset() {
            ticks = 0;
        }

        boolean isBlacklisted(ServerWorld world, K key) {
            Long end = blacklist.get(key);
            if (end == null) {
                return false;
            }
            if (end <= world.getTime()) {
                blacklist.remove(key);
                return false;
            }
            return true;
        }

        boolean giveUp(ServerWorld world, K key) {
            if (++ticks <= maxTicks) {
                return false;
            }
            blacklist.put(key, world.getTime() + blacklistTicks);
            ticks = 0;
            return true;
        }

        void prune(ServerWorld world) {
            long now = world.getTime();
            blacklist.values().removeIf(end -> end <= now);
        }
    }

    protected static final class ZoneScan {
        private static final int COLUMNS_PER_STEP = 7;

        private final int height;
        private final List<BlockPos> found = new java.util.ArrayList<>();
        @Nullable
        private WorkManager.Zone zone;
        private int nextDx;

        ZoneScan(int height) {
            this.height = height;
        }

        boolean running() {
            return zone != null;
        }

        void start(WorkManager.Zone zone) {
            this.zone = zone;
            nextDx = -zone.radius();
            found.clear();
        }

        boolean step(ServerWorld world, java.util.function.Predicate<BlockPos> accept) {
            if (zone == null) {
                return false;
            }
            BlockPos center = zone.center();
            int radius = zone.radius();
            long radiusSq = (long) radius * radius;
            int last = Math.min(radius, nextDx + COLUMNS_PER_STEP - 1);
            BlockPos.Mutable cursor = new BlockPos.Mutable();
            for (int dx = nextDx; dx <= last; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    long horizontal = (long) dx * dx + (long) dz * dz;
                    int x = center.getX() + dx;
                    int z = center.getZ() + dz;
                    if (horizontal > radiusSq || !world.isChunkLoaded(ChunkSectionPos.getSectionCoord(x), ChunkSectionPos.getSectionCoord(z))) {
                        continue;
                    }
                    for (int dy = -height; dy <= height; dy++) {
                        if (horizontal + (long) dy * dy > radiusSq) {
                            continue;
                        }
                        cursor.set(x, center.getY() + dy, z);
                        if (accept.test(cursor)) {
                            found.add(cursor.toImmutable());
                        }
                    }
                }
            }
            nextDx = last + 1;
            if (nextDx > radius) {
                zone = null;
                return true;
            }
            return false;
        }

        List<BlockPos> result() {
            return found;
        }
    }

    protected static boolean mayAlter(ServerWorld world, BlockPos pos) {
        if (!world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) {
            return false;
        }
        MinecraftServer server = world.getServer();
        if (!server.isDedicated() || world.getRegistryKey() != World.OVERWORLD
                || server.getPlayerManager().getOpList().isEmpty() || server.getSpawnProtectionRadius() <= 0) {
            return true;
        }
        BlockPos spawn = world.getSpawnPos();
        int distance = Math.max(Math.abs(pos.getX() - spawn.getX()), Math.abs(pos.getZ() - spawn.getZ()));
        return distance > server.getSpawnProtectionRadius();
    }

    protected static boolean isLoaded(net.minecraft.world.World world, BlockPos pos) {
        return world.isChunkLoaded(ChunkSectionPos.getSectionCoord(pos.getX()), ChunkSectionPos.getSectionCoord(pos.getZ()));
    }
}
