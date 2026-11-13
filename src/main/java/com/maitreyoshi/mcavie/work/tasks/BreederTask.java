package com.maitreyoshi.mcavie.work.tasks;

import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkGesture;
import com.maitreyoshi.mcavie.work.WorkManager;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
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
    protected boolean canWork(ServerLevel world, VillagerEntityMCA villager) {
        if (action != null && targetsValid(world, villager)) {
            return true;
        }
        action = null;
        if (world.getGameTime() < nextScan) {
            return false;
        }
        nextScan = world.getGameTime() + SCAN_INTERVAL;
        plan(world, villager);
        return action != null;
    }

    @Override
    protected boolean keepWorking(ServerLevel world, VillagerEntityMCA villager) {
        return !done && action != null && targetsValid(world, villager);
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        done = false;
        reach.reset();
        holdTool(villager);
    }

    @Override
    protected void tick(ServerLevel world, VillagerEntityMCA villager, long time) {
        Entity first = world.getEntity(targetA);
        if (first == null) {
            return;
        }
        if (villager.distanceToSqr(first) > REACH_SQ) {
            walkTo(villager, first.blockPosition());
            if (reach.giveUp(world, targetA)) {
                action = null;
                nextScan = Math.min(nextScan, time + AFTER_ACTION);
            }
            return;
        }
        arrive(villager, WorkGesture.TEND);
        villager.lookAt(first.blockPosition());
        villager.swing(InteractionHand.MAIN_HAND);

        switch (action) {
            case SHEAR -> {
                if (first instanceof Sheep sheep) {
                    sheep.setSheared(true);
                    world.playSound(null, sheep.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.NEUTRAL, 1.0F, 1.0F);
                    store(villager, new ItemStack(woolOf(sheep.getColor()), 1 + world.random.nextInt(3)));
                    gainExperience(villager, 1);
                }
            }
            case BREED -> {
                if (first instanceof Animal a && world.getEntity(targetB) instanceof Animal b) {
                    a.setInLove(null);
                    b.setInLove(null);
                    world.sendParticles(ParticleTypes.HEART, a.getX(), a.getEyeY(), a.getZ(), 3, 0.3, 0.3, 0.3, 0.02);
                    world.sendParticles(ParticleTypes.HEART, b.getX(), b.getEyeY(), b.getZ(), 3, 0.3, 0.3, 0.3, 0.02);
                    gainExperience(villager, 1);
                }
            }
            case EGG -> {
                if (first instanceof ItemEntity egg) {
                    store(villager, egg.getItem().copy(), false);
                    egg.discard();
                    world.playSound(null, villager.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.3F, 1.4F);
                    gainExperience(villager, 1);
                }
            }
        }
        done = true;
    }

    @Override
    protected void stop(ServerLevel world, VillagerEntityMCA villager, long time) {
        super.stop(world, villager, time);
        if (done) {
            nextScan = Math.min(nextScan, time + AFTER_ACTION);
        }
        action = null;
        targetA = null;
        targetB = null;
        done = false;
    }

    private boolean targetsValid(ServerLevel world, VillagerEntityMCA villager) {
        if (targetA == null) {
            return false;
        }
        Entity a = world.getEntity(targetA);
        if (a == null || !a.isAlive() || !inZone(villager, a.blockPosition()) || !isLoaded(world, a.blockPosition())) {
            return false;
        }
        return switch (action) {
            case SHEAR -> a instanceof Sheep sheep && !sheep.isSheared() && !sheep.isBaby();
            case EGG -> a instanceof ItemEntity;
            case BREED -> {
                Entity b = targetB == null ? null : world.getEntity(targetB);
                yield a instanceof Animal x && b instanceof Animal y && b.isAlive()
                        && canBreed(x) && canBreed(y);
            }
        };
    }

    private void plan(ServerLevel world, VillagerEntityMCA villager) {
        reach.prune(world);
        WorkManager.Zone zone = zone(villager);
        int radius = zone.radius();
        AABB box = new AABB(zone.center()).inflate(radius, radius, radius);
        List<Animal> animals = world.getEntitiesOfClass(Animal.class, box,
                a -> a.isAlive() && SPECIES.contains(a.getType()) && zone.contains(a.blockPosition()));

        Sheep sheep = animals.stream()
                .filter(a -> a instanceof Sheep s && !s.isBaby() && !s.isSheared() && !reach.isBlacklisted(world, a.getUUID()))
                .map(a -> (Sheep) a)
                .min(Comparator.comparingDouble(villager::distanceToSqr)).orElse(null);
        if (sheep != null) {
            action = Action.SHEAR;
            targetA = sheep.getUUID();
            targetB = null;
            return;
        }

        for (EntityType<?> type : SPECIES) {
            List<Animal> same = animals.stream().filter(a -> a.getType() == type).toList();
            if (same.size() >= SPECIES_CAP) {
                continue;
            }
            List<Animal> ready = new ArrayList<>(same.stream().filter(a -> canBreed(a) && !reach.isBlacklisted(world, a.getUUID())).toList());
            if (ready.size() < 2) {
                continue;
            }
            ready.sort(Comparator.comparingDouble(villager::distanceToSqr));
            Animal first = ready.get(0);
            Animal second = ready.stream().skip(1)
                    .min(Comparator.comparingDouble(first::distanceToSqr)).orElse(null);
            if (second != null) {
                action = Action.BREED;
                targetA = first.getUUID();
                targetB = second.getUUID();
                return;
            }
        }

        List<ItemEntity> eggs = world.getEntitiesOfClass(ItemEntity.class, box,
                e -> e.isAlive() && e.getItem().is(Items.EGG) && zone.contains(e.blockPosition()) && !reach.isBlacklisted(world, e.getUUID()));
        ItemEntity egg = eggs.stream().min(Comparator.comparingDouble(villager::distanceToSqr)).orElse(null);
        if (egg != null) {
            action = Action.EGG;
            targetA = egg.getUUID();
            targetB = null;
        }
    }

    private static boolean canBreed(Animal animal) {
        return animal.isAlive() && !animal.isBaby() && animal.getAge() == 0 && !animal.isInLove();
    }

    private static net.minecraft.world.item.Item woolOf(DyeColor color) {
        return BuiltInRegistries.ITEM.get(new ResourceLocation("minecraft", color.getName() + "_wool"));
    }
}
