package com.maitreyoshi.mcavie.life;

import com.maitreyoshi.mcavie.aging.Aging;
import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.village.VillageDecrees;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.relationship.AgeState;
import net.mca.entity.ai.relationship.Gender;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import com.maitreyoshi.mcavie.work.WorkManager;
import com.maitreyoshi.mcavie.work.WorkRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;

import java.time.Period;
import java.util.UUID;

public final class LifeCycle {
    public static final double DAYS_PER_YEAR = WorldCalendar.DAYS_PER_YEAR;
    public static final int MENOPAUSE_AGE = 50;

    private static final double[] STAGE_START_YEARS = {0, 2, 5, 13, 18};
    private static final int[][] INITIAL_AGE_RANGES = {
            {0, 1},
            {2, 4},
            {5, 12},
            {13, 17},
            {18, 75}
    };
    private static final int GROWTH_TOLERANCE = 40;
    private static final int STAGE_MARGIN = 100;

    private LifeCycle() {
    }

    public static void update(VillagerEntityMCA villager) {
        if (!(villager.getWorld() instanceof ServerWorld world)) {
            return;
        }
        syncTreeGender(villager, world);
        if (History.applyPendingDeath(villager, world)) {
            return;
        }
        LifeRegistry registry = LifeRegistry.get(world.getServer());
        long today = WorldCalendar.now(world).dayIndex();

        if (registry.isZombie(villager.getUuid())) {
            registry.cured(villager.getUuid(), today);
            Wellbeing.resume(world, villager.getUuid(), today);
        }
        Long birth = registry.getBirthDay(villager.getUuid());
        if (birth == null) {
            boolean newborn = isNewborn(villager, world);
            birth = newborn ? today : initialBirthDay(villager, world, today);
            if (newborn) {
                registry.recordBirth(villager.getUuid(), birth, WorldCalendar.exactCalendarDay(world));
            } else {
                registry.setBirthDay(villager.getUuid(), birth);
            }
            if (newborn) {
                VillageDecrees.onBirth(villager);
                if (villager.getBreedingAge() <= -net.mca.entity.ai.relationship.AgeState.getMaxAge() + 2400) {
                    com.maitreyoshi.mcavie.village.CivilRegistry.birth(villager, world);
                }
            }
        }
        Long death = registry.getDeathDay(villager.getUuid());
        if (death != null) {
            registry.resurrected(villager.getUuid(), WorldCalendar.exactCalendarDay(world), world.getTime());
            Resurrection.returned(villager, world);
        }
        Resurrection.update(villager, world);

        long body = registry.getBodyBirthDay(villager.getUuid());
        applyGrowth(villager, WorldCalendar.yearsBetween(body, today));
        Aging.update(villager, world, ageInYears(body, today));
    }

    public static boolean setAge(VillagerEntityMCA villager, ServerWorld world, int years) {
        LifeRegistry registry = LifeRegistry.get(world.getServer());
        UUID uuid = villager.getUuid();
        long today = WorldCalendar.now(world).dayIndex();
        Long birth = registry.getBirthDay(uuid);
        Long body = registry.getBodyBirthDay(uuid);
        if (birth != null && body != null && years < ageInYears(birth, today) - ageInYears(body, today)) {
            return false;
        }
        long target = WorldCalendar.gameDayOf(WorldCalendar.calendarDay(today) - Math.round((years + 0.5) * DAYS_PER_YEAR));
        for (int i = 0; i < 64 && ageInYears(target, today) != years; i++) {
            target += ageInYears(target, today) > years ? 1 : -1;
        }
        registry.forceBirthDay(uuid, target);
        update(villager);
        return true;
    }

    public static void forgetDead(ServerWorld world, UUID uuid) {
        MinecraftServer server = world.getServer();
        WellbeingRegistry.get(server).remove(uuid);
        LifeRegistry.get(server).clearZombie(uuid);
        WorkRegistry work = WorkRegistry.get(server);
        WorkRegistry.Record record = work.get(uuid);
        if (record != null && (record.center != null || record.depot != null)) {
            record.center = null;
            record.depot = null;
            record.depotDimension = null;
            work.markDirty();
        }
        WorkManager.forget(uuid);
    }

