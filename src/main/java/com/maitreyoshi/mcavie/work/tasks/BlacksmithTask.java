package com.maitreyoshi.mcavie.work.tasks;

import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.Repairs;
import com.maitreyoshi.mcavie.work.WorkGesture;
import com.maitreyoshi.mcavie.work.WorkManager;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class BlacksmithTask extends AbstractJobTask {
    private static final int SCAN_INTERVAL = 200;
    private static final int WORK_TICKS = 60;
    private static final double REACH_SQ = 6.25;
    private static final int REACH_TICKS = 200;
    private static final long BLACKLIST_TICKS = 2400;

    private UUID targetId;
    private boolean atDepot;
    private long nextScan;
    private int workTicks;
    private boolean done;
    private final Reach<Object> reach = new Reach<>(REACH_TICKS, BLACKLIST_TICKS);

    public BlacksmithTask() {
        super(Job.BLACKSMITH);
    }

    @Override
    protected boolean canWork(ServerLevel world, VillagerEntityMCA villager) {
        if (hasTarget(world, villager)) {
            return true;
        }
        targetId = null;
        atDepot = false;
        long now = world.getGameTime();
        if (now < nextScan) {
            return false;
        }
        nextScan = now + SCAN_INTERVAL;
        VillagerEntityMCA found = findClient(world, villager);
        if (found != null) {
            targetId = found.getUUID();
            return true;
        }
        BlockPos depot = depotPos(villager);
        if (depot != null && !reach.isBlacklisted(world, depot) && depotHasDamaged(world, villager)) {
            atDepot = true;
            return true;
        }
        return false;
    }

    @Override
    protected boolean keepWorking(ServerLevel world, VillagerEntityMCA villager) {
        return !done && hasTarget(world, villager);
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        workTicks = 0;
        done = false;
        reach.reset();
        holdTool(villager);
    }

    @Override
    protected void tick(ServerLevel world, VillagerEntityMCA villager, long time) {
        BlockPos pos;
        VillagerEntityMCA client = null;
        if (atDepot) {
            pos = depotPos(villager);
        } else {
            client = client(world, villager);
            pos = client != null ? client.blockPosition() : null;
        }
        if (pos == null) {
            return;
        }
        if (villager.distanceToSqr(Vec3.atCenterOf(pos)) > REACH_SQ) {
            walkTo(villager, pos);
            if (reach.giveUp(world, atDepot ? pos : targetId)) {
                targetId = null;
                atDepot = false;
            }
            return;
        }
        reach.reset();
        arrive(villager, WorkGesture.HAMMER);
        villager.lookAt(pos);
        holdTool(villager);
        workTicks++;
        if (workTicks == 1) {
            villager.swing(InteractionHand.MAIN_HAND);
            world.playSound(null, villager.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.3F, 1.2F);
        } else if (workTicks % 10 == 0) {
            villager.swing(InteractionHand.MAIN_HAND);
            world.playSound(null, villager.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.NEUTRAL, 0.15F, 1.6F);
            world.sendParticles(ParticleTypes.CRIT, villager.getX(), villager.getY(0.6), villager.getZ(), 5, 0.4, 0.3, 0.4, 0.1);
        }
        if (workTicks < WORK_TICKS) {
            return;
        }
        Container chest = depotInventory(world, villager);
        ItemStack damaged = null;
        Repairs.Cost cost = null;
        if (atDepot) {
            for (int i = 0; chest != null && i < chest.getContainerSize(); i++) {
                ItemStack stack = chest.getItem(i);
                Repairs.Cost c = Repairs.costIn(stack, chest);
                if (c != null && (damaged == null || stack.getDamageValue() > damaged.getDamageValue())) {
                    damaged = stack;
                    cost = c;
                }
            }
        } else if (client != null) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack stack = client.getItemBySlot(slot);
                Repairs.Cost c = Repairs.isHalfWorn(stack) ? Repairs.costIn(stack, chest) : null;
                if (c != null && (damaged == null || stack.getDamageValue() > damaged.getDamageValue())) {
                    damaged = stack;
                    cost = c;
                }
            }
        }
        if (damaged != null) {
            Repairs.pay(chest, cost);
            damaged.setDamageValue(0);
            if (atDepot) {
                chest.setChanged();
            } else {
                world.sendParticles(ParticleTypes.CRIT, client.getX(), client.getY(0.6), client.getZ(), 10, 0.4, 0.5, 0.4, 0.1);
            }
            gainExperience(villager, 1);
        }
        done = true;
    }

    @Override
    protected void stop(ServerLevel world, VillagerEntityMCA villager, long time) {
        super.stop(world, villager, time);
        targetId = null;
        atDepot = false;
        workTicks = 0;
        done = false;
    }

    private boolean hasTarget(ServerLevel world, VillagerEntityMCA villager) {
        if (atDepot) {
            BlockPos depot = depotPos(villager);
            return depot != null && !reach.isBlacklisted(world, depot) && depotHasDamaged(world, villager);
        }
        return targetId != null && client(world, villager) != null;
    }

    private VillagerEntityMCA client(ServerLevel world, VillagerEntityMCA villager) {
        if (targetId == null) {
            return null;
        }
        Entity entity = world.getEntity(targetId);
        if (entity instanceof VillagerEntityMCA client && client != villager && client.isAlive()
                && inZone(villager, client.blockPosition()) && damageOf(client, depotInventory(world, villager)) > 0) {
            return client;
        }
        return null;
    }

    // seul l'équipement à moitié usé et que la forge peut payer vaut le déplacement
    private static int damageOf(VillagerEntityMCA villager, Container forge) {
        int total = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = villager.getItemBySlot(slot);
            if (Repairs.isHalfWorn(stack) && Repairs.costIn(stack, forge) != null) {
                total += stack.getDamageValue();
            }
        }
        return total;
    }

    private VillagerEntityMCA findClient(ServerLevel world, VillagerEntityMCA villager) {
        reach.prune(world);
        WorkManager.Zone zone = zone(villager);
        int radius = zone.radius();
        AABB box = new AABB(zone.center()).inflate(radius, radius, radius);
        Container forge = depotInventory(world, villager);
        if (forge == null) {
            return null;
        }
        List<VillagerEntityMCA> candidates = world.getEntitiesOfClass(VillagerEntityMCA.class, box,
                v -> v != villager && v.isAlive() && zone.contains(v.blockPosition()) && damageOf(v, forge) > 0
                        && !reach.isBlacklisted(world, v.getUUID()));
        return candidates.stream()
                .max(Comparator.<VillagerEntityMCA>comparingInt(v -> damageOf(v, forge))
                        .thenComparingDouble(v -> -villager.distanceToSqr(v)))
                .orElse(null);
    }

    private boolean depotHasDamaged(ServerLevel world, VillagerEntityMCA villager) {
        Container chest = depotInventory(world, villager);
        if (chest == null) {
            return false;
        }
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (Repairs.costIn(chest.getItem(i), chest) != null) {
                return true;
            }
        }
        return false;
    }
}
