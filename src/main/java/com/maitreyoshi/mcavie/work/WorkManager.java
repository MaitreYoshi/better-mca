package com.maitreyoshi.mcavie.work;

import com.maitreyoshi.mcavie.aging.AgingConfig;
import com.maitreyoshi.mcavie.phone.PhoneEmployees;
import com.maitreyoshi.mcavie.village.VillageDecrees;
import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.life.Wellbeing;
import com.maitreyoshi.mcavie.village.VillageJustice;
import com.maitreyoshi.mcavie.village.VillageMarket;
import com.maitreyoshi.mcavie.work.block.WorkBlocks;
import com.maitreyoshi.mcavie.work.block.WorkstationBlock;
import com.maitreyoshi.mcavie.work.block.WorkstationBlockEntity;
import com.maitreyoshi.mcavie.work.tasks.DepositTask;
import com.maitreyoshi.mcavie.compat.ServerEntityEvents;
import com.maitreyoshi.mcavie.compat.ServerLifecycleEvents;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.ActivityMCA;
import net.mca.entity.ai.Chore;
import net.mca.entity.ai.MoveState;
import net.mca.entity.ai.relationship.AgeState;
import net.mca.server.world.data.FamilyTreeNode;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import com.maitreyoshi.mcavie.life.LifeCycle;
import com.maitreyoshi.mcavie.life.LifeRegistry;
import net.mca.ProfessionsMCA;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.World;

