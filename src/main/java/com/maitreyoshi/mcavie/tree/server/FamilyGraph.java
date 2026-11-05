package com.maitreyoshi.mcavie.tree.server;

import com.maitreyoshi.mcavie.SurnameRegistry;
import com.maitreyoshi.mcavie.SurnameText;
import com.maitreyoshi.mcavie.life.LifeRegistry;
import com.maitreyoshi.mcavie.life.WellbeingRegistry;
import com.maitreyoshi.mcavie.phone.PortraitRegistry;
import com.maitreyoshi.mcavie.tree.FamilyPerson;
import com.maitreyoshi.mcavie.tree.RelationInfo;
import com.maitreyoshi.mcavie.tree.server.mixin.FamilyTreeAccessor;
import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkRegistry;
import net.mca.entity.ai.relationship.RelationshipState;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

public final class FamilyGraph {
    private static final UUID DEBUG_ENTRY = UUID.fromString("12341234-1234-1234-1234-123412341234");

    private static final int BLOOD_COST = 1;
    private static final int SPOUSE_COST = 3;
    private static final int MAX_ANCESTOR_DEPTH = 16;
    private static final int MAX_ANCESTOR_PATHS = 1500;
    private static final int MAX_ATTEMPTS = 5000;
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    private final Map<UUID, FamilyTreeNode> entries;
    private final SurnameRegistry surnames;
    private final SpouseRegistry spouseHistory;
    private final LifeRegistry life;
    private final WellbeingRegistry wellbeing;
    private final WorkRegistry work;
    private final PortraitRegistry portraits;
    private final Map<UUID, List<UUID>> spouseCache = new HashMap<>();

    public FamilyGraph(MinecraftServer server, FamilyTree tree) {
        this.entries = ((FamilyTreeAccessor) tree).mcavie$getEntries();
        this.surnames = SurnameRegistry.get(server);
        this.spouseHistory = SpouseRegistry.get(server);
        this.life = LifeRegistry.get(server);
        this.wellbeing = WellbeingRegistry.get(server);
        this.work = WorkRegistry.get(server);
        this.portraits = PortraitRegistry.get(server);
    }