    // l'arbre MCA fige le genre à la création de la fiche : chez d'anciens villageois il peut différer du genre réel
    private static void syncTreeGender(VillagerEntityMCA villager, ServerWorld world) {
        Gender gender = villager.getGenetics().getGender();
        if (gender != Gender.MALE && gender != Gender.FEMALE) {
            return;
        }
        FamilyTreeNode node = villager.getRelationships().getFamilyEntry();
        if (node.gender() != gender) {
            node.setGender(gender);
            FamilyTree.get(world).markDirty();
        }
    }

    public static boolean isMenopausal(Entity entity) {
        if (!(entity instanceof VillagerEntityMCA villager) || villager.getGenetics().getGender() != Gender.FEMALE
                || !(villager.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        Long birth = LifeRegistry.get(world.getServer()).getBodyBirthDay(villager.getUuid());
        return birth != null && ageInYears(birth, WorldCalendar.now(world).dayIndex()) >= MENOPAUSE_AGE;
    }

    public static int ageInYears(long birthDay, long today) {
        // milieu du jour de jeu : en vitesse rapide (1 jour = 1 an) le début du jour oscille d'un jour autour de
        // l'anniversaire (années bissextiles), ce qui faisait stagner puis sauter l'âge ; identique en vitesse « 1 jour »
        long start = WorldCalendar.calendarDay(today);
        long reference = start + (WorldCalendar.calendarDay(today + 1) - start) / 2;
        return Period.between(WorldCalendar.dateOfDay(birthDay), WorldCalendar.dateOfCalendarDay(reference)).getYears();
    }

    private static boolean isNewborn(VillagerEntityMCA villager, ServerWorld world) {
        return stageIndex(AgeState.byCurrentAge(villager.getBreedingAge())) == 0 && hasLivingParent(villager, world);
    }

    private static long initialBirthDay(VillagerEntityMCA villager, ServerWorld world, long today) {
        int stage = stageIndex(AgeState.byCurrentAge(villager.getBreedingAge()));
        Random random = world.getRandom();
        int[] range = INITIAL_AGE_RANGES[stage];
        int years = FamilyAges.initialYears(world, villager.getUuid(), range[0], range[1], random);
        return WorldCalendar.gameDayOf(WorldCalendar.calendarDay(today) - (long) (years * DAYS_PER_YEAR) - random.nextInt(365));
    }

    private static boolean hasLivingParent(VillagerEntityMCA villager, ServerWorld world) {
        FamilyTree tree = FamilyTree.get(world);
        FamilyTreeNode node = villager.getRelationships().getFamilyEntry();
        return tree.getOrEmpty(node.father()).filter(p -> !p.isDeceased()).isPresent()
                || tree.getOrEmpty(node.mother()).filter(p -> !p.isDeceased()).isPresent();
    }

    private static int stageIndex(AgeState state) {
        return switch (state) {
            case BABY -> 0;
            case TODDLER -> 1;
            case CHILD -> 2;
            case TEEN -> 3;
            default -> 4;
        };
    }

    private static void applyGrowth(VillagerEntityMCA villager, double years) {
        int current = villager.getBreedingAge();
        if (years >= STAGE_START_YEARS[4]) {
            if (current < 0) {
                villager.setBreedingAge(0);
            }
            return;
        }

        int target = targetBreedingAge(years);
        if (Math.abs(current - target) > GROWTH_TOLERANCE) {
            villager.setBreedingAge(target);
        }
    }

    static int targetBreedingAge(double years) {
        int maxAge = AgeState.getMaxAge();
        int stageDuration = AgeState.getStageDuration();
        for (int stage = 0; stage < 4; stage++) {
            double start = STAGE_START_YEARS[stage];
            double end = STAGE_START_YEARS[stage + 1];
            if (years < end) {
                double delta = Math.max(0, (years - start) / (end - start));
                int stageStart = -maxAge + stage * stageDuration;
                int age = (int) (stageStart + delta * stageDuration);
                int last = stageStart + stageDuration - STAGE_MARGIN;
                return Math.min(Math.max(age, stageStart + 1), Math.max(stageStart + 1, Math.min(last, -STAGE_MARGIN)));
            }
        }
        return 0;
    }
}
