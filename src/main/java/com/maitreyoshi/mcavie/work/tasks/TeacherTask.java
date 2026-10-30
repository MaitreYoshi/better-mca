package com.maitreyoshi.mcavie.work.tasks;

import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.life.LifeCycle;
import com.maitreyoshi.mcavie.life.LifeRegistry;
import com.maitreyoshi.mcavie.life.Wellbeing;
import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkManager;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TeacherTask extends AbstractJobTask {
    private static final int SCAN_INTERVAL = 400;
    private static final int MIN_AGE = 5;
    private static final int MAX_AGE = 17;
    private static final double STAY_SQ = 16.0;
    private static final double TEACH_SQ = 36.0;
    private static final int LESSON_TICKS = 200;
    private static final float HAPPINESS = 2F;

    private final Map<UUID, Long> taughtDay = new HashMap<>();
    private final List<VillagerEntityMCA> pupils = new java.util.ArrayList<>();
    private long nextScan;
    private boolean lastScan;
    private int lessonTicks;

    public TeacherTask() {
        super(Job.TEACHER);
    }

    @Override
    protected boolean canWork(ServerWorld world, VillagerEntityMCA villager) {
        long now = world.getTime();
        if (now < nextScan) {
            return lastScan;
        }
        nextScan = now + SCAN_INTERVAL;
        lastScan = !findPupils(world, villager).isEmpty();
        return lastScan;
    }

    @Override
    protected boolean keepWorking(ServerWorld world, VillagerEntityMCA villager) {
        return lessonTicks < LESSON_TICKS;
    }

    @Override
    protected void run(ServerWorld world, VillagerEntityMCA villager, long time) {
        lessonTicks = 0;
        pupils.clear();
        pupils.addAll(findPupils(world, villager));
        holdTool(villager);
    }

    @Override
    protected void keepRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        lessonTicks++;
        if (lessonTicks % 20 != 1) {
            return;
        }
        WorkManager.Zone zone = zone(villager);
        pupils.removeIf(p -> !p.isAlive() || p.isRemoved() || !zone.contains(p.getBlockPos()));
        if (pupils.isEmpty()) {
            lessonTicks = LESSON_TICKS;
            return;
        }
        double x = 0, y = 0, z = 0;
        for (VillagerEntityMCA pupil : pupils) {
            x += pupil.getX();
            y += pupil.getY();
            z += pupil.getZ();
        }
        Vec3d group = new Vec3d(x / pupils.size(), y / pupils.size(), z / pupils.size());
        BlockPos groupPos = BlockPos.ofFloored(group);
        if (villager.squaredDistanceTo(group) > STAY_SQ) {
            villager.moveTowards(groupPos);
        } else {
            villager.getNavigation().stop();
            villager.lookAt(groupPos);
        }
        if (lessonTicks % 60 == 1) {
            villager.swingHand(Hand.MAIN_HAND);
        }
        long today = WorldCalendar.now(world).dayIndex();
        for (VillagerEntityMCA pupil : pupils) {
            Long last = taughtDay.get(pupil.getUuid());
            if (last != null && last == today) {
                continue;
            }
            if (villager.squaredDistanceTo(pupil) <= TEACH_SQ) {
                Wellbeing.addHappiness(world, pupil.getUuid(), HAPPINESS);
                world.spawnParticles(ParticleTypes.ENCHANT, pupil.getX(), pupil.getBodyY(1.2), pupil.getZ(), 15, 0.4, 0.4, 0.4, 0.5);
                taughtDay.put(pupil.getUuid(), today);
                gainExperience(villager, 1);
            }
        }
    }

    @Override
    protected void finishRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        lessonTicks = 0;
        nextScan = 0;
        pupils.clear();
        if (taughtDay.size() > 200) {
            taughtDay.clear();
        }
    }

    private List<VillagerEntityMCA> findPupils(ServerWorld world, VillagerEntityMCA villager) {
        WorkManager.Zone zone = zone(villager);
        int radius = zone.radius();
        Box box = new Box(zone.center()).expand(radius, radius, radius);
        LifeRegistry registry = LifeRegistry.get(world.getServer());
        long today = WorldCalendar.now(world).dayIndex();
        return world.getEntitiesByClass(VillagerEntityMCA.class, box, v -> {
            if (v == villager || !v.isAlive() || !zone.contains(v.getBlockPos())) {
                return false;
            }
            Long birth = registry.getBirthDay(v.getUuid());
            if (birth == null) {
                return false;
            }
            int age = LifeCycle.ageInYears(birth, today);
            return age >= MIN_AGE && age <= MAX_AGE;
        });
    }
}