    public Optional<FamilyTreeNode> node(@Nullable UUID id) {
        if (id == null || DEBUG_ENTRY.equals(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(entries.get(id));
    }

    public boolean contains(@Nullable UUID id) {
        return node(id).isPresent();
    }

    public List<FamilyTreeNode> all() {
        List<FamilyTreeNode> list = new ArrayList<>(entries.size());
        entries.forEach((id, node) -> {
            if (!DEBUG_ENTRY.equals(id)) {
                list.add(node);
            }
        });
        return list;
    }

    public List<UUID> parents(FamilyTreeNode node) {
        List<UUID> list = new ArrayList<>(2);
        if (contains(node.father())) {
            list.add(node.father());
        }
        if (contains(node.mother()) && !node.mother().equals(node.father())) {
            list.add(node.mother());
        }
        return list;
    }

    public List<UUID> children(FamilyTreeNode node) {
        List<UUID> list = new ArrayList<>();
        for (UUID child : node.children()) {
            if (contains(child) && !child.equals(node.id())) {
                list.add(child);
            }
        }
        return list;
    }

    public List<UUID> spouses(FamilyTreeNode node) {
        return spouseCache.computeIfAbsent(node.id(), id -> {
            Set<UUID> set = new LinkedHashSet<>();
            if (FamilyTreeNode.isValid(node.partner()) && node.getRelationshipState() != RelationshipState.SINGLE) {
                set.add(node.partner());
            }
            set.addAll(spouseHistory.get(id));
            for (UUID childId : node.children()) {
                node(childId).ifPresent(child -> {
                    if (id.equals(child.father())) {
                        set.add(child.mother());
                    } else if (id.equals(child.mother())) {
                        set.add(child.father());
                    }
                });
            }
            set.remove(id);
            set.removeIf(spouse -> !contains(spouse));
            return List.copyOf(set);
        });
    }

    public String surname(UUID id) {
        String surname = surnames.get(id);
        return surname == null ? "" : surname;
    }

    public String fullName(FamilyTreeNode node) {
        String surname = surname(node.id());
        if (surname.isEmpty()) {
            return node.getName();
        }
        return SurnameText.withSurname(node.getName(), SurnameText.display(surname, node.gender()));
    }

    public String firstName(FamilyTreeNode node) {
        String surname = surname(node.id());
        if (surname.isEmpty()) {
            return node.getName();
        }
        return SurnameText.firstName(node.getName(), SurnameText.display(surname, node.gender()));
    }

    private String professionKey(FamilyTreeNode node) {
        Job job = work.getJob(node.id());
        if (job == Job.RETIRED) {
            return work.retiredKey(node.id(), node.isDeceased());
        }
        return job != Job.NONE ? "job.mca_vie." + job.id() : "entity.minecraft.villager." + node.getProfessionName();
    }

    public FamilyPerson person(FamilyTreeNode node, @Nullable RelationInfo relationToViewer) {
        UUID id = node.id();
        WellbeingRegistry.Record record = wellbeing.get(id);
        return new FamilyPerson(
                id,
                fullName(node),
                surname(id),
                node.gender().ordinal(),
                node.isPlayer(),
                node.isDeceased(),
                node.father() == null ? Util.NIL_UUID : node.father(),
                node.mother() == null ? Util.NIL_UUID : node.mother(),
                spouses(node),
                children(node),
                professionKey(node),
                life.getBirthDay(id),
                node.isDeceased() ? life.getDeathDay(id) : null,
                record == null ? -1 : Math.round(record.happiness),
                portraits.get(id),
                relationToViewer,
                life.pastLives(id),
                life.exactBirth(id),
                node.isDeceased() ? life.exactDeath(id) : null
        );
    }

    private static final int NORMALIZED_MAX = 20000;
    private static final java.util.Map<String, String> NORMALIZED = new java.util.concurrent.ConcurrentHashMap<>();

    public static String normalize(@Nullable String text) {
        if (text == null) {
            return "";
        }
        String cached = NORMALIZED.get(text);
        if (cached != null) {
            return cached;
        }
        String normalized = MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT).trim();
        if (NORMALIZED.size() >= NORMALIZED_MAX) {
            NORMALIZED.clear();
        }
        NORMALIZED.put(text, normalized);
        return normalized;
    }

    public Paths shortestPaths(UUID from, int maxSteps, @Nullable UUID target) {
        Paths paths = new Paths(from);
        if (!contains(from)) {
            return paths;
        }

        record Visit(State state, int cost, int steps, long order) {
        }
        PriorityQueue<Visit> queue = new PriorityQueue<>((x, y) -> x.cost != y.cost ? Integer.compare(x.cost, y.cost)
                : x.steps != y.steps ? Integer.compare(x.steps, y.steps) : Long.compare(x.order, y.order));
        Map<State, Integer> best = new HashMap<>();
        Set<State> settled = new HashSet<>();
        long[] order = {0};

        State start = new State(from, false);
        best.put(start, 0);
        queue.add(new Visit(start, 0, 0, order[0]++));

        while (!queue.isEmpty()) {
            Visit visit = queue.poll();
            State state = visit.state;
            if (!settled.add(state)) {
                continue;
            }
            paths.reached.putIfAbsent(state.id, state);
            if (state.id.equals(target)) {
                break;
            }
            if (visit.steps >= maxSteps) {
                continue;
            }
            FamilyTreeNode node = entries.get(state.id);
            if (node == null) {
                continue;
            }

            List<Edge> edges = new ArrayList<>();
            if (!state.down) {
                parents(node).forEach(p -> edges.add(new Edge(new State(p, false), Kinship.Step.UP, BLOOD_COST)));
            }
            children(node).forEach(c -> edges.add(new Edge(new State(c, true), Kinship.Step.DOWN, BLOOD_COST)));
            spouses(node).forEach(s -> edges.add(new Edge(new State(s, false), Kinship.Step.SPOUSE, SPOUSE_COST)));

            for (Edge edge : edges) {
                int cost = visit.cost + edge.cost;
                if (settled.contains(edge.to) || cost >= best.getOrDefault(edge.to, Integer.MAX_VALUE)) {
                    continue;
                }
                best.put(edge.to, cost);
                paths.previous.put(edge.to, state);
                paths.stepTo.put(edge.to, edge.step);
                queue.add(new Visit(edge.to, cost, visit.steps + 1, order[0]++));
            }
        }
        return paths;
    }

    public record Link(List<UUID> nodes, List<Kinship.Step> steps) {
        private long spouseSteps() {
            return steps.stream().filter(s -> s == Kinship.Step.SPOUSE).count();
        }

        private List<UUID> key() {
            List<UUID> key = new ArrayList<>(nodes.size());
            for (int i = 0; i < nodes.size(); i++) {
                boolean apex = i > 0 && i < nodes.size() - 1
                        && steps.get(i - 1) == Kinship.Step.UP && steps.get(i) == Kinship.Step.DOWN;
                if (!apex) {
                    key.add(nodes.get(i));
                }
            }
            return key;
        }
    }

    public List<Link> links(UUID a, UUID b, int limit) {
        List<Link> result = new ArrayList<>();
        if (!contains(a) || !contains(b) || a.equals(b) || limit <= 0) {
            return result;
        }
        Set<List<UUID>> seen = new HashSet<>();
        Paths shortest = shortestPaths(a, Integer.MAX_VALUE, b);
        if (shortest.reached(b)) {
            Link first = new Link(shortest.nodes(b), shortest.steps(b));
            result.add(first);
            seen.add(first.key());
        }

        FamilyTreeNode nodeA = entries.get(a);
        FamilyTreeNode nodeB = entries.get(b);
        List<UUID> spousesA = spouses(nodeA);
        List<UUID> spousesB = spouses(nodeB);
        List<List<UUID[]>> groups = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        groups.get(0).add(new UUID[]{a, b});
        spousesB.forEach(y -> groups.get(1).add(new UUID[]{a, y}));
        spousesA.forEach(x -> groups.get(1).add(new UUID[]{x, b}));
        spousesA.forEach(x -> spousesB.forEach(y -> groups.get(2).add(new UUID[]{x, y})));

        Map<UUID, Map<UUID, List<List<List<UUID>>>>> ancestors = new HashMap<>();
        int[] attempts = {0};
        for (int g = 0; g < groups.size() && result.size() < limit; g++) {
            if (g == 1 && spousesA.contains(b)) {
                addLink(result, seen, new Link(List.of(a, b), List.of(Kinship.Step.SPOUSE)));
            }
            List<UUID[]> pairs = groups.get(g);
            pairs.removeIf(pair -> pair[0].equals(pair[1]) || pair[0].equals(b) || pair[1].equals(a));
            record Meeting(UUID[] pair, List<List<UUID>> fromX, List<List<UUID>> fromY) {
            }
            Map<Integer, List<Meeting>> byTotal = new TreeMap<>();
            for (UUID[] pair : pairs) {
                Map<UUID, List<List<List<UUID>>>> upX = ancestors.computeIfAbsent(pair[0], this::ancestorPaths);
                Map<UUID, List<List<List<UUID>>>> upY = ancestors.computeIfAbsent(pair[1], this::ancestorPaths);
                List<UUID> common = new ArrayList<>(upX.keySet());
                common.retainAll(upY.keySet());
                common.sort(Comparator.naturalOrder());
                for (UUID c : common) {
                    List<List<List<UUID>>> byX = upX.get(c);
                    List<List<List<UUID>>> byY = upY.get(c);
                    for (int i = 1; i < byX.size(); i++) {
                        for (int j = 1; j < byY.size(); j++) {
                            if (!byX.get(i).isEmpty() && !byY.get(j).isEmpty()) {
                                byTotal.computeIfAbsent(i + j, k -> new ArrayList<>()).add(new Meeting(pair, byX.get(i), byY.get(j)));
                            }
                        }
                    }
                }
            }
            for (List<Meeting> meetings : byTotal.values()) {
                for (Meeting meeting : meetings) {
                    for (List<UUID> pathX : meeting.fromX()) {
                        for (List<UUID> pathY : meeting.fromY()) {
                            if (result.size() >= limit || ++attempts[0] > MAX_ATTEMPTS) {
                                return result;
                            }
                            Link link = join(a, b, meeting.pair()[0], meeting.pair()[1], pathX, pathY);
                            if (link != null) {
                                addLink(result, seen, link);
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    private static void addLink(List<Link> result, Set<List<UUID>> seen, Link link) {
        if (seen.add(link.key())) {
            result.add(link);
        }
    }

    private Map<UUID, List<List<List<UUID>>>> ancestorPaths(UUID start) {
        Map<UUID, List<List<List<UUID>>>> map = new HashMap<>();
        List<UUID> path = new ArrayList<>();
        path.add(start);
        int[] count = {0};
        collectAncestors(path, map, count);
        return map;
    }

    private void collectAncestors(List<UUID> path, Map<UUID, List<List<List<UUID>>>> map, int[] count) {
        UUID id = path.get(path.size() - 1);
        List<List<List<UUID>>> byLength = map.computeIfAbsent(id, k -> new ArrayList<>());
        while (byLength.size() <= path.size()) {
            byLength.add(new ArrayList<>());
        }
        byLength.get(path.size()).add(List.copyOf(path));
        if (++count[0] >= MAX_ANCESTOR_PATHS || path.size() > MAX_ANCESTOR_DEPTH) {
            return;
        }
        FamilyTreeNode node = entries.get(id);
        if (node == null) {
            return;
        }
        for (UUID parent : parents(node)) {
            if (path.contains(parent) || count[0] >= MAX_ANCESTOR_PATHS) {
                continue;
            }
            path.add(parent);
            collectAncestors(path, map, count);
            path.remove(path.size() - 1);
        }
    }

    @Nullable
    private Link join(UUID a, UUID b, UUID x, UUID y, List<UUID> pathX, List<UUID> pathY) {
        List<UUID> nodes = new ArrayList<>();
        List<Kinship.Step> steps = new ArrayList<>();
        if (!x.equals(a)) {
            nodes.add(a);
            steps.add(Kinship.Step.SPOUSE);
        }
        nodes.addAll(pathX);
        for (int i = 1; i < pathX.size(); i++) {
            steps.add(Kinship.Step.UP);
        }
        for (int i = pathY.size() - 2; i >= 0; i--) {
            nodes.add(pathY.get(i));
            steps.add(Kinship.Step.DOWN);
        }
        if (!y.equals(b)) {
            nodes.add(b);
            steps.add(Kinship.Step.SPOUSE);
        }
        if (new HashSet<>(nodes).size() != nodes.size()) {
            return null;
        }
        int n = nodes.size();
        if (!x.equals(a) && steps.size() > 1 && steps.get(1) == Kinship.Step.DOWN && isParent(a, nodes.get(2))) {
            return null;
        }
        if (!y.equals(b) && n >= 3 && steps.get(steps.size() - 2) == Kinship.Step.UP && isParent(b, nodes.get(n - 3))) {
            return null;
        }
        return new Link(nodes, steps);
    }

    private boolean isParent(UUID parent, UUID child) {
        return node(child).map(n -> parents(n).contains(parent)).orElse(false);
    }

    private record State(UUID id, boolean down) {
    }

    private record Edge(State to, Kinship.Step step, int cost) {
    }

    public final class Paths {
        private final UUID from;
        private final Map<UUID, State> reached = new HashMap<>();
        private final Map<State, State> previous = new HashMap<>();
        private final Map<State, Kinship.Step> stepTo = new HashMap<>();

        private Paths(UUID from) {
            this.from = from;
        }

        public boolean reached(UUID id) {
            return reached.containsKey(id);
        }

        public List<UUID> nodes(UUID id) {
            State state = reached.get(id);
            if (state == null) {
                return List.of();
            }
            List<UUID> list = new ArrayList<>();
            for (State s = state; s != null; s = previous.get(s)) {
                list.add(s.id);
            }
            Collections.reverse(list);
            return list;
        }

        public List<Kinship.Step> steps(UUID id) {
            State state = reached.get(id);
            if (state == null) {
                return List.of();
            }
            List<Kinship.Step> list = new ArrayList<>();
            for (State s = state; previous.containsKey(s); s = previous.get(s)) {
                list.add(stepTo.get(s));
            }
            Collections.reverse(list);
            return list;
        }

        @Nullable
        public RelationInfo relation(UUID id) {
            if (from.equals(id)) {
                return Kinship.SELF;
            }
            if (!reached(id)) {
                return null;
            }
            return Kinship.classify(nodes(id), steps(id), FamilyGraph.this::node);
        }
    }
}
