package com.maitreyoshi.mcavie.tree.server;

import com.maitreyoshi.mcavie.MCAVie;
import net.mca.server.world.data.FamilyTreeNode;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SpouseRegistry extends PersistentState {
    private static final String DATA_ID = "mca_surnames_spouses";

    private static final Queue<UUID[]> PENDING = new ConcurrentLinkedQueue<>();

    private final Map<UUID, Set<UUID>> spouses = new HashMap<>();

    public static SpouseRegistry get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(SpouseRegistry::fromNbt, SpouseRegistry::new, DATA_ID);
    }

    private static SpouseRegistry fromNbt(NbtCompound nbt) {
        SpouseRegistry registry = new SpouseRegistry();
        NbtCompound entries = nbt.getCompound("spouses");
        for (String key : entries.getKeys()) {
            try {
                Set<UUID> set = registry.spouses.computeIfAbsent(UUID.fromString(key), k -> new LinkedHashSet<>());
                NbtList list = entries.getList(key, NbtElement.STRING_TYPE);
                for (int i = 0; i < list.size(); i++) {
                    set.add(UUID.fromString(list.getString(i)));
                }
            } catch (IllegalArgumentException e) {
                MCAVie.LOGGER.warn("UUID invalide dans le registre des conjoints : {}", key);
            }
        }
        return registry;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound entries = new NbtCompound();
        spouses.forEach((uuid, set) -> {
            NbtList list = new NbtList();
            set.forEach(spouse -> list.add(NbtString.of(spouse.toString())));
            entries.put(uuid.toString(), list);
        });
        nbt.put("spouses", entries);
        return nbt;
    }

    public List<UUID> get(UUID uuid) {
        Set<UUID> set = spouses.get(uuid);
        return set == null ? List.of() : new ArrayList<>(set);
    }

    public void add(UUID a, UUID b) {
        boolean changed = spouses.computeIfAbsent(a, k -> new LinkedHashSet<>()).add(b);
        changed |= spouses.computeIfAbsent(b, k -> new LinkedHashSet<>()).add(a);
        if (changed) {
            markDirty();
        }
    }

    public static void record(UUID a, UUID b) {
        if (FamilyTreeNode.isValid(a) && FamilyTreeNode.isValid(b) && !a.equals(b)) {
            PENDING.add(new UUID[]{a, b});
        }
    }

    public static void flush(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        SpouseRegistry registry = get(server);
        UUID[] pair;
        while ((pair = PENDING.poll()) != null) {
            registry.add(pair[0], pair[1]);
        }
    }

    public static void clearPending() {
        PENDING.clear();
    }
}
