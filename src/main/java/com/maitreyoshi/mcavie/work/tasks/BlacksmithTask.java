package com.maitreyoshi.mcavie.work.tasks;

import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkManager;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

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
    protected boolean canWork(ServerWorld world, VillagerEntityMCA villager) {
        if (hasTarget(world, villager)) {
            return true;
        }
        targetId = null;
        atDepot = false;
        long now = world.getTime();
        if (now < nextScan) {
            return false;
        }
        nextScan = now + SCAN_INTERVAL;
        if (!hasIron(world, villager)) {
            return false;
        }
        VillagerEntityMCA found = findClient(world, villager);
        if (found != null) {
            targetId = found.getUuid();
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
    protected boolean keepWorking(ServerWorld world, VillagerEntityMCA villager) {
        return !done && hasTarget(world, villager);
    }

    @Override
    protected void run(ServerWorld world, VillagerEntityMCA villager, long time) {
        workTicks = 0;
        done = false;
        reach.reset();
        holdTool(villager);
    }

    @Override
    protected void keepRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        BlockPos pos;
        VillagerEntityMCA client = null;
        if (atDepot) {
            pos = depotPos(villager);
        } else {
            client = client(world, villager);
            pos = client != null ? client.getBlockPos() : null;
        }
        if (pos == null) {
            return;
        }
        if (villager.squaredDistanceTo(Vec3d.ofCenter(pos)) > REACH_SQ) {
            villager.moveTowards(pos);
            if (reach.giveUp(world, atDepot ? pos : targetId)) {
                targetId = null;
                atDepot = false;
            }
            return;
        }
        reach.reset();
        villager.getNavigation().stop();
        villager.lookAt(pos);
        holdTool(villager);
        workTicks++;
        if (workTicks == 1) {
            villager.swingHand(Hand.MAIN_HAND);
            world.playSound(null, villager.getBlockPos(), SoundEvents.BLOCK_ANVIL_USE, SoundCategory.NEUTRAL, 0.3F, 1.2F);
        } else if (workTicks % 10 == 0) {
            villager.swingHand(Hand.MAIN_HAND);
            world.spawnParticles(ParticleTypes.CRIT, villager.getX(), villager.getBodyY(0.6), villager.getZ(), 5, 0.4, 0.3, 0.4, 0.1);
        }
        if (workTicks < WORK_TICKS) {
            return;
        }
        Inventory chest = depotInventory(world, villager);
        ItemStack damaged = null;
        if (atDepot) {
            for (int i = 0; chest != null && i < chest.size(); i++) {
                ItemStack stack = chest.getStack(i);
                if (isDamaged(stack) && (damaged == null || stack.getDamage() > damaged.getDamage())) {
                    damaged = stack;
                }
            }
        } else if (client != null) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack stack = client.getEquippedStack(slot);
                if (isDamaged(stack) && (damaged == null || stack.getDamage() > damaged.getDamage())) {
                    damaged = stack;
                }
            }
        }
        if (damaged != null && takeIron(chest)) {
            damaged.setDamage(0);
            if (atDepot) {
                chest.markDirty();
            } else {
                world.spawnParticles(ParticleTypes.CRIT, client.getX(), client.getBodyY(0.6), client.getZ(), 10, 0.4, 0.5, 0.4, 0.1);
            }
            gainExperience(villager, 1);
        }
        done = true;
    }

    @Override
    protected void finishRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        targetId = null;
        atDepot = false;
        workTicks = 0;
        done = false;
    }

    private boolean hasTarget(ServerWorld world, VillagerEntityMCA villager) {
        if (atDepot) {
            BlockPos depot = depotPos(villager);
            return depot != null && !reach.isBlacklisted(world, depot) && depotHasDamaged(world, villager);
        }
        return targetId != null && client(world, villager) != null;
    }

    private VillagerEntityMCA client(ServerWorld world, VillagerEntityMCA villager) {
        if (targetId == null) {
            return null;
        }
        Entity entity = world.getEntity(targetId);
        if (entity instanceof VillagerEntityMCA client && client != villager && client.isAlive()
                && inZone(villager, client.getBlockPos()) && damageOf(client) > 0) {
            return client;
        }
        return null;
    }

    private static boolean isDamaged(ItemStack stack) {
        return !stack.isEmpty() && stack.isDamageable() && stack.getDamage() > 0;
    }

    private static int damageOf(VillagerEntityMCA villager) {
        int total = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = villager.getEquippedStack(slot);
            if (isDamaged(stack)) {
                total += stack.getDamage();
            }
        }
        return total;
    }

    private VillagerEntityMCA findClient(ServerWorld world, VillagerEntityMCA villager) {
        reach.prune(world);
        WorkManager.Zone zone = zone(villager);
        int radius = zone.radius();
        Box box = new Box(zone.center()).expand(radius, radius, radius);
        List<VillagerEntityMCA> candidates = world.getEntitiesByClass(VillagerEntityMCA.class, box,
                v -> v != villager && v.isAlive() && zone.contains(v.getBlockPos()) && damageOf(v) > 0
                        && !reach.isBlacklisted(world, v.getUuid()));
        return candidates.stream()
                .max(Comparator.<VillagerEntityMCA>comparingInt(BlacksmithTask::damageOf)
                        .thenComparingDouble(v -> -villager.squaredDistanceTo(v)))
                .orElse(null);
    }

    private boolean hasIron(ServerWorld world, VillagerEntityMCA villager) {
        Inventory chest = depotInventory(world, villager);
        if (chest == null) {
            return false;
        }
        for (int i = 0; i < chest.size(); i++) {
            if (chest.getStack(i).isOf(Items.IRON_INGOT)) {
                return true;
            }
        }
        return false;
    }

    private static boolean takeIron(Inventory chest) {
        if (chest == null) {
            return false;
        }
        for (int i = 0; i < chest.size(); i++) {
            ItemStack stack = chest.getStack(i);
            if (stack.isOf(Items.IRON_INGOT)) {
                stack.decrement(1);
                chest.markDirty();
                return true;
            }
        }
        return false;
    }

    private boolean depotHasDamaged(ServerWorld world, VillagerEntityMCA villager) {
        Inventory chest = depotInventory(world, villager);
        if (chest == null) {
            return false;
        }
        for (int i = 0; i < chest.size(); i++) {
            if (isDamaged(chest.getStack(i))) {
                return true;
            }
        }
        return false;
    }
}
