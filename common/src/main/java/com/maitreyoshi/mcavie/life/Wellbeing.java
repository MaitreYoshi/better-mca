package com.maitreyoshi.mcavie.life;

import com.maitreyoshi.mcavie.SurnameNetwork;
import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.village.VillageDecrees;
import com.maitreyoshi.mcavie.village.VillageEconomy;
import com.maitreyoshi.mcavie.life.WellbeingRegistry.Record;
import com.maitreyoshi.mcavie.social.Social;
import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkRegistry;
import net.mca.entity.ai.relationship.AgeState;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.relationship.RelationshipState;
import net.mca.server.world.data.FamilyTreeNode;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class Wellbeing {
    public static final float MARRIAGE = 25;
    public static final float NEW_CHILD = 20;
    public static final float LIKED_GIFT = 5;
    public static final float SLEPT = 2;
    public static final float SURROUNDED_DAY = 1;
    public static final float HOME_DAY = 1;
    public static final float HIT_BY_PLAYER = -10;
    public static final float HIT = -5;
    public static final float NO_SLEEP_DAY = -5;
    public static final float ISOLATED_DAY = -3;
    public static final float LOST_SPOUSE = -30;
    public static final float LOST_CHILD = -40;
    public static final float LOST_PARENT = -20;
    public static final float DIVORCE = -25;
    public static final float HOMELESS_DAY = -2;
    public static final float EMPLOYED_DAY = 1;
    public static final float UNEMPLOYED_DAY = -1;
    public static final float DAILY_RETURN = 1;

    public static final int MAX_AGE = 120;
    private static final double GOMPERTZ_B = 0.11;
    private static final double GOMPERTZ_M = 90;
    private static final double SICK_DEATH_BASE = 0.0002;
    private static final long SICK_TICKS = 72000;

    private static final RelationshipState[] RELATIONSHIP_STATES = RelationshipState.values();

    private Wellbeing() {
    }

    public static void update(VillagerEntityMCA villager, boolean scanNeighbours) {
        if (!(villager.getWorld() instanceof ServerWorld world) || !villager.isAlive()) {
            return;
        }
        WellbeingRegistry registry = WellbeingRegistry.get(world.getServer());
        Long birth = LifeRegistry.get(world.getServer()).getBodyBirthDay(villager.getUuid());
        if (birth == null) {
            return;
        }
        long today = WorldCalendar.now(world).dayIndex();
        long night = world.getTimeOfDay() / WorldCalendar.TICKS_PER_DAY;
        FamilyTreeNode node = villager.getRelationships().getFamilyEntry();

        Record record = registry.get(villager.getUuid());
        if (record == null) {
            record = create(world.getRandom(), birth == today, today, night, node);
            registry.put(villager.getUuid(), record);
        }
        Object[] before = state(record);

        long now = world.getServer().getOverworld().getTime();
        if (record.sick) {
            if (record.sickUntil < 0) {
                record.sickUntil = now + SICK_TICKS;
            } else if (now >= record.sickUntil) {
                record.sick = false;
            }
        }

        if (villager.isSleeping() && record.lastSleptNight != night) {
            record.lastSleptNight = night;
            record.addHappiness(SLEPT);
        }

        if (scanNeighbours) {
            List<VillagerEntityMCA> near = new ArrayList<>(2);
            world.collectEntitiesByType(TypeFilter.instanceOf(VillagerEntityMCA.class), villager.getBoundingBox().expand(16),
                    e -> e != villager && e.isAlive() && e.squaredDistanceTo(villager) <= 16 * 16, near, 2);
            int close = near.size();
            boolean anyone = close > 0;
            if (!anyone) {
                world.collectEntitiesByType(TypeFilter.instanceOf(VillagerEntityMCA.class), villager.getBoundingBox().expand(32),
                        e -> e != villager && e.isAlive(), near, 1);
                anyone = !near.isEmpty();
            }
            if (anyone) {
                record.lastCompanyDay = today;
            }
            if (close >= 2) {
                record.lastSurroundedDay = today;
            }
            if (anyone && Social.isFriendNear(world, villager)) {
                record.lastFriendDay = today;
            }
        }

        RelationshipState state = node.getRelationshipState();
        RelationshipState previous = RELATIONSHIP_STATES[Math.min(record.knownRelationshipState, RELATIONSHIP_STATES.length - 1)];
        if (isMarried(state) && !isMarried(previous)) {
            record.addHappiness(MARRIAGE);
            com.maitreyoshi.mcavie.village.CivilRegistry.marriage(villager, world, node);
        } else if (isMarried(previous) && state == RelationshipState.SINGLE) {
            record.addHappiness(DIVORCE);
        }
        record.knownRelationshipState = state.ordinal();

        int children = node.children().size();
        if (children > record.knownChildren) {
            record.addHappiness(NEW_CHILD * (children - record.knownChildren));
        }
        record.knownChildren = children;

        int equilibrium = -1;
        while (record.lastProcessedDay < today) {
            if (equilibrium < 0) {
                equilibrium = VillageEconomy.equilibriumFor(villager);
            }
            long day = record.lastProcessedDay;
            boolean lastDay = day == today - 1;
            if (lastDay) {
                if (record.lastFriendDay >= day) {
                    record.addHappiness(Social.FRIEND_NEAR_DAY);
                } else if (record.lastSurroundedDay >= day) {
                    record.addHappiness(SURROUNDED_DAY);
                }
                if (Social.hasEnemyAtHome(world, villager)) {
                    record.addHappiness(Social.ENEMY_AT_HOME_DAY);
                }
                if (record.lastCompanyDay < day) {
                    record.addHappiness(ISOLATED_DAY);
                }
                if (night - record.lastSleptNight >= 2) {
                    record.addHappiness(NO_SLEEP_DAY);
                }
                record.addHappiness(villager.getResidency().getHome().isPresent() ? HOME_DAY : HOMELESS_DAY);
                if (AgeState.byCurrentAge(villager.getBreedingAge()) == AgeState.ADULT) {
                    boolean employed = WorkRegistry.get(world.getServer()).getJob(villager.getUuid()) != Job.NONE;
                    record.addHappiness(employed ? EMPLOYED_DAY : UNEMPLOYED_DAY);
                }
            }
            returnTowards(record, equilibrium);
            record.lastProcessedDay++;

            if (dailyHealth(villager, world, record, WorldCalendar.yearsBetween(birth, day + 1), WorldCalendar.calendarDaysPerGameDay(day + 1), now, lastDay)) {
                registry.markDirty();
                return;
            }
        }

        if (record.sick) {
            showSickness(villager, world);
        }

        if (!Arrays.equals(before, state(record))) {
            registry.markDirty();
        }
        syncIfChanged(villager, record);
    }

    private static Object[] state(Record record) {
        return new Object[]{record.happiness, record.lastProcessedDay, record.lastSleptNight, record.lastCompanyDay,
                record.lastSurroundedDay, record.lastFriendDay, record.knownChildren, record.knownRelationshipState,
                record.sick, record.sickUntil};
    }

    private static Record create(Random random, boolean newborn, long today, long night, FamilyTreeNode node) {
        Record record = new Record();
        record.happiness = newborn ? 70 : 40 + random.nextInt(41);
        record.lastProcessedDay = today;
        record.lastSleptNight = night;
        record.lastCompanyDay = today;
        record.lastSurroundedDay = today - 1;
        record.knownChildren = node.children().size();
        record.knownRelationshipState = node.getRelationshipState().ordinal();
        return record;
    }

    private static boolean isMarried(RelationshipState state) {
        return state == RelationshipState.MARRIED_TO_VILLAGER || state == RelationshipState.MARRIED_TO_PLAYER;
    }

    private static void returnTowards(Record record, int target) {
        if (record.happiness > target) {
            record.happiness = Math.max(target, record.happiness - DAILY_RETURN);
        } else if (record.happiness < target) {
            record.happiness = Math.min(target, record.happiness + DAILY_RETURN);
        }
    }

    private static boolean dailyHealth(VillagerEntityMCA villager, ServerWorld world, Record record, double age, double days, long now, boolean lastDay) {
        Random random = world.getRandom();
        double h = record.happiness;

        if (age >= MAX_AGE || random.nextDouble() < overDays(oldAgeDeathChance(age, h), days)) {
            villager.kill();
            return true;
        }

        if (record.sick) {
            if (!lastDay) {
                return false;
            }
            double chance = SICK_DEATH_BASE * (1 + (100 - h) / 50) * Math.max(1, 1 + (age - 60) / 10);
            if (random.nextDouble() < Math.min(chance, 0.3)) {
                villager.kill();
                return true;
            }
        } else if (random.nextDouble() < overDays(illnessChance(h) * VillageDecrees.illnessMultiplier(villager), days)) {
            record.sick = true;
            record.sickUntil = now + SICK_TICKS;
        }
        return false;
    }

    public static double oldAgeDeathChance(double age, double happiness) {
        double shift = happiness >= 50 ? -(happiness - 50) / 5 : (50 - happiness) * 0.7;
        double effectiveAge = age + shift;
        return GOMPERTZ_B * Math.exp(GOMPERTZ_B * (effectiveAge - GOMPERTZ_M)) / LifeCycle.DAYS_PER_YEAR;
    }

    public static double overDays(double perDay, double days) {
        return days == 1 ? perDay : 1 - Math.pow(1 - Math.min(1, perDay), days);
    }

    static double illnessChance(double happiness) {
        double perYear = 0.05 + 1.95 * Math.pow((100 - happiness) / 100, 2);
        return perYear / LifeCycle.DAYS_PER_YEAR;
    }

    private static void showSickness(VillagerEntityMCA villager, ServerWorld world) {
        villager.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 0, false, false));
        if (world.getRandom().nextInt(4) == 0) {
            world.spawnParticles(ParticleTypes.SNEEZE, villager.getX(), villager.getEyeY(), villager.getZ(), 3, 0.2, 0.1, 0.2, 0.01);
        }
    }

    public static boolean cure(VillagerEntityMCA villager) {
        if (!(villager.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        WellbeingRegistry registry = WellbeingRegistry.get(world.getServer());
        Record record = registry.get(villager.getUuid());
        if (record == null || !record.sick) {
            return false;
        }
        record.sick = false;
        record.sickUntil = 0;
        registry.markDirty();
        villager.removeStatusEffect(StatusEffects.SLOWNESS);
        syncIfChanged(villager, record);
        return true;
    }

    public static boolean isSick(ServerWorld world, UUID uuid) {
        Record record = WellbeingRegistry.get(world.getServer()).get(uuid);
        return record != null && record.sick;
    }

    public static void resume(ServerWorld world, UUID uuid, long today) {
        WellbeingRegistry registry = WellbeingRegistry.get(world.getServer());
        Record record = registry.get(uuid);
        if (record != null && record.lastProcessedDay < today) {
            record.lastProcessedDay = today;
            registry.markDirty();
        }
    }

    public static void addHappiness(ServerWorld world, UUID uuid, float delta) {
        WellbeingRegistry.get(world.getServer()).addHappiness(uuid, delta);
    }

    private static void syncIfChanged(VillagerEntityMCA villager, Record record) {
        int value = Math.round(record.happiness);
        if (value != record.lastSentHappiness || record.sick != record.lastSentSick) {
            record.lastSentHappiness = value;
            record.lastSentSick = record.sick;
            SurnameNetwork.sendHappiness(villager, value, record.sick);
        }
    }

    public static void sendTo(VillagerEntityMCA villager, net.minecraft.server.network.ServerPlayerEntity player) {
        if (villager.getWorld() instanceof ServerWorld world) {
            Record record = WellbeingRegistry.get(world.getServer()).get(villager.getUuid());
            if (record != null) {
                SurnameNetwork.sendHappinessTo(player, villager, Math.round(record.happiness), record.sick);
            }
        }
    }
}
