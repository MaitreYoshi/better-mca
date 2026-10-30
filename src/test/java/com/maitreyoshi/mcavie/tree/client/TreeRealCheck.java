package com.maitreyoshi.mcavie.tree.client;

import com.maitreyoshi.mcavie.SurnameText;
import com.maitreyoshi.mcavie.tree.FamilyPerson;
import com.maitreyoshi.mcavie.tree.TreeSelection;
import net.mca.entity.ai.relationship.Gender;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public final class TreeRealCheck {
    static final UUID DEBUG_ENTRY = UUID.fromString("12341234-1234-1234-1234-123412341234");
    static final int[] DEPTHS = {1, 2, 3, 4, -1};

    record Node(UUID id, String name, int gender, boolean player, boolean deceased, UUID father, UUID mother,
                UUID partner, int state, List<UUID> children) {
    }

    final Map<UUID, Node> nodes = new LinkedHashMap<>();
    final Map<UUID, List<UUID>> history = new HashMap<>();
    final Map<UUID, String> surnames = new HashMap<>();
    final Map<UUID, Long> births = new HashMap<>();
    final Map<UUID, Long> deaths = new HashMap<>();
    final Map<UUID, FamilyPerson> persons = new LinkedHashMap<>();

    static NbtCompound read(File dir, String name) throws IOException {
        File file = new File(dir, name);
        if (!file.exists()) {
            return new NbtCompound();
        }
        return NbtIo.readCompressed(file).getCompound("data");
    }

    TreeRealCheck(File dir) throws IOException {
        if (!new File(dir, "MCA-FamilyTree.dat").exists() && new File(dir, "data/MCA-FamilyTree.dat").exists()) {
            dir = new File(dir, "data");
        }
        NbtCompound tree = read(dir, "MCA-FamilyTree.dat");
        for (String key : tree.getKeys()) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (DEBUG_ENTRY.equals(id)) {
                continue;
            }
            NbtCompound nbt = tree.getCompound(key);
            List<UUID> kids = new ArrayList<>();
            NbtList list = nbt.getList("children", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                kids.add(list.getCompound(i).getUuid("uuid"));
            }
            nodes.put(id, new Node(id, nbt.getString("name"), nbt.getInt("gender"), nbt.getBoolean("isPlayer"), nbt.getBoolean("isDeceased"),
                    nbt.getUuid("father"), nbt.getUuid("mother"), nbt.containsUuid("spouse") ? nbt.getUuid("spouse") : TreeLayoutCheck.NIL,
                    nbt.getInt("marriageState"), kids));
        }
        NbtCompound spouses = read(dir, "mca_surnames_spouses.dat").getCompound("spouses");
        for (String key : spouses.getKeys()) {
            NbtList list = spouses.getList(key, NbtElement.STRING_TYPE);
            List<UUID> ids = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                ids.add(UUID.fromString(list.getString(i)));
            }
            history.put(UUID.fromString(key), ids);
        }
        NbtCompound names = read(dir, "mca_surnames.dat").getCompound("surnames");
        for (String key : names.getKeys()) {
            surnames.put(UUID.fromString(key), names.getString(key));
        }
        NbtCompound life = read(dir, "mca_surnames_life.dat");
        for (String key : life.getCompound("births").getKeys()) {
            births.put(UUID.fromString(key), life.getCompound("births").getLong(key));
        }
        for (String key : life.getCompound("deaths").getKeys()) {
            deaths.put(UUID.fromString(key), life.getCompound("deaths").getLong(key));
        }
        for (Node node : nodes.values()) {
            persons.put(node.id, person(node));
        }
    }

    boolean contains(UUID id) {
        return id != null && nodes.containsKey(id);
    }

    List<UUID> parents(UUID id) {
        Node node = nodes.get(id);
        List<UUID> list = new ArrayList<>(2);
        if (node == null) {
            return list;
        }
        if (contains(node.father)) {
            list.add(node.father);
        }
        if (contains(node.mother) && !node.mother.equals(node.father)) {
            list.add(node.mother);
        }
        return list;
    }

    List<UUID> children(UUID id) {
        Node node = nodes.get(id);
        List<UUID> list = new ArrayList<>();
        if (node == null) {
            return list;
        }
        for (UUID child : node.children) {
            if (contains(child) && !child.equals(id)) {
                list.add(child);
            }
        }
        return list;
    }

    final Map<UUID, List<UUID>> spouseCache = new HashMap<>();

    List<UUID> spouses(UUID id) {
        return spouseCache.computeIfAbsent(id, key -> {
            Node node = nodes.get(id);
            if (node == null) {
                return List.of();
            }
            Set<UUID> set = new LinkedHashSet<>();
            if (node.partner != null && !node.partner.equals(TreeLayoutCheck.NIL) && node.state != 0) {
                set.add(node.partner);
            }
            set.addAll(history.getOrDefault(id, List.of()));
            for (UUID childId : node.children) {
                Node child = nodes.get(childId);
                if (child != null) {
                    if (id.equals(child.father)) {
                        set.add(child.mother);
                    } else if (id.equals(child.mother)) {
                        set.add(child.father);
                    }
                }
            }
            set.remove(id);
            set.removeIf(spouse -> !contains(spouse));
            return List.copyOf(set);
        });
    }

    FamilyPerson person(Node node) {
        String surname = surnames.getOrDefault(node.id, "");
        String name = surname.isEmpty() ? node.name : SurnameText.withSurname(node.name, SurnameText.display(surname, Gender.byId(node.gender)));
        return new FamilyPerson(node.id, name, surname, node.gender, node.player, node.deceased,
                node.father == null ? TreeLayoutCheck.NIL : node.father, node.mother == null ? TreeLayoutCheck.NIL : node.mother,
                spouses(node.id), children(node.id), "", births.get(node.id), node.deceased ? deaths.get(node.id) : null, -1, null, null);
    }

    List<FamilyPerson> select(UUID root, int up, int down) {
        TreeSelection.Graph graph = new TreeSelection.Graph() {
            @Override
            public List<UUID> parents(UUID id) {
                return TreeRealCheck.this.parents(id);
            }

            @Override
            public List<UUID> children(UUID id) {
                return TreeRealCheck.this.children(id);
            }

            @Override
            public List<UUID> spouses(UUID id) {
                return TreeRealCheck.this.spouses(id);
            }
        };
        List<FamilyPerson> list = new ArrayList<>();
        for (UUID id : TreeSelection.select(graph, root, up, down, 400)) {
            FamilyPerson p = persons.get(id);
            if (p != null) {
                list.add(p);
            }
        }
        return list;
    }

    static String depth(int d) {
        return d < 0 ? "X" : String.valueOf(d);
    }

    public static void main(String[] args) throws IOException {
        String data = System.getProperty("treeData", args.length > 0 ? args[0] : "");
        if (data.isEmpty()) {
            System.out.println("usage: gradlew treeRealCheck -DtreeData=<world or data folder>");
            System.exit(2);
        }
        String focus = System.getProperty("treeRoot", "");
        TreeLayoutCheck.svgDir = Path.of(System.getProperty("treeSvg", "build/tree-real-svg"));
        TreeLayoutCheck.svgBudget = Integer.getInteger("treeSvgCount", 40);
        TreeRealCheck real = new TreeRealCheck(new File(data));
        System.out.println("persons: " + real.persons.size());
        TreeLayoutCheck.Report total = new TreeLayoutCheck.Report("real");
        Map<String, Integer> reasons = new TreeMap<>();
        TreeLayoutCheck.Report tree = new TreeLayoutCheck.Report("dedicated");
        int every = Integer.getInteger("treeEvery", 1);
        int index = 0;
        int shown = 0;
        int limit = Integer.getInteger("treeShown", 15);
        for (FamilyPerson root : real.persons.values()) {
            if (index++ % every != 0 || !focus.isEmpty() && !root.name().toLowerCase(Locale.ROOT).contains(focus.toLowerCase(Locale.ROOT))) {
                continue;
            }
            for (int up : DEPTHS) {
                for (int down : DEPTHS) {
                    String name = root.name() + "_u" + depth(up) + "d" + depth(down);
                    boolean named = !focus.isEmpty();
                    TreeLayoutCheck.Report r = TreeLayoutCheck.check(name, real.select(root.id(), up, down), root.id(), true, named);
                    total.add(r);
                    total.cases++;
                    if (r.fallback > 0) {
                        reasons.merge(r.fallbackReason.split(":")[0], 1, Integer::sum);
                    } else {
                        tree.add(r);
                        tree.cases++;
                    }
                    if ((r.errors() > 0 || r.fallback > 0) && (named || shown++ < limit)) {
                        System.out.println("  " + r + (r.fallback > 0 ? " FALLBACK " + r.fallbackReason : " TREE"));
                        r.details.stream().limit(10).forEach(d -> System.out.println("      " + d));
                        if (named) {
                            r.dump.forEach(System.out::println);
                        }
                    }
                }
            }
        }
        System.out.println("fallback reasons: " + reasons);
        System.out.println("DEDICATED:  " + tree);
        System.out.println("REAL TOTAL: " + total);
        if (total.errors() + total.fallback > 0) {
            System.exit(1);
        }
    }
}
