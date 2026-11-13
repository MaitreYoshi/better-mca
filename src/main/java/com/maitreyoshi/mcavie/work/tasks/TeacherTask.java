package com.maitreyoshi.mcavie.work.tasks;

import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.life.LifeCycle;
import com.maitreyoshi.mcavie.life.LifeRegistry;
import com.maitreyoshi.mcavie.life.Wellbeing;
import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkGesture;
import com.maitreyoshi.mcavie.work.WorkManager;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
    protected boolean canWork(ServerLevel world, VillagerEntityMCA villager) {
        long now = world.getGameTime();
        if (now < nextScan) {
            return lastScan;
        }
        nextScan = now + SCAN_INTERVAL;
        lastScan = !findPupils(world, villager).isEmpty();
        return lastScan;
    }

    @Override
    protected boolean keepWorking(ServerLevel world, VillagerEntityMCA villager) {
        return lessonTicks < LESSON_TICKS;
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        lessonTicks = 0;
        pupils.clear();
        pupils.addAll(findPupils(world, villager));
        holdTool(villager);
    }

    @Override
    protected void tick(ServerLevel world, VillagerEntityMCA villager, long time) {
        lessonTicks++;
        if (lessonTicks % 20 != 1) {
            return;
        }
        WorkManager.Zone zone = zone(villager);
        pupils.removeIf(p -> !p.isAlive() || p.isRemoved() || !zone.contains(p.blockPosition()));
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
        Vec3 group = new Vec3(x / pupils.size(), y / pupils.size(), z / pupils.size());
        BlockPos groupPos = BlockPos.containing(group);
        if (villager.distanceToSqr(group) > STAY_SQ) {
            walkTo(villager, groupPos);
        } else {
            arrive(villager, WorkGesture.TEACH);
            villager.lookAt(groupPos);
        }
        if (lessonTicks % 40 == 1) {
            villager.swing(InteractionHand.MAIN_HAND);
            world.sendParticles(ParticleTypes.ENCHANT, villager.getX(), villager.getY(1.0), villager.getZ(), 8, 0.3, 0.3, 0.3, 0.3);
            world.playSound(null, villager.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 0.6F, 1.0F);
        }
        long today = WorldCalendar.now(world).dayIndex();
        for (VillagerEntityMCA pupil : pupils) {
            Long last = taughtDay.get(pupil.getUUID());
            if (last != null && last == today) {
                continue;
            }
            if (villager.distanceToSqr(pupil) <= TEACH_SQ) {
                Wellbeing.addHappiness(world, pupil.getUUID(), HAPPINESS);
                world.sendParticles(ParticleTypes.ENCHANT, pupil.getX(), pupil.getY(1.2), pupil.getZ(), 15, 0.4, 0.4, 0.4, 0.5);
                taughtDay.put(pupil.getUUID(), today);
                store(villager, new ItemStack(Items.EXPERIENCE_BOTTLE));
                gainExperience(villager, 1);
            }
        }
    }

    @Override
    protected void stop(ServerLevel world, VillagerEntityMCA villager, long time) {
        super.stop(world, villager, time);
        lessonTicks = 0;
        nextScan = 0;
        pupils.clear();
        if (taughtDay.size() > 200) {
            taughtDay.clear();
        }
    }

    private List<VillagerEntityMCA> findPupils(ServerLevel world, VillagerEntityMCA villager) {
        WorkManager.Zone zone = zone(villager);
        int radius = zone.radius();
        AABB box = new AABB(zone.center()).inflate(radius, radius, radius);
        LifeRegistry registry = LifeRegistry.get(world.getServer());
        long today = WorldCalendar.now(world).dayIndex();
        return world.getEntitiesOfClass(VillagerEntityMCA.class, box, v -> {
            if (v == villager || !v.isAlive() || !zone.contains(v.blockPosition())) {
                return false;
            }
            Long birth = registry.getBirthDay(v.getUUID());
            if (birth == null) {
                return false;
            }
            int age = LifeCycle.ageInYears(birth, today);
            return age >= MIN_AGE && age <= MAX_AGE;
        });
    }
}
