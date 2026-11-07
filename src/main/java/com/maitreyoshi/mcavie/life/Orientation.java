package com.maitreyoshi.mcavie.life;

import com.maitreyoshi.mcavie.mixin.TraitAccessor;
import net.mca.Config;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.Traits;
import net.mca.entity.ai.relationship.Gender;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;

import java.util.UUID;

public enum Orientation {
    // ordinal sauvegardé : ajouter seulement à la fin
    STRAIGHT,
    GAY,
    BISEXUAL,
    ASEXUAL;

    private static final Orientation[] VALUES = values();

    public static Orientation byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : STRAIGHT;
    }

    public static Orientation of(VillagerLike<?> villager) {
        Traits traits = villager.getTraits();
        if (traits.hasTrait(Traits.HOMOSEXUAL)) {
            return GAY;
        }
        if (traits.hasTrait(Traits.BISEXUAL)) {
            return BISEXUAL;
        }
        if (traits.hasTrait(Traits.ASEXUAL)) {
            return ASEXUAL;
        }
        return STRAIGHT;
    }

    public static boolean compatible(Orientation a, Gender genderA, Orientation b, Gender genderB) {
        if (a == ASEXUAL || b == ASEXUAL) {
            return false;
        }
        boolean sameSex = genderA == genderB;
        if (a == GAY || b == GAY) {
            return a == GAY && b == GAY && sameSex;
        }
        return !sameSex || a == BISEXUAL && b == BISEXUAL;
    }

    public static boolean compatible(VillagerLike<?> a, VillagerLike<?> b) {
        return compatible(of(a), a.getGenetics().getGender(), of(b), b.getGenetics().getGender());
    }

    public static void update(VillagerEntityMCA villager) {
        if (!(villager.getWorld() instanceof ServerWorld world)) {
            return;
        }
        OrientationRegistry registry = OrientationRegistry.get(world.getServer());
        UUID uuid = villager.getUuid();
        Orientation stored = registry.get(uuid);
        if (stored != null && !registry.isObserved(uuid)) {
            apply(villager.getTraits(), stored);
        }
        registry.set(uuid, of(villager), true);
    }

    public static Orientation known(ServerWorld world, UUID uuid, Random random) {
        OrientationRegistry registry = OrientationRegistry.get(world.getServer());
        Orientation stored = registry.get(uuid);
        if (stored == null) {
            stored = roll(random);
            registry.set(uuid, stored, false);
        }
        return stored;
    }

    public static void assign(ServerWorld world, UUID uuid, Orientation orientation) {
        OrientationRegistry.get(world.getServer()).set(uuid, orientation, false);
    }

    public static Orientation roll(Random random) {
        boolean gay = rolls(random, Traits.HOMOSEXUAL);
        boolean bisexual = rolls(random, Traits.BISEXUAL);
        boolean asexual = rolls(random, Traits.ASEXUAL);
        return gay ? GAY : bisexual ? BISEXUAL : asexual ? ASEXUAL : STRAIGHT;
    }

    private static boolean rolls(Random random, Traits.Trait trait) {
        if (!trait.isEnabled()) {
            return false;
        }
        double total = Traits.Trait.values().stream().mapToDouble(t -> ((TraitAccessor) t).mcavie$getChance()).sum();
        return total > 0 && random.nextFloat() < Config.getInstance().traitChance / total * ((TraitAccessor) trait).mcavie$getChance();
    }

    public static void apply(Traits traits, Orientation orientation) {
        traits.removeTrait(Traits.HOMOSEXUAL);
        traits.removeTrait(Traits.BISEXUAL);
        traits.removeTrait(Traits.ASEXUAL);
        switch (orientation) {
            case GAY -> traits.addTrait(Traits.HOMOSEXUAL);
            case BISEXUAL -> traits.addTrait(Traits.BISEXUAL);
            case ASEXUAL -> traits.addTrait(Traits.ASEXUAL);
            default -> {
            }
        }
    }
}
