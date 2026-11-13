package com.maitreyoshi.mcavie.tree.server;

import com.maitreyoshi.mcavie.MCAVie;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SpouseRegistry extends PersistentState {
    private static final String DATA_ID = "mca_surnames_spouses";

    // REMEMBER : simple mémoire ; MARRIED : ce couple devient le plus récent ; DIVORCED : séparation volontaire ; REVIVED : résurrection
    private enum Op { REMEMBER, MARRIED, DIVORCED, REVIVED }

    private record Pending(Op op, UUID a, UUID b) {
    }

    private static final Queue<Pending> PENDING = new ConcurrentLinkedQueue<>();

    // ordre d'insertion = ordre chronologique des mariages
    private final Map<UUID, Set<UUID>> spouses = new HashMap<>();
    private final Set<String> divorced = new HashSet<>();

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
        NbtList list = nbt.getList("divorced", NbtElement.STRING_TYPE);
        for (int i = 0; i < list.size(); i++) {
            registry.divorced.add(list.getString(i));
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
        NbtList list = new NbtList();
        divorced.forEach(key -> list.add(NbtString.of(key)));
        nbt.put("divorced", list);
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

    // Fusion de deux fiches : tout ce qui visait « from » vise désormais « to », sans changer l'ordre des mariages.
    public void replace(UUID from, UUID to) {
        Set<UUID> old = spouses.remove(from);
        for (Map.Entry<UUID, Set<UUID>> entry : spouses.entrySet()) {
            if (entry.getValue().contains(from)) {
                Set<UUID> updated = new LinkedHashSet<>();
                for (UUID id : entry.getValue()) {
                    updated.add(id.equals(from) ? to : id);
                }
                updated.remove(entry.getKey());
                entry.setValue(updated);
            }
        }
        if (old != null) {
            Set<UUID> target = spouses.computeIfAbsent(to, k -> new LinkedHashSet<>());
            target.addAll(old);
            target.remove(to);
        }
        Set<String> keys = new HashSet<>();
        for (String entry : divorced) {
            keys.add(entry.contains(from.toString()) ? rekey(entry, from, to) : entry);
        }
        divorced.clear();
        divorced.addAll(keys);
        markDirty();
    }

    private static String rekey(String entry, UUID from, UUID to) {
        String[] ids = entry.split("\\+");
        return key(ids[0].equals(from.toString()) ? to : UUID.fromString(ids[0]),
                ids[1].equals(from.toString()) ? to : UUID.fromString(ids[1]));
    }

    private void married(UUID a, UUID b) {
        Set<UUID> sa = spouses.computeIfAbsent(a, k -> new LinkedHashSet<>());
        Set<UUID> sb = spouses.computeIfAbsent(b, k -> new LinkedHashSet<>());
        sa.remove(b);
        sa.add(b);
        sb.remove(a);
        sb.add(a);
        divorced.remove(key(a, b));
        markDirty();
    }

    public boolean isDivorced(UUID a, UUID b) {
        return divorced.contains(key(a, b));
    }

    // dernier conjoint en date, null s'il n'y en a pas
    @Nullable
    public UUID latest(UUID uuid) {
        Set<UUID> set = spouses.get(uuid);
        UUID last = null;
        if (set != null) {
            for (UUID spouse : set) {
                last = spouse;
            }
        }
        return last;
    }

    // veuvage : l'un des deux est mort, et le défunt reste l'époux tant que le survivant ne s'est pas remarié
    @Nullable
    public UUID widowedSpouse(FamilyTree tree, FamilyTreeNode node) {
        UUID last = lastSpouse(tree, node);
        if (last == null) {
            return null;
        }
        boolean dead = node.isDeceased() || tree.getOrEmpty(last).map(FamilyTreeNode::isDeceased).orElse(false);
        return dead ? last : null;
    }

    // sans conjoint actuel : le dernier conjoint, s'il n'y a pas eu divorce et que ni l'un ni l'autre ne s'est remarié
    @Nullable
    private UUID lastSpouse(FamilyTree tree, FamilyTreeNode node) {
        if (FamilyTreeNode.isValid(node.partner())) {
            return null;
        }
        UUID last = latest(node.id());
        if (last == null || isDivorced(node.id(), last)) {
            return null;
        }
        FamilyTreeNode other = tree.getOrEmpty(last).orElse(null);
        if (other == null || FamilyTreeNode.isValid(other.partner()) || !node.id().equals(latest(last))) {
            return null;
        }
        return last;
    }

    private static String key(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + "+" + b : b + "+" + a;
    }

    public static void record(UUID a, UUID b) {
        enqueue(Op.REMEMBER, a, b);
    }

    public static void recordMarriage(UUID a, UUID b) {
        enqueue(Op.MARRIED, a, b);
    }

    public static void recordDivorce(UUID a, UUID b) {
        enqueue(Op.DIVORCED, a, b);
    }

    public static void recordRevival(UUID uuid) {
        if (FamilyTreeNode.isValid(uuid)) {
            PENDING.add(new Pending(Op.REVIVED, uuid, uuid));
        }
    }

    private static void enqueue(Op op, UUID a, UUID b) {
        if (FamilyTreeNode.isValid(a) && FamilyTreeNode.isValid(b) && !a.equals(b)) {
            PENDING.add(new Pending(op, a, b));
        }
    }

    public static void flush(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        SpouseRegistry registry = get(server);
        Pending pending;
        while ((pending = PENDING.poll()) != null) {
            switch (pending.op()) {
                case REMEMBER -> registry.add(pending.a(), pending.b());
                case MARRIED -> registry.married(pending.a(), pending.b());
                case DIVORCED -> {
                    registry.add(pending.a(), pending.b());
                    if (registry.divorced.add(key(pending.a(), pending.b()))) {
                        registry.markDirty();
                    }
                }
                case REVIVED -> registry.revive(server, pending.a());
            }
        }
    }

    // ressuscité : il retrouve son conjoint si celui-ci est en vie et ne s'est pas remarié entre-temps
    private void revive(MinecraftServer server, UUID uuid) {
        FamilyTree tree = FamilyTree.get(server.getOverworld());
        FamilyTreeNode node = tree.getOrEmpty(uuid).orElse(null);
        if (node == null || node.isDeceased()) {
            return;
        }
        UUID spouse = lastSpouse(tree, node);
        FamilyTreeNode other = spouse == null ? null : tree.getOrEmpty(spouse).orElse(null);
        if (other == null || other.isDeceased()) {
            return;
        }
        node.updatePartner(other);
        other.updatePartner(node);
        tree.markDirty();
    }

    public static void clearPending() {
        PENDING.clear();
    }
}
