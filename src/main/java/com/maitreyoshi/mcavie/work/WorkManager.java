package com.maitreyoshi.mcavie.work;

import com.maitreyoshi.mcavie.village.VillageDecrees;
import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.life.Wellbeing;
import com.maitreyoshi.mcavie.work.tasks.DepositTask;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.ActivityMCA;
import net.mca.entity.ai.Chore;
import net.mca.entity.ai.MoveState;
import net.mca.entity.ai.relationship.AgeState;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.Brain;
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
    private static UUID cachedUuid;
    private static long cachedTime;
    private static WorkRegistry.Record cachedRecord;

    private WorkManager() {
    }

    public static boolean isWorkTime(World world) {
        WorldCalendar.CalendarDate date = WorldCalendar.now(world);
        return date.dayOfWeek() != DayOfWeek.SUNDAY && date.hour() >= START_HOUR && date.hour() < END_HOUR;
    }

    public static boolean isWorking(VillagerEntityMCA villager) {
        return WORKING.getOrDefault(villager.getUuid(), false);
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

        Job job = WorkRegistry.get(world.getServer()).getJob(villager.getUuid());
        boolean shouldWork = job.isCraft()
                && (AgeState.byCurrentAge(villager.getBreedingAge()) == AgeState.ADULT || age >= APPRENTICE_AGE)
                && isWorkTime(world)
                && !com.maitreyoshi.mcavie.village.VillageMarket.isMarketDayFor(villager, world)
                && !com.maitreyoshi.mcavie.village.VillageJustice.isImprisoned(villager)
                && !villager.isSleeping()
                && !Wellbeing.isSick(world, villager.getUuid())
                && villager.getVillagerBrain().getCurrentJob() == Chore.NONE
                && villager.getVillagerBrain().getMoveState() == MoveState.MOVE
                && !villager.getVillagerBrain().isPanicking()
                && !DepositTask.isBlocked(world, villager);

        boolean wasWorking = isWorking(villager);
        if (shouldWork == wasWorking) {
            if (!shouldWork && isMarkedTool(villager.getMainHandStack())) {
                putToolAway(villager);
            }
            return;
        }
        WORKING.put(villager.getUuid(), shouldWork);
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
        ItemStack tool = job.tool();
        if (tool.isEmpty() || hand.isOf(tool.getItem())) {
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

    private static ItemStack originalOf(ItemStack tool) {
        NbtCompound mark = tool.getNbt() == null ? null : tool.getNbt().getCompound(TOOL_KEY);
        return mark == null || !mark.contains("held") ? ItemStack.EMPTY : ItemStack.fromNbt(mark.getCompound("held"));
    }

    private static int ageInYears(VillagerEntityMCA villager, ServerWorld world) {
        Long birth = LifeRegistry.get(world.getServer()).getBirthDay(villager.getUuid());
        return birth == null ? -1 : LifeCycle.ageInYears(birth, WorldCalendar.now(world).dayIndex());
    }

    private static void updateCareer(VillagerEntityMCA villager, ServerWorld world, int age) {
        if (age < 0) {
            return;
        }
        WorkRegistry registry = WorkRegistry.get(world.getServer());
        WorkRegistry.Record record = registry.get(villager.getUuid());
        Job job = record == null ? Job.NONE : record.job;

        if (age >= com.maitreyoshi.mcavie.aging.AgingConfig.get().retirementAge) {
            if (job != Job.RETIRED) {
                String profession = villager.getProfessionName();
                String former = job != Job.NONE ? "job.mca_vie." + job.id()
                        : profession.equals("mca.none") ? null : "entity.minecraft.villager." + profession;
                if (villager.isGuard()) {
                    villager.setProfession(VillagerProfession.NONE);
                }
                record = registry.getOrCreate(villager.getUuid());
                record.job = Job.RETIRED;
                record.formerJob = former;
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
        net.mca.server.world.data.FamilyTreeNode node = villager.getRelationships().getFamilyEntry();
        java.util.List<Job> parentJobs = new java.util.ArrayList<>();
        for (UUID parent : new UUID[]{node.father(), node.mother()}) {
            Job parentJob = registry.getJob(parent);
            if (parentJob.isCraft()) {
                parentJobs.add(parentJob);
            }
        }
        net.minecraft.util.math.random.Random random = world.getRandom();
        if (!parentJobs.isEmpty() && random.nextFloat() < 0.6f) {
            return parentJobs.get(random.nextInt(parentJobs.size()));
        }
        Job[] crafts = java.util.Arrays.stream(Job.values()).filter(Job::isCraft).toArray(Job[]::new);
        return crafts[random.nextInt(crafts.length)];
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

    public static void setJob(VillagerEntityMCA villager, Job job) {
        WorkRegistry.Record record = getRecord(villager);
        if (record.job != job) {
            record.level = 1;
            record.experience = 0;
        }
        record.job = job;
        record.center = villager.getBlockPos();
        dropDepotOutsideZone(record);
        record.apprenticeChosen = true;
        markDirty(villager);
        if (job == Job.GUARD) {
            villager.setProfession(ProfessionsMCA.GUARD.get());
        } else if (job == Job.ARCHER) {
            villager.setProfession(ProfessionsMCA.ARCHER.get());
        } else if (villager.isGuard()) {
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
        DepositTask.forget(uuid);
    }

    public static void register() {
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof VillagerEntityMCA) {
                forget(entity.getUuid());
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            WORKING.clear();
            cachedRecord = null;
            cachedUuid = null;
            DepositTask.clear();
        });
    }
}
