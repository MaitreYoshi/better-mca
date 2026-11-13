package com.maitreyoshi.mcavie.work.tasks;

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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
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
    protected boolean canWork(ServerLevel world, VillagerEntityMCA villager) {
        long now = world.getGameTime();
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
            patientId = found.getUUID();
        }
        return patientId != null;
    }

    @Override
    protected boolean keepWorking(ServerLevel world, VillagerEntityMCA villager) {
        return !treated && patientId != null && patient(world, villager) != null;
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        treatTicks = 0;
        treated = false;
        reach.reset();
        holdTool(villager);
    }

    @Override
    protected void tick(ServerLevel world, VillagerEntityMCA villager, long time) {
        VillagerEntityMCA patient = patient(world, villager);
        if (patient == null) {
            return;
        }
        BlockPos pos = patient.blockPosition();
        if (villager.distanceToSqr(patient) > REACH_SQ) {
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
            villager.swing(InteractionHand.MAIN_HAND);
            world.sendParticles(ParticleTypes.HAPPY_VILLAGER, patient.getX(), patient.getY(0.6), patient.getZ(), 4, 0.4, 0.5, 0.4, 0.02);
            world.playSound(null, villager.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 0.4F, 1.2F);
        }
        if (treatTicks < TREAT_TICKS) {
            return;
        }
        if (Wellbeing.isSick(world, patient.getUUID())) {
            Wellbeing.cure(patient);
        } else if (patient.getHealth() < patient.getMaxHealth()) {
            patient.heal(HEAL_AMOUNT);
        }
        world.sendParticles(ParticleTypes.HAPPY_VILLAGER, patient.getX(), patient.getY(0.6), patient.getZ(), 12, 0.5, 0.6, 0.5, 0.05);
        gainExperience(villager, 1);
        treated = true;
    }

    @Override
    protected void stop(ServerLevel world, VillagerEntityMCA villager, long time) {
        super.stop(world, villager, time);
        if (treated) {
            cooldownUntil = time + COOLDOWN;
        }
        patientId = null;
        treatTicks = 0;
        treated = false;
    }

    private VillagerEntityMCA patient(ServerLevel world, VillagerEntityMCA villager) {
        if (patientId == null) {
            return null;
        }
        Entity entity = world.getEntity(patientId);
        if (entity instanceof VillagerEntityMCA patient && patient != villager && patient.isAlive()
                && inZone(villager, patient.blockPosition()) && needsCare(world, patient)) {
            return patient;
        }
        return null;
    }

    private static boolean needsCare(ServerLevel world, VillagerEntityMCA patient) {
        return Wellbeing.isSick(world, patient.getUUID()) || patient.getHealth() < patient.getMaxHealth();
    }

    private VillagerEntityMCA findPatient(ServerLevel world, VillagerEntityMCA villager) {
        reach.prune(world);
        WorkManager.Zone zone = zone(villager);
        int radius = zone.radius();
        AABB box = new AABB(zone.center()).inflate(radius, radius, radius);
        List<VillagerEntityMCA> candidates = world.getEntitiesOfClass(VillagerEntityMCA.class, box,
                v -> v != villager && v.isAlive() && zone.contains(v.blockPosition()) && needsCare(world, v)
                        && !reach.isBlacklisted(world, v.getUUID()));
        return candidates.stream()
                .min(Comparator.<VillagerEntityMCA>comparingInt(v -> Wellbeing.isSick(world, v.getUUID()) ? 0 : 1)
                        .thenComparingDouble(villager::distanceToSqr))
                .orElse(null);
    }
}
