package com.maitreyoshi.mcavie.work.tasks;

import com.maitreyoshi.mcavie.life.Wellbeing;
import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkGesture;
import com.maitreyoshi.mcavie.work.WorkManager;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class DoctorTask extends AbstractJobTask {
    private static final int SCAN_INTERVAL = 100;
    private static final int COOLDOWN = 200;
    private static final int TREAT_TICKS = 40;
    private static final float HEAL_AMOUNT = 6F;
    private static final double REACH_SQ = 6.25;
    private static final int REACH_TICKS = 200;
    private static final long BLACKLIST_TICKS = 2400;

    private UUID patientId;
    private long nextScan;
    private long cooldownUntil;
    private int treatTicks;
    private boolean treated;
    private final Reach<UUID> reach = new Reach<>(REACH_TICKS, BLACKLIST_TICKS);

    public DoctorTask() {
        super(Job.DOCTOR);
    }

    @Override
    protected boolean canWork(ServerWorld world, VillagerEntityMCA villager) {
        long now = world.getTime();
        if (now < cooldownUntil) {
            return false;
        }
        if (patientId != null && patient(world, villager) != null) {
            return true;
        }
        patientId = null;
        if (now < nextScan) {
            return false;
        }
        nextScan = now + SCAN_INTERVAL;
        VillagerEntityMCA found = findPatient(world, villager);
        if (found != null) {
            patientId = found.getUuid();
        }
        return patientId != null;
    }

    @Override
    protected boolean keepWorking(ServerWorld world, VillagerEntityMCA villager) {
        return !treated && patientId != null && patient(world, villager) != null;
    }

    @Override
    protected void run(ServerWorld world, VillagerEntityMCA villager, long time) {
        treatTicks = 0;
        treated = false;
        reach.reset();
        holdTool(villager);
    }

    @Override
    protected void keepRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        VillagerEntityMCA patient = patient(world, villager);
        if (patient == null) {
            return;
        }
        BlockPos pos = patient.getBlockPos();
        if (villager.squaredDistanceTo(patient) > REACH_SQ) {
            walkTo(villager, pos);
            if (reach.giveUp(world, patientId)) {
                patientId = null;
            }
            return;
        }
        reach.reset();
        arrive(villager, WorkGesture.HEAL);
        villager.lookAt(pos);
        holdTool(villager);
        treatTicks++;
        if (treatTicks % 10 == 1) {
            villager.swingHand(Hand.MAIN_HAND);
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, patient.getX(), patient.getBodyY(0.6), patient.getZ(), 4, 0.4, 0.5, 0.4, 0.02);
            world.playSound(null, villager.getBlockPos(), SoundEvents.ITEM_BOTTLE_FILL, SoundCategory.NEUTRAL, 0.4F, 1.2F);
        }
        if (treatTicks < TREAT_TICKS) {
            return;
        }
        if (Wellbeing.isSick(world, patient.getUuid())) {
            Wellbeing.cure(patient);
        } else if (patient.getHealth() < patient.getMaxHealth()) {
            patient.heal(HEAL_AMOUNT);
        }
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, patient.getX(), patient.getBodyY(0.6), patient.getZ(), 12, 0.5, 0.6, 0.5, 0.05);
        gainExperience(villager, 1);
        treated = true;
    }

    @Override
    protected void finishRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        super.finishRunning(world, villager, time);
        if (treated) {
            cooldownUntil = time + COOLDOWN;
        }
        patientId = null;
        treatTicks = 0;
        treated = false;
    }

    private VillagerEntityMCA patient(ServerWorld world, VillagerEntityMCA villager) {
        if (patientId == null) {
            return null;
        }
        Entity entity = world.getEntity(patientId);
        if (entity instanceof VillagerEntityMCA patient && patient != villager && patient.isAlive()
                && inZone(villager, patient.getBlockPos()) && needsCare(world, patient)) {
            return patient;
        }
        return null;
    }

    private static boolean needsCare(ServerWorld world, VillagerEntityMCA patient) {
        return Wellbeing.isSick(world, patient.getUuid()) || patient.getHealth() < patient.getMaxHealth();
    }

    private VillagerEntityMCA findPatient(ServerWorld world, VillagerEntityMCA villager) {
        reach.prune(world);
        WorkManager.Zone zone = zone(villager);
        int radius = zone.radius();
        Box box = new Box(zone.center()).expand(radius, radius, radius);
        List<VillagerEntityMCA> candidates = world.getEntitiesByClass(VillagerEntityMCA.class, box,
                v -> v != villager && v.isAlive() && zone.contains(v.getBlockPos()) && needsCare(world, v)
                        && !reach.isBlacklisted(world, v.getUuid()));
        return candidates.stream()
                .min(Comparator.<VillagerEntityMCA>comparingInt(v -> Wellbeing.isSick(world, v.getUuid()) ? 0 : 1)
                        .thenComparingDouble(villager::squaredDistanceTo))
                .orElse(null);
    }
}
