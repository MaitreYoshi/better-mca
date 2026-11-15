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

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public final class CivilRegistry {
    public static final int BIRTH = 0;
    public static final int MARRIAGE = 1;
    public static final int DEATH = 2;
    public static final int JUSTICE = 3;
    public static final String ZOMBIE = "zombie";

    private CivilRegistry() {
    }

    public static final long NO_EXACT = Long.MIN_VALUE;

    public record Entry(int kind, long day, String name, String other, int age, long exact) {
        public Entry(int kind, long day, String name, String other, int age) {
            this(kind, day, name, other, age, NO_EXACT);
        }

        public boolean hasExact() {
            return exact != NO_EXACT;
        }

        Entry withExact(long calendarDay) {
            return new Entry(kind, day, name, other, age, calendarDay);
        }

        NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putInt("kind", kind);
            nbt.putLong("day", day);
            nbt.putString("name", name);
            nbt.putString("other", other);
            nbt.putInt("age", age);
            if (hasExact()) {
                nbt.putLong("exact", exact);
            }
            return nbt;
        }

        static Entry fromNbt(NbtCompound nbt) {
            return new Entry(nbt.getInt("kind"), nbt.getLong("day"), nbt.getString("name"), nbt.getString("other"), nbt.getInt("age"),
                    nbt.contains("exact") ? nbt.getLong("exact") : NO_EXACT);
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
        add(village.isPresent() ? village : village(baby, world), exact(new Entry(BIRTH, today(world), name(baby), parents, 0),
                LifeRegistry.get(world.getServer()).exactBirth(baby.getUuid())));
        com.maitreyoshi.mcavie.social.SocialConversation.witness(world, baby, "birth", net.minecraft.text.Text.literal(name(baby)), baby.getUuid());
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
        add(village(villager, world), exact(new Entry(DEATH, today, name(villager), "", age),
                LifeRegistry.get(world.getServer()).exactDeath(villager.getUuid())));
    }

    public static void zombified(VillagerEntityMCA villager, ServerWorld world) {
        add(village(villager, world), new Entry(DEATH, today(world), name(villager), ZOMBIE, -1));
    }

    public static void record(Village village, int kind, long day, String name, String other, int age) {
        add(Optional.of(village), new Entry(kind, day, name, other, age));
    }

    public static void record(Village village, int kind, long day, String name, String other, int age, @Nullable Long exact) {
        add(Optional.of(village), exact(new Entry(kind, day, name, other, age), exact));
    }

    private static Entry exact(Entry entry, @Nullable Long calendarDay) {
        return calendarDay == null ? entry : entry.withExact(calendarDay);
    }

    /** Anciennes entrées : reprend la date exacte de l'arbre (même villageois, même jour de jeu). */
    public static void fillExactDates(ServerWorld world) {
        LifeRegistry life = LifeRegistry.get(world.getServer());
        Map<String, List<UUID>> byName = new HashMap<>();
        ((com.maitreyoshi.mcavie.tree.server.mixin.FamilyTreeAccessor) FamilyTree.get(world)).mcavie$getEntries().values()
                .forEach(n -> byName.computeIfAbsent(n.getName(), k -> new ArrayList<>()).add(n.id()));
        for (Village village : VillageManager.get(world)) {
            VillageState state = VillageState.of(village);
            boolean changed = false;
            for (ListIterator<Entry> it = state.registry.listIterator(); it.hasNext(); ) {
                Entry e = it.next();
                if (e.hasExact() || e.kind() != BIRTH && e.kind() != DEATH) {
                    continue;
                }
                boolean birth = e.kind() == BIRTH;
                Long exact = null;
                List<UUID> known = byName.getOrDefault(e.name(), List.of()).stream()
                        .filter(id -> (birth ? life.getBirthDay(id) : life.getDeathDay(id)) != null).toList();
                for (UUID id : known) {
                    Long day = birth ? life.getBirthDay(id) : life.getDeathDay(id);
                    if (day == e.day() || known.size() == 1) {
                        exact = birth ? life.exactBirth(id) : life.exactDeath(id);
                        break;
                    }
                }
                if (exact != null) {
                    it.set(e.withExact(exact));
                    changed = true;
                }
            }
            if (changed) {
                village.markDirty();
            }
        }
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
