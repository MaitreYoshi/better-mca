package com.maitreyoshi.mcavie.life;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.tree.server.mixin.FamilyTreeAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Âges cohérents avec l'arbre MCA : parents plus âgés que leurs enfants, conjoints d'âges proches,
 * et PNJ nés en jeu pas plus vieux que le monde. MCA ne stocke aucune date de naissance :
 * les âges viennent uniquement de LifeRegistry.
 */
public final class FamilyAges {
    private static final int MIN_PARENT_GAP = 18;
    private static final int MAX_PARENT_GAP = 45;
    private static final int MAX_SPOUSE_GAP = 8;
    // Seuils de réparation, larges pour ne jamais toucher une famille plausible.
    private static final int BAD_PARENT_GAP_MIN = 14;
    private static final int BAD_PARENT_GAP_MAX = 60;
    private static final int BAD_SPOUSE_GAP = 30;
    private static final int UNKNOWN_MAX = 75;
    private static final int OLDEST = 90;
    private static final int COMPONENT_LIMIT = 256;

    private FamilyAges() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> repair(server.getOverworld()));
    }

    /** Âge initial (en années) d'un villageois sans date de naissance, choisi dans [lo, hi] selon sa famille. */
    static int initialYears(ServerWorld world, UUID self, int lo, int hi, Random random) {
        long today = WorldCalendar.now(world).dayIndex();
        Family family = Family.around(world, self, today);
        int worldYears = worldYears(world, today);
        int s = family.index.getOrDefault(self, -1);
        if (s >= 0) {
            for (int i = 0; i < family.size(); i++) {
                if (family.age[i] >= 0) {
                    family.lo[i] = family.hi[i] = family.age[i];
                } else if (i == s) {
                    family.lo[i] = lo;
                    family.hi[i] = hi;
                } else {
                    family.lo[i] = 0;
                    family.hi[i] = UNKNOWN_MAX;
                }
            }
            if (family.capToWorld(worldYears)) {
                return family.lo[s] + random.nextInt(family.hi[s] - family.lo[s] + 1);
            }
        }
        int top = s >= 0 && family.bornInGame[s] ? Math.max(lo, Math.min(hi, worldYears)) : hi;
        return lo + random.nextInt(top - lo + 1);
    }

    /** Corrige les familles dont les âges sont impossibles (attribués avant cette correction). */
    public static void repair(ServerWorld world) {
        LifeRegistry life = LifeRegistry.get(world.getServer());
        long today = WorldCalendar.now(world).dayIndex();
        int worldYears = worldYears(world, today);
        Set<UUID> seen = new HashSet<>();
        int changed = 0;
        for (FamilyTreeNode node : new ArrayList<>(entries(world).values())) {
            if (seen.contains(node.id()) || life.getBirthDay(node.id()) == null) {
                continue;
            }
            Family family = Family.around(world, node.id(), today);
            seen.addAll(family.ids);
            if (family.inconsistent(worldYears)) {
                changed += family.fix(life, worldYears);
            }
        }
        if (changed > 0) {
            MCAVie.LOGGER.info("Âges incohérents avec la famille corrigés : {} villageois", changed);
        }
    }

    private static Map<UUID, FamilyTreeNode> entries(ServerWorld world) {
        return ((FamilyTreeAccessor) FamilyTree.get(world)).mcavie$getEntries();
    }

    /** Années écoulées depuis la création du monde (heure du jour ou compteur Time, le plus grand). */
    static int worldYears(ServerWorld world, long today) {
        long start = Math.min(0, today - world.getTime() / WorldCalendar.TICKS_PER_DAY);
        return (int) Math.floor(WorldCalendar.yearsBetween(start, today));
    }

    private static int[] stageRange(int years) {
        if (years < 2) {
            return new int[]{0, 1};
        } else if (years < 5) {
            return new int[]{2, 4};
        } else if (years < 13) {
            return new int[]{5, 12};
        } else if (years < 18) {
            return new int[]{13, 17};
        }
        return new int[]{18, Math.max(OLDEST, years)};
    }

    static final class Family {
        final List<UUID> ids = new ArrayList<>();
        final Map<UUID, Integer> index = new HashMap<>();
        final List<int[]> parentEdges = new ArrayList<>();
        final List<int[]> spouseEdges = new ArrayList<>();
        int[] age;
        long[] birth;
        boolean[] bornInGame;
        int[] lo;
        int[] hi;

        int size() {
            return ids.size();
        }

        /** Composante familiale vivante (villageois uniquement) autour de start. */
        static Family around(ServerWorld world, UUID start, long today) {
            Map<UUID, FamilyTreeNode> entries = entries(world);
            LifeRegistry life = LifeRegistry.get(world.getServer());
            Family f = new Family();
            ArrayDeque<UUID> queue = new ArrayDeque<>();
            if (member(entries, life, start)) {
                f.add(start);
                queue.add(start);
            }
            while (!queue.isEmpty() && f.size() < COMPONENT_LIMIT) {
                FamilyTreeNode node = entries.get(queue.poll());
                List<UUID> next = new ArrayList<>(node.children());
                next.add(node.father());
                next.add(node.mother());
                next.add(node.partner());
                for (UUID other : next) {
                    if (!f.index.containsKey(other) && member(entries, life, other) && f.size() < COMPONENT_LIMIT) {
                        f.add(other);
                        queue.add(other);
                    }
                }
            }
            int n = f.size();
            f.age = new int[n];
            f.birth = new long[n];
            f.bornInGame = new boolean[n];
            f.lo = new int[n];
            f.hi = new int[n];
            for (int i = 0; i < n; i++) {
                FamilyTreeNode node = entries.get(f.ids.get(i));
                Long birth = life.getBirthDay(node.id());
                f.birth[i] = birth == null ? 0 : birth;
                f.age[i] = birth == null ? -1 : Math.max(0, LifeCycle.ageInYears(birth, today));
                f.bornInGame[i] = realParent(entries, node.father()) || realParent(entries, node.mother());
                for (UUID parent : new UUID[]{node.father(), node.mother()}) {
                    Integer p = f.index.get(parent);
                    if (p != null) {
                        f.parentEdges.add(new int[]{p, i});
                    }
                }
                Integer partner = f.index.get(node.partner());
                if (partner != null && partner > i) {
                    f.spouseEdges.add(new int[]{i, partner});
                }
            }
            return f;
        }

        /** Famille construite à la main, pour les vérifications hors jeu. */
        static Family of(int[] ages, boolean[] bornInGame, int[][] parentEdges, int[][] spouseEdges) {
            Family f = new Family();
            int n = ages.length;
            for (int i = 0; i < n; i++) {
                f.add(new UUID(0, i));
            }
            f.age = ages.clone();
            f.birth = new long[n];
            f.bornInGame = bornInGame.clone();
            f.lo = new int[n];
            f.hi = new int[n];
            f.parentEdges.addAll(List.of(parentEdges));
            f.spouseEdges.addAll(List.of(spouseEdges));
            return f;
        }

        private void add(UUID uuid) {
            index.put(uuid, ids.size());
            ids.add(uuid);
        }

        private static boolean member(Map<UUID, FamilyTreeNode> entries, LifeRegistry life, UUID uuid) {
            FamilyTreeNode node = FamilyTreeNode.isValid(uuid) ? entries.get(uuid) : null;
            return node != null && !node.isPlayer() && !node.isDeceased() && life.getDeathDay(uuid) == null;
        }

        /** Parent véritable (joueur ou villageois), pas l'ascendant fictif que MCA crée au spawn. */
        private static boolean realParent(Map<UUID, FamilyTreeNode> entries, UUID uuid) {
            FamilyTreeNode node = FamilyTreeNode.isValid(uuid) ? entries.get(uuid) : null;
            return node != null && !node.probablyGenerated();
        }

        /** Resserre les bornes jusqu'au point fixe ; faux si les contraintes sont incompatibles. */
        boolean propagate() {
            int guard = 4 * size() + 16;
            boolean changed = true;
            while (changed && guard-- > 0) {
                changed = false;
                for (int[] e : parentEdges) {
                    changed |= raise(e[0], lo[e[1]] + MIN_PARENT_GAP) | lower(e[0], hi[e[1]] + MAX_PARENT_GAP)
                            | raise(e[1], lo[e[0]] - MAX_PARENT_GAP) | lower(e[1], hi[e[0]] - MIN_PARENT_GAP);
                }
                for (int[] e : spouseEdges) {
                    changed |= raise(e[0], lo[e[1]] - MAX_SPOUSE_GAP) | lower(e[0], hi[e[1]] + MAX_SPOUSE_GAP)
                            | raise(e[1], lo[e[0]] - MAX_SPOUSE_GAP) | lower(e[1], hi[e[0]] + MAX_SPOUSE_GAP);
                }
                for (int i = 0; i < size(); i++) {
                    if (lo[i] > hi[i]) {
                        return false;
                    }
                }
            }
            return !changed;
        }

        /**
         * Plafonne les PNJ nés en jeu à l'âge du monde, sans descendre sous le minimum imposé
         * par leurs descendants : deux générations nées en jeu ne peuvent pas avoir toutes deux 18 ans.
         */
        boolean capToWorld(int worldYears) {
            if (!propagate()) {
                return false;
            }
            for (int i = 0; i < size(); i++) {
                if (bornInGame[i]) {
                    hi[i] = Math.max(lo[i], Math.min(hi[i], worldYears));
                }
            }
            return propagate();
        }

        private boolean raise(int i, int value) {
            if (value > lo[i]) {
                lo[i] = value;
                return true;
            }
            return false;
        }

        private boolean lower(int i, int value) {
            if (value < hi[i]) {
                hi[i] = value;
                return true;
            }
            return false;
        }

        boolean inconsistent(int worldYears) {
            for (int[] e : parentEdges) {
                if (age[e[0]] >= 0 && age[e[1]] >= 0) {
                    int gap = age[e[0]] - age[e[1]];
                    if (gap < BAD_PARENT_GAP_MIN || gap > BAD_PARENT_GAP_MAX) {
                        return true;
                    }
                }
            }
            for (int[] e : spouseEdges) {
                if (age[e[0]] >= 0 && age[e[1]] >= 0 && Math.abs(age[e[0]] - age[e[1]]) > BAD_SPOUSE_GAP) {
                    return true;
                }
            }
            for (int i = 0; i < size(); i++) {
                if (bornInGame[i] && age[i] > Math.max(worldYears, stageRange(age[i])[0]) + 1) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Réattribue les âges de la famille en restant le plus près possible des âges actuels,
         * des aînés vers les cadets, sans changer le stade de croissance de chacun.
         */
        int fix(LifeRegistry life, int worldYears) {
            int[] chosen = solve(worldYears);
            if (chosen == null) {
                MCAVie.LOGGER.warn("Âges de la famille de {} impossibles à rendre cohérents", ids.get(0));
                return 0;
            }
            int changed = 0;
            for (int i = 0; i < size(); i++) {
                if (age[i] >= 0 && chosen[i] != age[i]) {
                    long shift = Math.round((age[i] - chosen[i]) * LifeCycle.DAYS_PER_YEAR);
                    life.setBirthDay(ids.get(i), WorldCalendar.gameDayOf(WorldCalendar.calendarDay(birth[i]) + shift));
                    changed++;
                }
            }
            return changed;
        }

        /** Nouveaux âges (-1 pour qui n'en a pas encore), ou null si la famille est insoluble. */
        int[] solve(int worldYears) {
            for (int i = 0; i < size(); i++) {
                int[] range = age[i] >= 0 ? stageRange(age[i]) : new int[]{0, UNKNOWN_MAX};
                lo[i] = range[0];
                hi[i] = range[1];
            }
            if (!capToWorld(worldYears)) {
                return null;
            }
            int[] depth = new int[size()];
            for (int pass = 0; pass < size(); pass++) {
                boolean changed = false;
                for (int[] e : parentEdges) {
                    if (depth[e[1]] < depth[e[0]] + 1 && depth[e[0]] + 1 <= size()) {
                        depth[e[1]] = depth[e[0]] + 1;
                        changed = true;
                    }
                }
                if (!changed) {
                    break;
                }
            }
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < size(); i++) {
                if (age[i] >= 0) {
                    order.add(i);
                }
            }
            order.sort(Comparator.<Integer>comparingInt(i -> depth[i]).thenComparingInt(i -> -age[i]));
            int[] chosen = new int[size()];
            java.util.Arrays.fill(chosen, -1);
            for (int i : order) {
                chosen[i] = Math.max(lo[i], Math.min(hi[i], age[i]));
                lo[i] = hi[i] = chosen[i];
                if (!propagate()) {
                    return null;
                }
            }
            return chosen;
        }
    }
}
