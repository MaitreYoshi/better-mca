package com.maitreyoshi.mcavie.work.tasks;

import com.google.common.collect.ImmutableMap;
import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkManager;
import com.maitreyoshi.mcavie.work.WorkNetwork;
import com.maitreyoshi.mcavie.work.WorkRegistry;
import net.mca.entity.VillagerEntityMCA;
import com.maitreyoshi.mcavie.work.WorkStatus;
import com.maitreyoshi.mcavie.work.WorkGesture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class AbstractJobTask extends Behavior<VillagerEntityMCA> {
    protected static final int MAX_RUN_TICKS = 400;

    private final Job job;

    protected AbstractJobTask(Job job) {
        this(job, MAX_RUN_TICKS);
    }

    protected AbstractJobTask(Job job, int maxRunTicks) {
        super(ImmutableMap.of(), maxRunTicks);
        this.job = job;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA villager) {
        if (!WorkManager.isWorking(villager) || WorkManager.getJob(villager) != job || DepositTask.shouldDeposit(world, villager)) {
            return false;
        }
        if (canWork(world, villager)) {
            return true;
        }
        WorkStatus status = idleStatus(world, villager);
        if (status != null) {
        } else if (isSearching()) {
            status = WorkStatus.SEARCHING;
        } else if (altersBlocks() && !world.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            status = WorkStatus.NO_GRIEFING;
        } else {
            status = WorkStatus.NOTHING_TO_DO;
        }
        WorkManager.setStatus(villager, status);
        return false;
    }

    @Override
    protected void stop(ServerLevel world, VillagerEntityMCA villager, long time) {
        WorkManager.setGesture(villager, WorkGesture.NONE);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    @Nullable
    protected WorkStatus idleStatus(ServerLevel world, VillagerEntityMCA villager) {
        return null;
    }

    protected boolean isSearching() {
        return false;
    }

    protected boolean altersBlocks() {
        return false;
    }

    static void walkTo(VillagerEntityMCA villager, BlockPos pos) {
        WorkManager.setStatus(villager, WorkStatus.TRAVELING);
        WorkManager.setGesture(villager, WorkGesture.NONE);
        Optional<WalkTarget> current = villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET);
        if (current.isEmpty() || !current.get().getTarget().currentBlockPosition().equals(pos)) {
            villager.moveTowards(pos);
        }
    }

    static void arrive(VillagerEntityMCA villager, WorkStatus status) {
        WorkManager.setStatus(villager, status);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getNavigation().stop();
    }

    protected static void arrive(VillagerEntityMCA villager, WorkGesture gesture) {
        arrive(villager, WorkStatus.WORKING);
        WorkManager.setGesture(villager, gesture);
    }

    protected static void hitBlock(ServerLevel world, VillagerEntityMCA villager, BlockPos pos, int ticks, int total) {
        if (ticks % 5 != 1) {
            return;
        }
        villager.swing(InteractionHand.MAIN_HAND);
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        world.destroyBlockProgress(villager.getId(), pos, Math.min(9, ticks * 10 / Math.max(1, total)));
        SoundType sounds = state.getSoundType();
        world.playSound(null, pos, sounds.getHitSound(), SoundSource.NEUTRAL, (sounds.getVolume() + 1.0F) / 4.0F, sounds.getPitch() * 0.8F);
        world.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
    }

    protected static void clearBreaking(ServerLevel world, VillagerEntityMCA villager, @Nullable BlockPos pos) {
        if (pos != null) {
            world.destroyBlockProgress(villager.getId(), pos, -1);
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel world, VillagerEntityMCA villager, long time) {
        return WorkManager.isWorking(villager) && WorkManager.getJob(villager) == job
                && !DepositTask.isBlocked(world, villager) && keepWorking(world, villager);
    }

    protected abstract boolean canWork(ServerLevel world, VillagerEntityMCA villager);

    protected abstract boolean keepWorking(ServerLevel world, VillagerEntityMCA villager);

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
        if (bonus) {
            addBonus(villager, stack);
        }
        ItemStack rest = villager.getInventory().addItem(stack);
        if (!rest.isEmpty()) {
            villager.level().addFreshEntity(new ItemEntity(villager.level(), villager.getX(), villager.getY() + 0.5, villager.getZ(), rest));
        }
    }

    protected static void addBonus(VillagerEntityMCA villager, ItemStack stack) {
        WorkRegistry.Record record = WorkManager.findRecord(villager);
        int level = record == null ? 1 : record.level;
        if (!stack.isEmpty() && stack.getCount() < stack.getMaxStackSize() && villager.getRandom().nextFloat() < (level - 1) * 0.1f) {
            stack.grow(1);
        }
    }

    protected void holdTool(VillagerEntityMCA villager) {
        WorkManager.giveTool(villager, job);
    }

    protected void gainExperience(VillagerEntityMCA villager, int amount) {
        WorkManager.addExperience(villager, amount);
    }

    protected List<ItemStack> blockDrops(ServerLevel world, VillagerEntityMCA villager, BlockPos pos, BlockState state) {
        return blockDrops(world, villager, pos, state, job.tool());
    }

    protected List<ItemStack> blockDrops(ServerLevel world, VillagerEntityMCA villager, BlockPos pos, BlockState state, ItemStack tool) {
        LootParams.Builder builder = new LootParams.Builder(world)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.TOOL, tool)
                .withParameter(LootContextParams.THIS_ENTITY, villager)
                .withParameter(LootContextParams.BLOCK_STATE, state)
                .withLuck(0);
        return world.getServer().getLootData()
                .getLootTable(state.getBlock().getLootTable())
                .getRandomItems(builder.create(LootContextParamSets.BLOCK));
    }

    @Nullable
    protected static BlockPos depotPos(VillagerEntityMCA villager) {
        WorkRegistry.Record record = WorkManager.findRecord(villager);
        return record == null ? null : record.depot;
    }

    @Nullable
    static Container depotInventory(ServerLevel world, VillagerEntityMCA villager) {
        WorkRegistry.Record record = WorkManager.findRecord(villager);
        BlockPos depot = record == null ? null : record.depot;
        if (depot == null || !isLoaded(world, depot)
                || (record.depotDimension != null && !record.depotDimension.equals(world.dimension().location()))) {
            return null;
        }
        BlockState state = world.getBlockState(depot);
        if (state.getBlock() instanceof ChestBlock chestBlock) {
            return ChestBlock.getContainer(chestBlock, state, world, depot, true);
        }
        if (world.getBlockEntity(depot) instanceof Container inventory) {
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

        boolean isBlacklisted(ServerLevel world, K key) {
            Long end = blacklist.get(key);
            if (end == null) {
                return false;
            }
            if (end <= world.getGameTime()) {
                blacklist.remove(key);
                return false;
            }
            return true;
        }

        boolean giveUp(ServerLevel world, K key) {
            if (++ticks <= maxTicks) {
                return false;
            }
            blacklist.put(key, world.getGameTime() + blacklistTicks);
            ticks = 0;
            return true;
        }

        void prune(ServerLevel world) {
            long now = world.getGameTime();
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

        boolean step(ServerLevel world, java.util.function.Predicate<BlockPos> accept) {
            if (zone == null) {
                return false;
            }
            BlockPos center = zone.center();
            int radius = zone.radius();
            long radiusSq = (long) radius * radius;
            int last = Math.min(radius, nextDx + COLUMNS_PER_STEP - 1);
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int dx = nextDx; dx <= last; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    long horizontal = (long) dx * dx + (long) dz * dz;
                    int x = center.getX() + dx;
                    int z = center.getZ() + dz;
                    if (horizontal > radiusSq || !world.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
                        continue;
                    }
                    for (int dy = -height; dy <= height; dy++) {
                        if (horizontal + (long) dy * dy > radiusSq) {
                            continue;
                        }
                        cursor.set(x, center.getY() + dy, z);
                        if (accept.test(cursor)) {
                            found.add(cursor.immutable());
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

    protected static boolean mayAlter(ServerLevel world, BlockPos pos) {
        if (!world.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return false;
        }
        MinecraftServer server = world.getServer();
        if (!server.isDedicatedServer() || world.dimension() != Level.OVERWORLD
                || server.getPlayerList().getOps().isEmpty() || server.getSpawnProtectionRadius() <= 0) {
            return true;
        }
        BlockPos spawn = world.getSharedSpawnPos();
        int distance = Math.max(Math.abs(pos.getX() - spawn.getX()), Math.abs(pos.getZ() - spawn.getZ()));
        return distance > server.getSpawnProtectionRadius();
    }

    protected static boolean isLoaded(net.minecraft.world.level.Level world, BlockPos pos) {
        return world.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }
}
