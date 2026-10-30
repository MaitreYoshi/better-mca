package com.maitreyoshi.mcavie.village;

import com.maitreyoshi.mcavie.Surnames;
import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.life.LifeCycle;
import com.maitreyoshi.mcavie.life.LifeRegistry;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.VillagerLike;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageManager;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;

import java.util.Optional;
import java.util.stream.Collectors;

public final class CivilRegistry {
    public static final int BIRTH = 0;
    public static final int MARRIAGE = 1;
    public static final int DEATH = 2;
    public static final int JUSTICE = 3;
    public static final String ZOMBIE = "zombie";

    private CivilRegistry() {
    }

    public record Entry(int kind, long day, String name, String other, int age) {
        NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putInt("kind", kind);
            nbt.putLong("day", day);
            nbt.putString("name", name);
            nbt.putString("other", other);
            nbt.putInt("age", age);
            return nbt;
        }

        static Entry fromNbt(NbtCompound nbt) {
            return new Entry(nbt.getInt("kind"), nbt.getLong("day"), nbt.getString("name"), nbt.getString("other"), nbt.getInt("age"));
        }
    }

    public static void birth(VillagerEntityMCA baby, ServerWorld world) {
        Surnames.update(baby);
        FamilyTreeNode node = baby.getRelationships().getFamilyEntry();
        String parents = node.getParents().map(FamilyTreeNode::getName).filter(n -> n != null && !n.isBlank())
                .collect(Collectors.joining(" & "));
        Optional<Village> village = node.getParents()
                .map(parent -> VillageManager.get(world).findVillages(v -> v.hasResident(parent.id())).findFirst())
                .flatMap(Optional::stream)
                .findFirst();
        add(village.isPresent() ? village : village(baby, world), new Entry(BIRTH, today(world), name(baby), parents, 0));
    }

    public static void marriage(VillagerEntityMCA villager, ServerWorld world, FamilyTreeNode node) {
        Optional<Village> village = village(villager, world);
        if (village.isEmpty() || !FamilyTreeNode.isValid(node.partner())) {
            return;
        }
        String self = name(villager);
        String partner = FamilyTree.get(world).getOrEmpty(node.partner()).map(FamilyTreeNode::getName).orElse("");
        long today = today(world);
        VillageState state = VillageState.of(village.get());
        boolean known = state.registry.stream().anyMatch(e -> e.kind() == MARRIAGE && Math.abs(e.day() - today) <= 30
                && (e.name().equals(partner) && e.other().equals(self) || e.name().equals(self) && e.other().equals(partner)));
        if (!known) {
            add(village, new Entry(MARRIAGE, today, self, partner, 0));
        }
    }

    public static void death(VillagerEntityMCA villager, ServerWorld world) {
        Long birth = LifeRegistry.get(world.getServer()).getBirthDay(villager.getUuid());
        long today = today(world);
        int age = birth == null ? -1 : LifeCycle.ageInYears(birth, today);
        add(village(villager, world), new Entry(DEATH, today, name(villager), "", age));
    }

    public static void zombified(VillagerEntityMCA villager, ServerWorld world) {
        add(village(villager, world), new Entry(DEATH, today(world), name(villager), ZOMBIE, -1));
    }

    public static void record(Village village, int kind, long day, String name, String other, int age) {
        add(Optional.of(village), new Entry(kind, day, name, other, age));
    }

    private static String name(VillagerEntityMCA villager) {
        String name = villager.getTrackedValue(VillagerLike.VILLAGER_NAME);
        return name == null || name.isBlank() ? villager.getName().getString() : name;
    }

    private static long today(ServerWorld world) {
        return WorldCalendar.now(world).dayIndex();
    }

    private static Optional<Village> village(VillagerEntityMCA villager, ServerWorld world) {
        Optional<Village> home = villager.getResidency().getHomeVillage();
        return home.isPresent() ? home : VillageManager.get(world).findNearestVillage(villager);
    }

    private static void add(Optional<Village> village, Entry entry) {
        village.ifPresent(v -> {
            VillageState.of(v).addRegistry(entry);
            v.markDirty();
        });
    }
}
