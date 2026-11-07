package com.maitreyoshi.mcavie.tree;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public final class TreeSelectionCheck {
    private final Map<String, UUID> ids = new LinkedHashMap<>();
    private final Map<UUID, String> names = new LinkedHashMap<>();
    private final Map<UUID, List<UUID>> parents = new LinkedHashMap<>();
    private final Map<UUID, List<UUID>> children = new LinkedHashMap<>();
    private final Map<UUID, List<UUID>> spouses = new LinkedHashMap<>();
    private int failures;

    private UUID id(String name) {
        return ids.computeIfAbsent(name, n -> {
            UUID uuid = new UUID(2, ids.size() + 1);
            names.put(uuid, n);
            return uuid;
        });
    }

    private void marry(String a, String b) {
        link(spouses, id(a), id(b));
        link(spouses, id(b), id(a));
    }

    private void child(String father, String mother, String child) {
        UUID kid = id(child);
        for (String parent : new String[]{father, mother}) {
            link(parents, kid, id(parent));
            link(children, id(parent), kid);
        }
    }

    private static void link(Map<UUID, List<UUID>> map, UUID from, UUID to) {
        List<UUID> list = map.computeIfAbsent(from, k -> new ArrayList<>());
        if (!list.contains(to)) {
            list.add(to);
        }
    }

    private final TreeSelection.Graph graph = new TreeSelection.Graph() {
        @Override
        public List<UUID> parents(UUID id) {
            return parents.getOrDefault(id, List.of());
        }

        @Override
        public List<UUID> children(UUID id) {
            return children.getOrDefault(id, List.of());
        }

        @Override
        public List<UUID> spouses(UUID id) {
            return spouses.getOrDefault(id, List.of());
        }
    };

    private void expect(String label, String root, int up, int down, int max, String... expected) {
        Set<String> actual = new TreeSet<>();
        TreeSelection.select(graph, id(root), up, down, max).forEach(uuid -> actual.add(names.get(uuid)));
        Set<String> wanted = new TreeSet<>(List.of(expected));
        if (actual.equals(wanted)) {
            System.out.println("OK   " + label);
            return;
        }
        failures++;
        Set<String> missing = new TreeSet<>(wanted);
        missing.removeAll(actual);
        Set<String> extra = new TreeSet<>(actual);
        extra.removeAll(wanted);
        System.out.println("FAIL " + label + " manquants=" + missing + " en trop=" + extra);
    }

    private void build() {
        marry("Papy", "Mamie");
        marry("Papy", "BelleMamie");
        child("Papy", "Mamie", "Pere");
        child("Papy", "Mamie", "Oncle");
        child("Papy", "Mamie", "Tante");
        marry("Oncle", "Tata");
        child("Oncle", "Tata", "Cousin");
        marry("PapyM", "MamieM");
        child("PapyM", "MamieM", "Mere");
        marry("Pere", "Mere");
        child("Pere", "Mere", "Racine");
        child("Pere", "Mere", "Soeur");
        marry("Pere", "BelleMere");
        child("Pere", "BelleMere", "DemiFrere");
        marry("DemiFrere", "DemiBelleSoeur");
        child("DemiFrere", "DemiBelleSoeur", "Neveu");
        marry("Neveu", "NeveuEpouse");
        child("Neveu", "NeveuEpouse", "PetitNeveu");
        marry("BeauPere", "BelleMereEpouse");
        child("BeauPere", "BelleMereEpouse", "Epouse");
        child("BeauPere", "BelleMereEpouse", "BeauFrere");
        marry("Racine", "Ex");
        marry("Racine", "Epouse");
        child("Racine", "Epouse", "Fils");
        child("Racine", "Epouse", "Fille");
        marry("Fils", "BelleFille");
        child("Fils", "BelleFille", "PetitFils");
        marry("PetitFils", "PetiteFilleEpouse");
        child("PetitFils", "PetiteFilleEpouse", "ArrierePetitFils");
    }

    private void run() {
        build();
        expect("racine haut 2 bas 2", "Racine", 2, 2, 400,
                "Racine", "Pere", "Mere", "Papy", "Mamie", "PapyM", "MamieM",
                "Ex", "Epouse", "Fils", "BelleFille", "Fille", "PetitFils", "PetiteFilleEpouse",
                "Soeur", "DemiFrere", "DemiBelleSoeur", "Neveu", "NeveuEpouse", "PetitNeveu");
        expect("racine haut 1 bas 1", "Racine", 1, 1, 400,
                "Racine", "Pere", "Mere", "Ex", "Epouse", "Fils", "BelleFille", "Fille",
                "Soeur", "DemiFrere", "DemiBelleSoeur", "Neveu", "NeveuEpouse");
        expect("racine illimite", "Racine", -1, -1, 400,
                "Racine", "Pere", "Mere", "Papy", "Mamie", "PapyM", "MamieM",
                "Ex", "Epouse", "Fils", "BelleFille", "Fille", "PetitFils", "PetiteFilleEpouse", "ArrierePetitFils",
                "Soeur", "DemiFrere", "DemiBelleSoeur", "Neveu", "NeveuEpouse", "PetitNeveu");
        expect("epouse haut 2 bas 2", "Epouse", 2, 2, 400,
                "Epouse", "BeauPere", "BelleMereEpouse", "BeauFrere",
                "Racine", "Fils", "BelleFille", "Fille", "PetitFils", "PetiteFilleEpouse");
        expect("pere haut 1 bas 1", "Pere", 1, 1, 400,
                "Pere", "Papy", "Mamie", "Oncle", "Tante", "Tata", "Cousin",
                "Mere", "BelleMere", "Racine", "Ex", "Epouse", "Soeur", "DemiFrere", "DemiBelleSoeur");
        expect("plafond", "Racine", 2, 2, 5, "Racine", "Pere", "Mere", "Papy", "Mamie");

        List<UUID> first = new ArrayList<>(TreeSelection.select(graph, id("Racine"), -1, -1, 400));
        List<UUID> second = new ArrayList<>(TreeSelection.select(graph, id("Racine"), -1, -1, 400));
        if (!first.equals(second) || !first.get(0).equals(id("Racine"))) {
            failures++;
            System.out.println("FAIL ordre stable");
        } else {
            System.out.println("OK   ordre stable");
        }

        if (failures > 0) {
            System.out.println(failures + " échec(s)");
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        new TreeSelectionCheck().run();
    }
}
