package com.maitreyoshi.mcavie.work.tasks;

import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkGesture;
import com.maitreyoshi.mcavie.work.WorkManager;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.DyeColor;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BreederTask extends AbstractJobTask {
    private static final int SPECIES_CAP = 8;
    private static final Set<EntityType<?>> SPECIES = Set.of(
            EntityType.COW, EntityType.SHEEP, EntityType.PIG, EntityType.CHICKEN, EntityType.RABBIT);
    private static final int SCAN_INTERVAL = 400;
    private static final int AFTER_ACTION = 40;
    private static final double REACH_SQ = 6.25;
    private static final int REACH_TICKS = 200;
    private static final long BLACKLIST_TICKS = 2400;

    private enum Action {SHEAR, BREED, EGG}

    private Action action;
    private UUID targetA;
    private UUID targetB;
    private long nextScan;
    private boolean done;
    private final Reach<UUID> reach = new Reach<>(REACH_TICKS, BLACKLIST_TICKS);

    public BreederTask() {
        super(Job.BREEDER);
    }

    @Override
    protected boolean canWork(ServerWorld world, VillagerEntityMCA villager) {
        if (action != null && targetsValid(world, villager)) {
            return true;
        }
        action = null;
        if (world.getTime() < nextScan) {
            return false;
        }
        nextScan = world.getTime() + SCAN_INTERVAL;
        plan(world, villager);
        return action != null;
    }

    @Override
    protected boolean keepWorking(ServerWorld world, VillagerEntityMCA villager) {
        return !done && action != null && targetsValid(world, villager);
    }

    @Override
    protected void run(ServerWorld world, VillagerEntityMCA villager, long time) {
        done = false;
        reach.reset();
        holdTool(villager);
    }

    @Override
    protected void keepRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        Entity first = world.getEntity(targetA);
        if (first == null) {
            return;
        }
        if (villager.squaredDistanceTo(first) > REACH_SQ) {
            walkTo(villager, first.getBlockPos());
            if (reach.giveUp(world, targetA)) {
                action = null;
                nextScan = Math.min(nextScan, time + AFTER_ACTION);
            }
            return;
        }
        arrive(villager, WorkGesture.TEND);
        villager.lookAt(first.getBlockPos());
        villager.swingHand(Hand.MAIN_HAND);

        switch (action) {
            case SHEAR -> {
                if (first instanceof SheepEntity sheep) {
                    sheep.setSheared(true);
                    world.playSound(null, sheep.getBlockPos(), SoundEvents.ENTITY_SHEEP_SHEAR, SoundCategory.NEUTRAL, 1.0F, 1.0F);
                    store(villager, new ItemStack(woolOf(sheep.getColor()), 1 + world.random.nextInt(3)));
                    gainExperience(villager, 1);
                }
            }
            case BREED -> {
                if (first instanceof AnimalEntity a && world.getEntity(targetB) instanceof AnimalEntity b) {
                    a.lovePlayer(null);
                    b.lovePlayer(null);
                    world.spawnParticles(ParticleTypes.HEART, a.getX(), a.getEyeY(), a.getZ(), 3, 0.3, 0.3, 0.3, 0.02);
                    world.spawnParticles(ParticleTypes.HEART, b.getX(), b.getEyeY(), b.getZ(), 3, 0.3, 0.3, 0.3, 0.02);
                    gainExperience(villager, 1);
                }
            }
            case EGG -> {
                if (first instanceof ItemEntity egg) {
                    store(villager, egg.getStack().copy(), false);
                    egg.discard();
                    world.playSound(null, villager.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.NEUTRAL, 0.3F, 1.4F);
                    gainExperience(villager, 1);
                }
            }
        }
        done = true;
    }

    @Override
    protected void finishRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        super.finishRunning(world, villager, time);
        if (done) {
            nextScan = Math.min(nextScan, time + AFTER_ACTION);
        }
        action = null;
        targetA = null;
        targetB = null;
        done = false;
    }

    private boolean targetsValid(ServerWorld world, VillagerEntityMCA villager) {
        if (targetA == null) {
            return false;
        }
        Entity a = world.getEntity(targetA);
        if (a == null || !a.isAlive() || !inZone(villager, a.getBlockPos()) || !isLoaded(world, a.getBlockPos())) {
            return false;
        }
        return switch (action) {
            case SHEAR -> a instanceof SheepEntity sheep && !sheep.isSheared() && !sheep.isBaby();
            case EGG -> a instanceof ItemEntity;
            case BREED -> {
                Entity b = targetB == null ? null : world.getEntity(targetB);
                yield a instanceof AnimalEntity x && b instanceof AnimalEntity y && b.isAlive()
                        && canBreed(x) && canBreed(y);
            }
        };
    }

    private void plan(ServerWorld world, VillagerEntityMCA villager) {
        reach.prune(world);
        WorkManager.Zone zone = zone(villager);
        int radius = zone.radius();
        Box box = new Box(zone.center()).expand(radius, radius, radius);
        List<AnimalEntity> animals = world.getEntitiesByClass(AnimalEntity.class, box,
                a -> a.isAlive() && SPECIES.contains(a.getType()) && zone.contains(a.getBlockPos()));

        SheepEntity sheep = animals.stream()
                .filter(a -> a instanceof SheepEntity s && !s.isBaby() && !s.isSheared() && !reach.isBlacklisted(world, a.getUuid()))
                .map(a -> (SheepEntity) a)
                .min(Comparator.comparingDouble(villager::squaredDistanceTo)).orElse(null);
        if (sheep != null) {
            action = Action.SHEAR;
            targetA = sheep.getUuid();
            targetB = null;
            return;
        }

        for (EntityType<?> type : SPECIES) {
            List<AnimalEntity> same = animals.stream().filter(a -> a.getType() == type).toList();
            if (same.size() >= SPECIES_CAP) {
                continue;
            }
            List<AnimalEntity> ready = new ArrayList<>(same.stream().filter(a -> canBreed(a) && !reach.isBlacklisted(world, a.getUuid())).toList());
            if (ready.size() < 2) {
                continue;
            }
            ready.sort(Comparator.comparingDouble(villager::squaredDistanceTo));
            AnimalEntity first = ready.get(0);
            AnimalEntity second = ready.stream().skip(1)
                    .min(Comparator.comparingDouble(first::squaredDistanceTo)).orElse(null);
            if (second != null) {
                action = Action.BREED;
                targetA = first.getUuid();
                targetB = second.getUuid();
                return;
            }
        }

        List<ItemEntity> eggs = world.getEntitiesByClass(ItemEntity.class, box,
                e -> e.isAlive() && e.getStack().isOf(Items.EGG) && zone.contains(e.getBlockPos()) && !reach.isBlacklisted(world, e.getUuid()));
        ItemEntity egg = eggs.stream().min(Comparator.comparingDouble(villager::squaredDistanceTo)).orElse(null);
        if (egg != null) {
            action = Action.EGG;
            targetA = egg.getUuid();
            targetB = null;
        }
    }

    private static boolean canBreed(AnimalEntity animal) {
        return animal.isAlive() && !animal.isBaby() && animal.getBreedingAge() == 0 && !animal.isInLove();
    }

    private static net.minecraft.item.Item woolOf(DyeColor color) {
        return Registries.ITEM.get(new Identifier("minecraft", color.getName() + "_wool"));
    }
}