import java.time.DayOfWeek;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WorkManager {
    public static final int START_HOUR = 8;
    public static final int END_HOUR = 18;
    public static final int APPRENTICE_AGE = 14;
    private static final int ADULT_AGE = 18;
    private static final int[] LEVEL_THRESHOLDS = {0, 50, 150, 350, 700};
    private static final String TOOL_KEY = "mca_vie_tool";

    private static final Map<UUID, Boolean> WORKING = new ConcurrentHashMap<>();
    private static final Map<UUID, WorkStatus> STATUS = new ConcurrentHashMap<>();
    private static final Map<UUID, WorkGesture> GESTURE = new ConcurrentHashMap<>();
    private static UUID cachedUuid;
    private static long cachedTime;
    private static WorkRegistry.Record cachedRecord;

    private WorkManager() {
    }

    public static boolean isWorkTime(World world) {
        WorldCalendar.CalendarDate date = WorldCalendar.now(world);
        return WorldCalendar.rhythmDay(date) != DayOfWeek.SUNDAY && date.hour() >= START_HOUR && date.hour() < END_HOUR;
    }

    public static boolean isWorking(VillagerEntityMCA villager) {
        return WORKING.getOrDefault(villager.getUuid(), false);
    }

    public static WorkStatus getStatus(VillagerEntityMCA villager) {
        return STATUS.getOrDefault(villager.getUuid(), WorkStatus.SEARCHING);
    }

    public static void setStatus(VillagerEntityMCA villager, WorkStatus status) {
        if (STATUS.put(villager.getUuid(), status) != status) {
            WorkNetwork.sendJobInfo(villager);
        }
    }

    public static WorkGesture getGesture(VillagerEntityMCA villager) {
        return GESTURE.getOrDefault(villager.getUuid(), WorkGesture.NONE);
    }

    public static void setGesture(VillagerEntityMCA villager, WorkGesture gesture) {
        if (GESTURE.getOrDefault(villager.getUuid(), WorkGesture.NONE) != gesture) {
            GESTURE.put(villager.getUuid(), gesture);
            WorkNetwork.sendJobInfo(villager);
        }
    }

    public static Job getJob(VillagerEntityMCA villager) {
        if (villager.getWorld() instanceof ServerWorld world) {
            return WorkRegistry.get(world.getServer()).getJob(villager.getUuid());
        }
        return Job.NONE;
    }

    public static WorkRegistry.Record getRecord(VillagerEntityMCA villager) {
        return WorkRegistry.get(((ServerWorld) villager.getWorld()).getServer()).getOrCreate(villager.getUuid());
    }

    @Nullable
    public static WorkRegistry.Record findRecord(VillagerEntityMCA villager) {
        if (villager.getWorld() instanceof ServerWorld world) {
            long time = world.getTime();
            if (cachedRecord != null && time == cachedTime && villager.getUuid().equals(cachedUuid)) {
                return cachedRecord;
            }
            WorkRegistry.Record record = WorkRegistry.get(world.getServer()).get(villager.getUuid());
            if (record != null) {
                cachedUuid = villager.getUuid();
                cachedTime = time;
                cachedRecord = record;
            }
            return record;
        }
        return null;
    }

    public static void markDirty(VillagerEntityMCA villager) {
        if (villager.getWorld() instanceof ServerWorld world) {
            WorkRegistry.get(world.getServer()).markDirty();
        }
    }

    public record Zone(BlockPos center, int radius) {
        public boolean contains(BlockPos pos) {
            return center.getSquaredDistance(pos) <= (double) radius * radius;
        }
    }

    public static void dropDepotOutsideZone(WorkRegistry.Record record) {
        if (record.depot != null && record.center != null && !new Zone(record.center, record.radius).contains(record.depot)) {
            record.depot = null;
            record.depotDimension = null;
        }
    }

    public static Zone getZone(VillagerEntityMCA villager) {
        WorkRegistry.Record record = findRecord(villager);
        BlockPos center = record != null && record.center != null ? record.center : villager.getBlockPos();
        return new Zone(center, record != null ? record.radius : WorkRegistry.DEFAULT_RADIUS);
    }

    public static BlockPos getCenter(VillagerEntityMCA villager) {
        return getZone(villager).center();
    }

    public static boolean isInZone(VillagerEntityMCA villager, BlockPos pos) {
        return getZone(villager).contains(pos);
    }

    public static void update(VillagerEntityMCA villager) {
        if (!(villager.getWorld() instanceof ServerWorld world)) {
            return;
        }
        int age = ageInYears(villager, world);
        updateCareer(villager, world, age);

        WorkRegistry.Record employee = findRecord(villager);
        if (employee != null) {
            PhoneEmployees.update(villager, world, employee);
        }
        Job job = WorkRegistry.get(world.getServer()).getJob(villager.getUuid());
        syncProfession(villager, job);
        WorkRegistry.Record work = findRecord(villager);
        boolean hasWorkstation = work != null && work.depot != null;
        if (job.isCraft() && !hasWorkstation && (world.getTime() / 20 + villager.getId()) % 5 == 0) {
            hasWorkstation = linkNearestWorkstation(villager, world, getRecord(villager));
        }
        boolean shouldWork = job.isCraft()
                && hasWorkstation
                && (AgeState.byCurrentAge(villager.getBreedingAge()) == AgeState.ADULT || age >= APPRENTICE_AGE)
                && isWorkTime(world)
                && !VillageMarket.isMarketDayFor(villager, world)
                && !VillageJustice.isImprisoned(villager)
                && !villager.isSleeping()
                && !Wellbeing.isSick(world, villager.getUuid())
                && villager.getVillagerBrain().getCurrentJob() == Chore.NONE
                && villager.getVillagerBrain().getMoveState() == MoveState.MOVE
                && !villager.getVillagerBrain().isPanicking()
                && !DepositTask.isBlocked(world, villager);

        if (!shouldWork && job.isCraft()) {
            setStatus(villager, !hasWorkstation ? WorkStatus.NO_WORKSTATION
                    : DepositTask.isBlocked(world, villager) ? WorkStatus.INVENTORY_FULL : WorkStatus.SEARCHING);
        }
        boolean wasWorking = isWorking(villager);
        if (shouldWork == wasWorking) {
            if (!shouldWork && isMarkedTool(villager.getMainHandStack())) {
                putToolAway(villager);
            }
            return;
        }
        WORKING.put(villager.getUuid(), shouldWork);
        if (shouldWork) {
            STATUS.put(villager.getUuid(), WorkStatus.SEARCHING);
        }
        WorkNetwork.sendJobInfo(villager);

        if (shouldWork) {
            villager.getBrain().doExclusively(ActivityMCA.CHORE.get());
            giveTool(villager, job);
        } else {
            villager.getBrain().doExclusively(Activity.IDLE);
            putToolAway(villager);
        }
    }

    public static void giveTool(VillagerEntityMCA villager, Job job) {
        ItemStack hand = villager.getMainHandStack();
        if (job == Job.MINER && isMarkedTool(hand) && hand.isIn(ItemTags.PICKAXES)) {
            return;
        }
        giveTool(villager, job.tool());
    }

    public static void giveTool(VillagerEntityMCA villager, ItemStack tool) {
        ItemStack hand = villager.getMainHandStack();
        if (tool.isEmpty() || (hand.isOf(tool.getItem())
                && EnchantmentHelper.get(hand).equals(EnchantmentHelper.get(tool)))) {
            return;
        }
        ItemStack original = ItemStack.EMPTY;
        if (isMarkedTool(hand)) {
            original = originalOf(hand);
        } else if (!hand.isEmpty()) {
            ItemStack rest = villager.getInventory().addStack(hand.copy());
            if (!rest.isEmpty()) {
                villager.setStackInHand(Hand.MAIN_HAND, rest);
                return;
            }
            original = hand.copy();
        }
        NbtCompound mark = new NbtCompound();
        if (!original.isEmpty()) {
            mark.put("held", original.writeNbt(new NbtCompound()));
        }
        tool.getOrCreateNbt().put(TOOL_KEY, mark);
        villager.setStackInHand(Hand.MAIN_HAND, tool);
    }

    public static void putToolAway(VillagerEntityMCA villager) {
        SimpleInventory inventory = villager.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            if (isMarkedTool(inventory.getStack(i))) {
                inventory.setStack(i, ItemStack.EMPTY);
            }
        }
        ItemStack hand = villager.getMainHandStack();
        if (!isMarkedTool(hand)) {
            return;
        }
        ItemStack original = originalOf(hand);
        ItemStack back = ItemStack.EMPTY;
        for (int i = 0; i < inventory.size() && !original.isEmpty(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (ItemStack.canCombine(stack, original)) {
                back = stack.split(original.getCount());
                break;
            }
        }
        villager.setStackInHand(Hand.MAIN_HAND, back);
    }

    private static boolean isMarkedTool(ItemStack stack) {
        return !stack.isEmpty() && stack.hasNbt() && stack.getNbt().contains(TOOL_KEY);
    }

    public static boolean isWorkTool(ItemStack stack) {
        return isMarkedTool(stack);
    }

    public static ItemStack heldOriginal(VillagerEntityMCA villager) {
        ItemStack hand = villager.getMainHandStack();
        return isMarkedTool(hand) ? originalOf(hand) : ItemStack.EMPTY;
    }

    private static ItemStack originalOf(ItemStack tool) {
        NbtCompound mark = tool.getNbt() == null ? null : tool.getNbt().getCompound(TOOL_KEY);
        return mark == null || !mark.contains("held") ? ItemStack.EMPTY : ItemStack.fromNbt(mark.getCompound("held"));
    }

    private static int ageInYears(VillagerEntityMCA villager, ServerWorld world) {
        Long birth = LifeRegistry.get(world.getServer()).getBodyBirthDay(villager.getUuid());
        return birth == null ? -1 : LifeCycle.ageInYears(birth, WorldCalendar.now(world).dayIndex());
    }

    // clé de traduction du refus d'un métier proposé par un joueur, ou null s'il l'accepte
    public static String jobRefusal(VillagerEntityMCA villager) {
        if (!(villager.getWorld() instanceof ServerWorld world)) {
            return null;
        }
        int age = ageInYears(villager, world);
        if (age >= AgingConfig.get().retirementAge) {
            return "job.mca_vie.refused_old";
        }
        boolean adult = AgeState.byCurrentAge(villager.getBreedingAge()) == AgeState.ADULT;
        if (age >= 0 ? age < APPRENTICE_AGE : !adult) {
            return "job.mca_vie.refused_young";
        }
        return null;
    }

    private static void updateCareer(VillagerEntityMCA villager, ServerWorld world, int age) {
        if (age < 0) {
            return;
        }
        WorkRegistry registry = WorkRegistry.get(world.getServer());
        WorkRegistry.Record record = registry.get(villager.getUuid());
        Job job = record == null ? Job.NONE : record.job;

        if (age >= AgingConfig.get().retirementAge) {
            if (job != Job.RETIRED) {
                String profession = villager.getProfessionName();
                String former = job != Job.NONE ? "job.mca_vie." + job.id()
                        : profession.equals("mca.none") ? null : "entity.minecraft.villager." + profession;
                if (villager.isGuard() || isSyncedProfession(villager, job)) {
                    villager.setProfession(VillagerProfession.NONE);
                }
                record = registry.getOrCreate(villager.getUuid());
                record.job = Job.RETIRED;
                record.formerJob = former;
                record.employer = null;
                registry.markDirty();
                WorkNetwork.sendJobInfo(villager);
            }
            return;
        }

        if (age >= APPRENTICE_AGE && age < ADULT_AGE && job == Job.NONE && (record == null || !record.apprenticeChosen)) {
            record = registry.getOrCreate(villager.getUuid());
            record.apprenticeChosen = true;
            record.job = chooseApprenticeship(villager, world);
            record.level = 1;
            record.experience = 0;
            record.center = villager.getBlockPos();
            registry.markDirty();
            WorkNetwork.sendJobInfo(villager);
        }
    }

    private static Job chooseApprenticeship(VillagerEntityMCA villager, ServerWorld world) {
        WorkRegistry registry = WorkRegistry.get(world.getServer());
        FamilyTreeNode node = villager.getRelationships().getFamilyEntry();
        List<Job> parentJobs = new ArrayList<>();
        for (UUID parent : new UUID[]{node.father(), node.mother()}) {
            Job parentJob = registry.getJob(parent);
            if (parentJob.isCraft()) {
                parentJobs.add(parentJob);
            }
        }
        Random random = world.getRandom();
        if (!parentJobs.isEmpty() && random.nextFloat() < 0.6f) {
            return parentJobs.get(random.nextInt(parentJobs.size()));
        }
        Job[] crafts = Arrays.stream(Job.values()).filter(Job::isCraft).toArray(Job[]::new);
        return crafts[random.nextInt(crafts.length)];
    }

    public static int nextLevelExperience(int level) {
        return level >= 1 && level < LEVEL_THRESHOLDS.length ? LEVEL_THRESHOLDS[level] : -1;
    }

    public static int levelExperience(int level) {
        return LEVEL_THRESHOLDS[Math.max(0, Math.min(LEVEL_THRESHOLDS.length - 1, level - 1))];
    }

    public static int levelFor(int experience) {
        int level = 1;
        for (int i = 1; i < LEVEL_THRESHOLDS.length; i++) {
            if (experience >= LEVEL_THRESHOLDS[i]) {
                level = i + 1;
            }
        }
        return level;
    }

    public static void keepWorking(VillagerEntityMCA villager) {
        if (isWorking(villager) && !isInDanger(villager)) {
            villager.getBrain().getFirstPossibleNonCoreActivity().ifPresent(activity -> {
                if (!activity.equals(ActivityMCA.CHORE.get())) {
                    villager.getBrain().doExclusively(ActivityMCA.CHORE.get());
                }
            });
        }
    }

    private static boolean isInDanger(VillagerEntityMCA villager) {
        Brain<?> brain = villager.getBrain();
        return brain.hasActivity(Activity.PANIC) || brain.hasActivity(Activity.HIDE)
                || brain.hasActivity(Activity.RAID) || brain.hasActivity(Activity.PRE_RAID);
    }

    @Nullable
    private static VillagerProfession professionFor(Job job) {
        return JobProfessions.of(job);
    }

    private static boolean isSyncedProfession(VillagerEntityMCA villager, Job job) {
        VillagerProfession profession = professionFor(job);
        return profession != null && villager.getProfession() == profession;
    }

    private static void syncProfession(VillagerEntityMCA villager, Job job) {
        VillagerProfession profession = professionFor(job);
        if (profession == null || AgeState.byCurrentAge(villager.getBreedingAge()) != AgeState.ADULT) {
            return;
        }
        if (villager.getProfession() != profession) {
            villager.setProfession(profession);
            villager.setVillagerData(villager.getVillagerData().withLevel(1));
            villager.setExperience(0);
        }
        if (villager.getExperience() == 0) {
            villager.setExperience(1);
        }
        WorkRegistry.Record tradeRecord = findRecord(villager);
        if (tradeRecord != null) {
            JobProfessions.syncTradeLevel(villager, job, tradeRecord.level);
        }
    }

    public static final int WORKSTATION_SEARCH_RADIUS = 32;

    public static boolean linkNearestWorkstation(VillagerEntityMCA villager, ServerWorld world, WorkRegistry.Record record) {
        WorkstationBlock wanted = WorkBlocks.forJob(record.job);
        if (wanted == null) {
            return false;
        }
        WorkRegistry registry = WorkRegistry.get(world.getServer());
        Identifier dimension = world.getRegistryKey().getValue();
        BlockPos origin = villager.getBlockPos();
        int chunkRadius = (WORKSTATION_SEARCH_RADIUS >> 4) + 1;
        int cx = origin.getX() >> 4;
        int cz = origin.getZ() >> 4;
        BlockPos best = null;
        double bestDistance = (double) WORKSTATION_SEARCH_RADIUS * WORKSTATION_SEARCH_RADIUS;
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                if (!world.isChunkLoaded(cx + dx, cz + dz)) {
                    continue;
                }
                for (BlockEntity entity : world.getChunk(cx + dx, cz + dz).getBlockEntities().values()) {
                    BlockPos pos = entity.getPos();
                    double distance = pos.getSquaredDistance(origin);
                    if (entity instanceof WorkstationBlockEntity && distance <= bestDistance
                            && entity.getCachedState().isOf(wanted) && !registry.isClaimed(dimension, pos, villager.getUuid())) {
                        bestDistance = distance;
                        best = pos;
                    }
                }
            }
        }
        if (best == null) {
            return false;
        }
        record.depot = best.toImmutable();
        record.depotDimension = dimension;
        record.center = record.depot;
        registry.markDirty();
        WorkNetwork.sendJobInfo(villager);
        return true;
    }

    public static void setJob(VillagerEntityMCA villager, Job job) {
        WorkRegistry.Record record = getRecord(villager);
        Job previous = record.job;
        if (record.job != job) {
            record.level = 1;
            record.experience = 0;
        }
        record.job = job;
        if (previous != job) {
            record.depot = null;
            record.depotDimension = null;
            record.center = villager.getBlockPos();
        }
        record.apprenticeChosen = true;
        if (job.isCraft() && record.depot == null && villager.getWorld() instanceof ServerWorld world) {
            linkNearestWorkstation(villager, world, record);
        }
        markDirty(villager);
        if (job == Job.GUARD) {
            villager.setProfession(ProfessionsMCA.GUARD.get());
        } else if (job == Job.ARCHER) {
            villager.setProfession(ProfessionsMCA.ARCHER.get());
        } else if (professionFor(job) != null) {
            syncProfession(villager, job);
        } else if (villager.isGuard() || isSyncedProfession(villager, previous)) {
            villager.setProfession(VillagerProfession.NONE);
        }
        if (isWorking(villager)) {
            WORKING.put(villager.getUuid(), false);
            villager.getBrain().doExclusively(Activity.IDLE);
            putToolAway(villager);
        }
        WorkNetwork.sendJobInfo(villager);
    }

    public static void addExperience(VillagerEntityMCA villager, int amount) {
        WorkRegistry.Record record = getRecord(villager);
        record.experience += VillageDecrees.apprenticeXp(villager, amount);
        markDirty(villager);
        int level = levelFor(record.experience);
        if (level > record.level) {
            record.level = level;
            if (villager.getWorld() instanceof ServerWorld world) {
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getEyeY() + 0.3, villager.getZ(), 12, 0.4, 0.4, 0.4, 0.05);
                world.playSound(null, villager.getBlockPos(), SoundEvents.ENTITY_VILLAGER_CELEBRATE, SoundCategory.NEUTRAL, 0.8f, 1.0f);
            }
            WorkNetwork.sendJobInfo(villager);
        }
    }

    public static void forget(UUID uuid) {
        WORKING.remove(uuid);
        STATUS.remove(uuid);
        GESTURE.remove(uuid);
        DepositTask.forget(uuid);
        RepairErrands.forget(uuid);
    }

    public static void register() {
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof VillagerEntityMCA) {
                forget(entity.getUuid());
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            WORKING.clear();
            STATUS.clear();
            GESTURE.clear();
            cachedRecord = null;
            cachedUuid = null;
            DepositTask.clear();
        });
    }
}
