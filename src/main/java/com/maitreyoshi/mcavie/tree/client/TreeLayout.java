package com.maitreyoshi.mcavie.tree.client;

import com.maitreyoshi.mcavie.tree.FamilyPerson;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.ToIntFunction;

public final class TreeLayout {
    public static final int PADDING = 5;
    public static final int LINE_HEIGHT = 10;
    public static final int ICON_SPACE = 12;
    public static final int COUPLE_LINE_Y = 9;
    public static final int PORTRAIT = 28;
    public static final int PORTRAIT_GAP = 4;
    private static final int MIN_CARD_WIDTH = 64;
    private static final int MAX_TEXT_WIDTH = 130;
    private static final int CARD_GAP = 10;
    private static final int FAMILY_GAP = 22;
    private static final int COUPLE_GAP = 20;
    private static final int ROW_GAP = 38;
    private static final int PASSES = 5;
    private static final int MAX_STARTS = 24;
    private static final int LINK_STEP = 10;
    private static final int REMOTE_LINE_Y = 3;
    private static final int MAX_PERMUTED = 7;
    private static final int MAX_ANCESTOR_CARDS = 25000;
    private static final int MAX_COPY_DEPTH = 32;
    private static final int BAR_TOP = 10;
    private static final int BAR_BOTTOM = 8;
    private static final int BAR_STEP = 5;
    private static final int BAR_MARGIN = 6;
    private static final int IMPROVE_PASSES = 2;
    private static final int SIFT_WINDOW = 6;
    private static final int BAR_GAP = 12;

    public static final int LINE_COLOR = 0xFFC8C8C8;
    public static final int WIDOW_COLOR = 0xFF9A9A9A;
    public static final int DIM_COLOR = 0xFF6E6E6E;

    public static final TreeLayout EMPTY = new TreeLayout();

    public final List<Card> cards = new ArrayList<>();
    public final Map<UUID, Card> byId = new HashMap<>();
    public final List<Segment> segments = new ArrayList<>();
    public final List<Mark> marks = new ArrayList<>();
    @Nullable
    public Card root;
    public int minX;
    public int minY;
    public int maxX;
    public int maxY;
    boolean pedigree;
    String fallback = "";

    private TreeLayout() {
    }

    public boolean isEmpty() {
        return cards.isEmpty();
    }

    public static final class Card {
        public final FamilyPerson person;
        public final String[] lines;
        public final int[] colors;
        public final int width;
        public final int height;
        public boolean highlighted;
        public final boolean copy;
        int gen;
        int index;
        double x;
        int cx;
        public int y;

        private Card(FamilyPerson person, List<String> lines, List<Integer> colors, int width, int height) {
            this.person = person;
            this.lines = lines.toArray(new String[0]);
            this.colors = colors.stream().mapToInt(Integer::intValue).toArray();
            this.width = width;
            this.height = height;
            this.copy = false;
        }

        private Card(Card original) {
            this.person = original.person;
            this.lines = original.lines;
            this.colors = original.colors;
            this.width = original.width;
            this.height = original.height;
            this.copy = true;
        }

        public UUID id() {
            return person.id();
        }

        public int centerX() {
            return cx;
        }

        public int left() {
            return cx - width / 2;
        }

        public int right() {
            return left() + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean contains(double worldX, double worldY) {
            return worldX >= left() && worldX < right() && worldY >= y && worldY < bottom();
        }
    }

    public record Segment(int x1, int y1, int x2, int y2, int color, boolean dashed) {
    }

    public record Mark(int x, int y, String icon) {
    }

    public static Card card(FamilyPerson person, TextRenderer font, long today) {
        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        int icon = person.deceased() ? ICON_SPACE : 0;

        lines.add(TreeTexts.trim(font, TreeTexts.name(person), MAX_TEXT_WIDTH - icon));
        colors.add(TreeTexts.nameColor(person.gender()));

        Text years = TreeTexts.yearsLine(person, today);
        if (years != null) {
            lines.add(TreeTexts.trim(font, years.getString(), MAX_TEXT_WIDTH));
            colors.add(0xDDDDDD);
        }
        String profession = TreeTexts.profession(person);
        if (!profession.isEmpty()) {
            lines.add(TreeTexts.trim(font, profession, MAX_TEXT_WIDTH));
            colors.add(0xAAAAAA);
        }
        if (com.maitreyoshi.mcavie.phone.client.PhoneSettings.treeRelations() && TreeTexts.isShownOnCard(person.relationToViewer())) {
            lines.add(TreeTexts.trim(font, TreeTexts.yourRelation(person.relationToViewer(), person.gender()), MAX_TEXT_WIDTH));
            colors.add(0xFFFF55);
        }

        return card(person, lines, colors, font::getWidth);
    }

    static Card card(FamilyPerson person, List<String> lines, List<Integer> colors, ToIntFunction<String> measure) {
        int icon = person.deceased() ? ICON_SPACE : 0;
        int width = measure.applyAsInt(lines.get(0)) + icon;
        for (int i = 1; i < lines.size(); i++) {
            width = Math.max(width, measure.applyAsInt(lines.get(i)));
        }
        int portrait = person.portrait() != null ? PORTRAIT + PORTRAIT_GAP : 0;
        width = Math.max(MIN_CARD_WIDTH, width + 2 * PADDING + portrait);
        int height = lines.size() * LINE_HEIGHT + 2 * PADDING - 2;
        if (portrait > 0) {
            height = Math.max(height, PORTRAIT + 8);
        }
        return new Card(person, lines, colors, width, height);
    }

    public static TreeLayout family(List<FamilyPerson> persons, UUID rootId, TextRenderer font, long today, int maxUp, int maxDown) {
        return family(persons, rootId, person -> card(person, font, today), maxUp, maxDown);
    }

    static TreeLayout family(List<FamilyPerson> persons, UUID rootId, Function<FamilyPerson, Card> cards, int maxUp, int maxDown) {
        if (persons.isEmpty()) {
            return new TreeLayout();
        }
        return new FamilyBuilder(persons, rootId, cards, maxUp, maxDown).build();
    }

    private static final class FamilyBuilder {
        private final TreeLayout layout = new TreeLayout();
        private final Map<UUID, FamilyPerson> persons = new LinkedHashMap<>();
        private final UUID rootId;
        private final Function<FamilyPerson, Card> cards;

        private final Map<UUID, List<UUID>> parents = new HashMap<>();
        private final Map<UUID, List<UUID>> children = new HashMap<>();
        private final Map<UUID, List<UUID>> partners = new HashMap<>();
        private final Map<UUID, Integer> generation = new HashMap<>();
        private final Map<UUID, UUID> clusterOf = new HashMap<>();
        private final Map<UUID, List<UUID>> clusters = new HashMap<>();

        private final TreeMap<Integer, List<Card>> rows = new TreeMap<>();
        private final Map<UUID, List<Card>> lines = new HashMap<>();
        private final Map<UUID, Integer> lineIds = new HashMap<>();
        private final List<Map<UUID, List<Port>>> ports = List.of(new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
        private final Map<Integer, List<Unit>> unitsByGen = new HashMap<>();
        private TreeMap<Integer, List<List<Card>>> current = new TreeMap<>();

        private final Map<String, Unit> units = new LinkedHashMap<>();
        private final Map<Card, Unit> unitOfChild = new HashMap<>();
        private final Map<Card, List<Unit>> unitsOfParent = new HashMap<>();

        private FamilyBuilder(List<FamilyPerson> list, UUID rootId, Function<FamilyPerson, Card> cards, int maxUp, int maxDown) {
            this.maxUp = maxUp;
            this.maxDown = maxDown;
            for (FamilyPerson person : list) {
                persons.putIfAbsent(person.id(), person);
            }
            this.rootId = rootId;
            this.cards = cards;
        }

        private static final class Unit {
            final List<Card> parents = new ArrayList<>();
            final List<Card> kids = new ArrayList<>();
            final List<Card> order = new ArrayList<>();
            final List<Card> births = new ArrayList<>();
            final BitSet homes = new BitSet();
            Card home;
        }

        private TreeLayout build() {
            readLinks();
            computeGenerations();
            computeClusters();
            for (FamilyPerson person : persons.values()) {
                Card card = cards.apply(person);
                card.gen = generation.get(person.id());
                card.highlighted = person.id().equals(rootId);
                layout.cards.add(card);
                layout.byId.put(person.id(), card);
            }
            layout.root = layout.byId.get(rootId);
            buildUnits();
            layout.pedigree = pedigree();
            if (layout.pedigree) {
                pedigreeConnectors();
            } else {
                orderRows();
                placeCards();
                buildConnectors();
            }
            computeBounds(layout);
            return layout;
        }

        private void readLinks() {
            for (FamilyPerson person : persons.values()) {
                if (person.hasFather()) {
                    link(person.father(), person.id());
                }
                if (person.hasMother()) {
                    link(person.mother(), person.id());
                }
            }
            for (FamilyPerson person : persons.values()) {
                for (UUID child : person.children()) {
                    link(person.id(), child);
                }
            }
            for (FamilyPerson person : persons.values()) {
                for (UUID spouse : person.spouses()) {
                    addPartner(person.id(), spouse);
                }
            }
            for (UUID id : persons.keySet()) {
                List<UUID> list = parentsOf(id);
                if (list.size() >= 2) {
                    addPartner(list.get(0), list.get(1));
                }
            }
        }

        private void link(UUID parent, UUID child) {
            if (parent.equals(child) || !persons.containsKey(parent) || !persons.containsKey(child)) {
                return;
            }
            List<UUID> list = parents.computeIfAbsent(child, k -> new ArrayList<>());
            if (list.contains(parent) || list.size() >= 2) {
                return;
            }
            list.add(parent);
            children.computeIfAbsent(parent, k -> new ArrayList<>()).add(child);
        }

        private void addPartner(UUID a, UUID b) {
            if (a.equals(b) || !persons.containsKey(a) || !persons.containsKey(b)) {
                return;
            }
            List<UUID> list = partners.computeIfAbsent(a, k -> new ArrayList<>());
            if (!list.contains(b)) {
                list.add(b);
                partners.computeIfAbsent(b, k -> new ArrayList<>()).add(a);
            }
        }

        private List<UUID> parentsOf(UUID id) {
            return parents.getOrDefault(id, List.of());
        }

        private List<UUID> childrenOf(UUID id) {
            return children.getOrDefault(id, List.of());
        }

        private List<UUID> partnersOf(UUID id) {
            return partners.getOrDefault(id, List.of());
        }

        private boolean arePartners(Card a, Card b) {
            return partnersOf(a.id()).contains(b.id());
        }

        private void computeGenerations() {
            UUID start = persons.containsKey(rootId) ? rootId : persons.keySet().iterator().next();
            spread(start);
            for (UUID id : persons.keySet()) {
                if (!generation.containsKey(id)) {
                    spread(id);
                }
            }
        }

        private void spread(UUID start) {
            ArrayDeque<UUID> queue = new ArrayDeque<>();
            generation.put(start, 0);
            queue.add(start);
            while (!queue.isEmpty()) {
                UUID id = queue.poll();
                int gen = generation.get(id);
                for (UUID parent : parentsOf(id)) {
                    if (generation.putIfAbsent(parent, gen - 1) == null) {
                        queue.add(parent);
                    }
                }
                for (UUID child : childrenOf(id)) {
                    if (generation.putIfAbsent(child, gen + 1) == null) {
                        queue.add(child);
                    }
                }
                for (UUID partner : partnersOf(id)) {
                    if (generation.putIfAbsent(partner, gen) == null) {
                        queue.add(partner);
                    }
                }
            }
        }

        private void computeClusters() {
            for (UUID id : persons.keySet()) {
                clusterOf.put(id, id);
            }
            for (UUID id : persons.keySet()) {
                for (UUID partner : partnersOf(id)) {
                    if (generation.get(id).equals(generation.get(partner))) {
                        UUID a = find(id);
                        UUID b = find(partner);
                        if (!a.equals(b)) {
                            clusterOf.put(b, a);
                        }
                    }
                }
            }
            for (UUID id : persons.keySet()) {
                clusters.computeIfAbsent(find(id), k -> new ArrayList<>()).add(id);
            }
        }

        private UUID find(UUID id) {
            UUID parent = clusterOf.get(id);
            if (parent.equals(id)) {
                return id;
            }
            UUID top = find(parent);
            clusterOf.put(id, top);
            return top;
        }

        private List<UUID> clusterMembers(UUID id) {
            return clusters.get(find(id));
        }

        private static final class NotTree extends RuntimeException {
            NotTree(String reason) {
                super(reason, null, false, false);
            }
        }

        private final class Shape {
            final List<Card> members = new ArrayList<>();
            final Map<Integer, Double> lo = new HashMap<>();
            final Map<Integer, Double> hi = new HashMap<>();
            final Map<Integer, Card> loCard = new HashMap<>();
            final Map<Integer, Card> hiCard = new HashMap<>();

            void add(Card card) {
                members.add(card);
                edge(2 * card.gen, card.x - card.width / 2.0, card.x + card.width / 2.0, card, card);
            }

            void span(int level, double from, double to) {
                edge(level, from, to, null, null);
            }

            private void edge(int level, double from, double to, @Nullable Card left, @Nullable Card right) {
                Double l = lo.get(level);
                if (l == null || from < l) {
                    lo.put(level, from);
                    loCard.put(level, left);
                }
                Double h = hi.get(level);
                if (h == null || to > h) {
                    hi.put(level, to);
                    hiCard.put(level, right);
                }
            }

            void absorb(Shape other) {
                members.addAll(other.members);
                for (int level : other.lo.keySet()) {
                    edge(level, other.lo.get(level), other.hi.get(level), null, null);
                    if (lo.get(level).equals(other.lo.get(level))) {
                        loCard.put(level, other.loCard.get(level));
                    }
                    if (hi.get(level).equals(other.hi.get(level))) {
                        hiCard.put(level, other.hiCard.get(level));
                    }
                }
            }

            void shift(double dx) {
                if (dx == 0) {
                    return;
                }
                members.forEach(c -> c.x += dx);
                lo.replaceAll((k, v) -> v + dx);
                hi.replaceAll((k, v) -> v + dx);
            }
        }

        private double need(Shape left, Shape right) {
            double shift = -Double.MAX_VALUE;
            for (int level : right.lo.keySet()) {
                Double edge = left.hi.get(level);
                if (edge == null) {
                    continue;
                }
                double gap = BAR_GAP;
                if ((level & 1) == 0) {
                    Card a = left.hiCard.get(level);
                    Card b = right.loCard.get(level);
                    gap = a != null && b != null ? separation(a, b) - a.width / 2.0 - b.width / 2.0 : FAMILY_GAP;
                }
                shift = Math.max(shift, edge + gap - right.lo.get(level));
            }
            return shift == -Double.MAX_VALUE ? 0 : shift;
        }

        private record Couple(List<Card> row, Card hub, List<Card> spouses) {
        }

        private record Brood(List<Card> parents, List<Card> kids) {
        }

        private record Group(Shape shape, List<Card> kids) {
        }

        private record Half(Card parent, @Nullable Card mate, Group group) {
        }

        private record Pair(Card a, Card b) {
        }

        private final Set<Card> claimed = new HashSet<>();
        private final Set<Unit> consumed = new HashSet<>();
        private final Set<Card> blood = new HashSet<>();
        private final Set<Card> walked = new HashSet<>();
        private final Map<Unit, List<Card>> bloodKids = new HashMap<>();
        private final List<Card> copies = new ArrayList<>();
        private final List<Couple> couples = new ArrayList<>();
        private final List<Brood> broods = new ArrayList<>();
        private final Map<Card, Brood> broodOf = new HashMap<>();
        private final Map<Unit, Set<Card>> drawn = new HashMap<>();
        private final Set<String> linked = new HashSet<>();
        private final Map<Card, Shape> ancestry = new HashMap<>();
        private double maxSpread;
        @Nullable
        private Card betweenCard;
        private double[] betweenEdge;
        private int copyDepth;
        private final Set<Unit> inProgress = new HashSet<>();
        private final Set<Card> ancestors = new HashSet<>();
        private final int maxUp;
        private final int maxDown;

        private Card cardOf(UUID id) {
            return layout.byId.get(id);
        }

        private Card original(Card card) {
            return card.copy ? cardOf(card.id()) : card;
        }

        private Card occurrence(Card card, int gen) {
            Card result = claimed.add(card) ? card : new Card(card);
            if (result.copy) {
                copies.add(result);
            }
            result.gen = gen;
            result.x = 0;
            return result;
        }

        private Card copyOf(Card card, int gen) {
            Card result = new Card(card);
            copies.add(result);
            result.gen = gen;
            return result;
        }

        private List<Card> kidsOf(Unit unit) {
            return bloodKids.get(unit);
        }

        private void findBlood(Card root) {
            ArrayDeque<Card> todo = new ArrayDeque<>(List.of(root));
            while (!todo.isEmpty()) {
                Card card = todo.poll();
                if (blood.add(card)) {
                    parentsOf(card.id()).forEach(id -> todo.add(cardOf(id)));
                }
            }
            ancestors.addAll(blood);
            ArrayDeque<Card> walk = new ArrayDeque<>(List.of(root));
            for (UUID parent : parentsOf(root.id())) {
                childrenOf(parent).forEach(id -> walk.add(cardOf(id)));
            }
            Set<Card> seen = new HashSet<>();
            while (!walk.isEmpty()) {
                Card card = walk.poll();
                if (seen.add(card)) {
                    blood.add(card);
                    walked.add(card);
                    childrenOf(card.id()).forEach(id -> walk.add(cardOf(id)));
                }
            }
            for (Unit unit : units.values()) {
                List<Card> kids = new ArrayList<>();
                for (Card kid : unit.kids) {
                    if (blood.contains(kid)) {
                        kids.add(kid);
                    }
                }
                bloodKids.put(unit, kids);
            }
        }

        private boolean pedigree() {
            Card root = layout.root;
            if (root == null) {
                return false;
            }
            Map<Card, Integer> gens = new HashMap<>();
            layout.cards.forEach(c -> gens.put(c, c.gen));
            for (Card card : layout.cards) {
                maxSpread += 2 * (card.width + FAMILY_GAP);
            }
            boolean ok;
            try {
                findBlood(root);
                copyDepth = copyDepth(root);
                rootRow(root);
                validate();
                ok = true;
            } catch (NotTree e) {
                ok = false;
                layout.fallback = e.getMessage();
            }
            if (ok) {
                // Limite de générations : celui qu'on n'atteint que plus bas que la fenêtre n'est pas dessiné.
                layout.cards.removeIf(card -> !claimed.contains(card));
                List<Card> all = new ArrayList<>(layout.cards);
                all.addAll(copies);
                for (Card card : all) {
                    rows.computeIfAbsent(card.gen, g -> new ArrayList<>()).add(card);
                }
                for (List<Card> row : rows.values()) {
                    row.sort(Comparator.comparingDouble(c -> c.x));
                    for (int i = 0; i < row.size(); i++) {
                        row.get(i).index = i;
                        if (i > 0 && row.get(i).x - row.get(i - 1).x < (row.get(i).width + row.get(i - 1).width) / 2.0 + 1) {
                            ok = false;
                            layout.fallback = "overlap";
                        }
                    }
                }
            }
            if (!ok) {
                rows.clear();
                gens.forEach((c, g) -> c.gen = g);
                return false;
            }
            layout.cards.addAll(copies);
            double shift = root.x;
            for (Card card : layout.cards) {
                card.cx = (int) Math.round(card.x - shift);
            }
            snap(root);
            return true;
        }

        private void validate() {
            if (maxDown < 0 && claimed.size() != layout.cards.size()) {
                throw new NotTree("cards");
            }
            for (Unit unit : units.values()) {
                if (maxDown >= 0 && !consumed.contains(unit)) {
                    continue;
                }
                Set<Card> shown = drawn.getOrDefault(unit, Set.of());
                for (Card kid : kidsOf(unit)) {
                    // Avec une limite de générations, un ancêtre au bord de la fenêtre garde ses parents hors champ.
                    if (!shown.contains(kid) && !(maxUp >= 0 && blood.contains(kid))) {
                        throw new NotTree("kids");
                    }
                }
            }
            for (Card card : walked) {
                if (maxDown >= 0 && !claimed.contains(card)) {
                    continue;
                }
                for (UUID id : partnersOf(card.id())) {
                    if (!linked.contains(pairKey(card, cardOf(id)))) {
                        throw new NotTree("partners: " + card.person.name() + " / " + cardOf(id).person.name());
                    }
                }
            }
        }

        private void brood(Unit unit, List<Card> parents, List<Card> kids) {
            Brood brood = new Brood(parents, kids);
            broods.add(brood);
            for (Card kid : kids) {
                broodOf.put(kid, brood);
                drawn.computeIfAbsent(unit, u -> new HashSet<>()).add(original(kid));
            }
        }

        private void couple(List<Card> row, Card hub, List<Card> spouses) {
            couples.add(new Couple(row, hub, spouses));
            for (Card spouse : spouses) {
                linked.add(pairKey(hub, spouse));
            }
        }

        private List<Card> parentPair(Card child, Unit unit) {
            if (unit.parents.size() > 2) {
                throw new NotTree("parents");
            }
            List<Card> pair = new ArrayList<>(unit.parents);
            if (pair.size() == 2) {
                Card a = pair.get(0);
                Card b = pair.get(1);
                boolean bFather = b.id().equals(child.person.father()) || !a.id().equals(child.person.father())
                        && TreeTexts.isFemale(a.person.gender()) && !TreeTexts.isFemale(b.person.gender());
                if (bFather) {
                    Collections.reverse(pair);
                }
            }
            return pair;
        }

        private double coupleDistance(Shape father, Card f, Shape mother, Card m) {
            father.shift(-f.x);
            mother.shift(-m.x);
            return need(father, mother);
        }

        private void placeCouple(Shape father, Card f, Shape mother, Card m, double distance, double anchor) {
            double t = anchor - (f.width / 2.0 + distance - m.width / 2.0) / 2;
            father.shift(t - f.x);
            mother.shift(t + distance - m.x);
        }

        private List<Card> parentsAbove(Card child, Unit unit) {
            List<Card> list = new ArrayList<>();
            for (Card parent : parentPair(child, unit)) {
                list.add(occurrence(parent, child.gen - 1));
            }
            if (list.size() == 2) {
                couple(list, list.get(0), List.of(list.get(1)));
            }
            return list;
        }

        // Un ancêtre qui revient (implexe) montre aussi ses parents à chaque apparition,
        // sur autant de générations que le plafond de cartes le permet.
        private int copyDepth(Card root) {
            int best = 0;
            for (int depth = 1; depth <= MAX_COPY_DEPTH; depth++) {
                int[] count = {0};
                countAncestors(root.id(), 0, depth, depth, new HashSet<>(), count);
                if (count[0] > MAX_ANCESTOR_CARDS) {
                    break;
                }
                best = depth;
            }
            return best;
        }

        private void countAncestors(UUID id, int level, int left, int depth, Set<UUID> seen, int[] count) {
            if (++count[0] > MAX_ANCESTOR_CARDS) {
                return;
            }
            boolean copy = !seen.add(id);
            if (copy && left <= 0 || maxUp >= 0 && level >= maxUp) {
                return;
            }
            for (UUID parent : parentsOf(id)) {
                countAncestors(parent, level + 1, copy ? left - 1 : depth, depth, seen, count);
            }
        }

        private Shape anc(Card card) {
            return anc(card, copyDepth);
        }

        private Shape anc(Card card, int left) {
            card.x = 0;
            Shape shape = new Shape();
            shape.add(card);
            ancestry.put(card, shape);
            Unit unit = maxUp >= 0 && -card.gen >= maxUp ? null : unitOfChild.get(original(card));
            if (unit != null && card.copy && left <= 0 && claimed.containsAll(unit.parents)
                    && drawn.getOrDefault(unit, Set.of()).contains(original(card))) {
                unit = null;
            }
            if (unit == null) {
                return shape;
            }
            List<Card> pair = parentsAbove(card, unit);
            brood(unit, pair, List.of(card));
            int next = card.copy ? left - 1 : copyDepth;
            if (pair.size() == 1) {
                Shape above = anc(pair.get(0), next);
                shape.absorb(above);
            } else {
                Card f = pair.get(0);
                Card m = pair.get(1);
                Shape a = anc(f, next);
                Shape b = anc(m, next);
                placeCouple(a, f, b, m, coupleDistance(a, f, b, m), 0);
                shape.absorb(a);
                shape.absorb(b);
            }
            return shape;
        }

        private void rootRow(Card root) {
            Unit full = unitOfChild.get(root);
            if (full == null) {
                block(occurrence(root, 0));
                return;
            }
            root.gen = 0;
            List<Card> pair = parentsAbove(root, full);
            Set<Unit> family = new LinkedHashSet<>();
            for (Card parent : pair) {
                family.addAll(unitsOfParent.getOrDefault(original(parent), List.of()));
            }
            List<Unit> fatherSide = new ArrayList<>();
            List<Unit> motherSide = new ArrayList<>();
            for (Unit unit : family) {
                if (kidsOf(unit).isEmpty()) {
                    continue;
                }
                if (!consumed.add(unit)) {
                    throw new NotTree("rootUnits");
                }
                if (unit == full) {
                    continue;
                }
                if (unit.parents.contains(original(pair.get(0)))) {
                    fatherSide.add(unit);
                } else if (pair.size() == 2 && unit.parents.contains(original(pair.get(1)))) {
                    motherSide.add(unit);
                } else {
                    throw new NotTree("rootHalf");
                }
            }
            Group middle = group(full, 0, pair);
            // La fratrie est centrée sous le couple, pas sur l'enfant affiché : sinon, plus il est loin dans la fratrie, plus le parent s'éloigne.
            middle.shape.shift(-(kidLow(middle) + kidHigh(middle)) / 2);
            List<Half> ofFather = halves(fatherSide, pair.get(0));
            List<Half> ofMother = pair.size() == 2 ? halves(motherSide, pair.get(1)) : List.of();
            if (pair.size() == 1) {
                Card parent = pair.get(0);
                placeHalves(ofFather, parent, parent.id().equals(root.person.father()) ? 1 : -1, middle, anc(parent));
                return;
            }
            // Comme dans la vue descendants : les autres épouses à droite de l'homme, les autres maris à gauche de la femme.
            // Les autres épouses du père, quel que soit l'ordre des mariages, s'intercalent entre le père et elle (sauf si la mère a aussi un autre mari : les lignes se croiseraient).
            List<Half> between = new ArrayList<>();
            for (Half half : ofFather) {
                if (half.mate != null && ofMother.stream().allMatch(h -> h.mate == null)) {
                    between.add(half);
                }
            }
            List<Half> outer = new ArrayList<>(ofFather);
            outer.removeAll(between);
            List<Half> left = sideHalves(ofFather, ofMother);
            List<Half> right = sideHalves(ofMother, outer);
            Card f = pair.get(0);
            Card m = pair.get(1);
            Shape a = anc(f);
            Shape b = anc(m);
            double distance = coupleDistance(a, f, b, m);
            if (!between.isEmpty()) {
                // Épouse intercalée : le couple descend au-dessus de l'aîné, sinon le père serait repoussé à gauche de toute la fratrie.
                middle.shape.shift(-kidLow(middle));
                placeBetween(between, f, a, m, b, distance, !left.isEmpty() && left.get(0).mate == null, !right.isEmpty() && right.get(0).mate == null, middle);
                double[] state = betweenEdge;
                placeHalves(left, f, -1, middle, a, state[0], state[1]);
                placeHalves(right, m, 1, middle, b, kidHigh(middle), Double.NaN);
                return;
            }
            if (!left.isEmpty() && left.get(0).mate == null) {
                distance = Math.max(distance, -2 * (kidLow(middle) - BAR_GAP) - f.width / 2.0 + m.width / 2.0);
            }
            if (!right.isEmpty() && right.get(0).mate == null) {
                distance = Math.max(distance, 2 * (kidHigh(middle) + BAR_GAP) + f.width / 2.0 - m.width / 2.0);
            }
            placeCouple(a, f, b, m, distance, 0);
            placeHalves(left, f, -1, middle, a);
            placeHalves(right, m, 1, middle, b);
        }

        // Demi-frères et sœurs : l'enfant sans autre parent connu en premier, puis les autres conjoints dans l'ordre des mariages.
        private List<Half> halves(List<Unit> list, Card parent) {
            List<UUID> order = marriageOrder(parent.id(), partnersOf(parent.id()));
            List<Unit> sorted = new ArrayList<>(list);
            sorted.sort(Comparator.comparingInt(unit -> {
                Card other = otherParent(unit, parent);
                return other == null ? -1 : order.indexOf(other.id());
            }));
            List<Half> result = new ArrayList<>();
            List<Card> mates = new ArrayList<>();
            for (Unit unit : sorted) {
                Card other = otherParent(unit, parent);
                Card mate = null;
                if (other != null) {
                    mate = blood.contains(other) || claimed.contains(other) ? copyOf(other, parent.gen) : occurrence(other, parent.gen);
                    mates.add(mate);
                }
                result.add(new Half(parent, mate, group(unit, parent.gen + 1, mate == null ? List.of(parent) : List.of(parent, mate))));
            }
            if (!mates.isEmpty()) {
                List<Card> row = new ArrayList<>(mates);
                row.add(parent);
                couple(row, parent, mates);
            }
            return result;
        }

        private static List<Half> sideHalves(List<Half> solo, List<Half> mates) {
            List<Half> result = new ArrayList<>();
            for (Half half : solo) {
                if (half.mate == null) {
                    result.add(half);
                }
            }
            for (Half half : mates) {
                if (half.mate != null) {
                    result.add(half);
                }
            }
            return result;
        }

        @Nullable
        private Card otherParent(Unit unit, Card parent) {
            for (Card card : unit.parents) {
                if (card != original(parent)) {
                    return card;
                }
            }
            return null;
        }

        private void placeBetween(List<Half> between, Card f, Shape a, Card m, Shape b, double distance, boolean fatherSolo, boolean motherSolo, Group middle) {
            Card closest = between.get(between.size() - 1).mate;
            betweenCard = closest;
            double gap = COUPLE_GAP / 2.0;
            if (motherSolo) {
                gap = Math.max(gap, kidHigh(middle) + BAR_GAP - m.width / 2.0);
            }
            double motherX = gap + m.width / 2.0;
            closest.x = -(gap + closest.width / 2.0);
            Shape placed = middle.shape;
            double edge = kidLow(middle);
            double previous = Double.NaN;
            double fatherX = 0;
            for (int i = between.size() - 1; i >= 0; i--) {
                Half half = between.get(i);
                Group group = half.group;
                group.shape.shift(-need(group.shape, placed));
                if (!Double.isNaN(previous)) {
                    group.shape.shift(-Math.max(0, kidHigh(group) - previous + BAR_GAP));
                }
                Card mate = half.mate;
                Card neighbor = i > 0 ? between.get(i - 1).mate : f;
                double x = mate.x - separation(neighbor, mate);
                double anchor = (x + neighbor.width / 2.0 + mate.x - mate.width / 2.0) / 2;
                double over = anchor - edge + BAR_GAP;
                if (over > 0) {
                    x -= 2 * over + 1;
                }
                if (i == 0) {
                    x = Math.min(x, motherX - distance);
                    if (fatherSolo) {
                        x = Math.min(x, kidLow(group) - BAR_GAP);
                    }
                    fatherX = x;
                } else {
                    neighbor.x = x;
                }
                anchor = (x + neighbor.width / 2.0 + mate.x - mate.width / 2.0) / 2;
                placed.absorb(group.shape);
                edge = kidLow(group);
                previous = anchor;
            }
            a.shift(fatherX - f.x);
            b.shift(motherX - m.x);
            for (Half half : between) {
                a.add(half.mate);
            }
            betweenEdge = new double[]{edge, previous};
        }

        private void placeHalves(List<Half> list, Card parent, int dir, Group middle, Shape line) {
            placeHalves(list, parent, dir, middle, line, dir < 0 ? kidLow(middle) : kidHigh(middle), Double.NaN);
        }

        private void placeHalves(List<Half> list, Card parent, int dir, Group middle, Shape line, double startEdge, double startPrevious) {
            Shape placed = middle.shape;
            double edge = startEdge;
            double previous = startPrevious;
            Card last = parent;
            for (Half half : list) {
                Group group = half.group;
                group.shape.shift(dir < 0 ? -need(group.shape, placed) : need(placed, group.shape));
                if (!Double.isNaN(previous)) {
                    double inner = dir < 0 ? kidHigh(group) : kidLow(group);
                    group.shape.shift(dir * Math.max(0, dir * (previous - inner) + BAR_GAP));
                }
                double anchor = half.parent.x;
                if (half.mate != null) {
                    Card mate = half.mate;
                    mate.x = last.x + dir * (dir < 0 ? separation(mate, last) : separation(last, mate));
                    anchor = gapAnchor(last, mate, dir);
                    double over = dir * (edge - anchor) + BAR_GAP;
                    if (over > 0) {
                        mate.x += dir * (2 * over + 1);
                        anchor = gapAnchor(last, mate, dir);
                    }
                    line.add(mate);
                    last = mate;
                }
                placed.absorb(group.shape);
                edge = dir < 0 ? kidLow(group) : kidHigh(group);
                previous = anchor;
            }
        }

        private static double gapAnchor(Card inner, Card outer, int dir) {
            return (inner.x + dir * inner.width / 2.0 + outer.x - dir * outer.width / 2.0) / 2;
        }

        private Group group(Unit unit, int gen, List<Card> parents) {
            List<Card> kids = new ArrayList<>(kidsOf(unit));
            kids.sort(Comparator.comparing(Card::id, birthOrder()));
            Shape acc = new Shape();
            List<Card> placed = new ArrayList<>();
            for (Card kid : kids) {
                Shape shape;
                Card occ = occurrence(kid, gen);
                if (occ.copy && !replayable(occ)) {
                    shape = new Shape();
                    shape.add(occ);
                } else {
                    shape = block(occ);
                }
                placed.add(occ);
                if (!acc.members.isEmpty()) {
                    shape.shift(need(acc, shape));
                }
                acc.absorb(shape);
            }
            brood(unit, parents, placed);
            return new Group(acc, placed);
        }

        // Deux descendants mariés entre eux : les enfants sont dessinés sous chacune de leurs deux lignées.
        private boolean mayRepeat(Card card) {
            return !ancestors.contains(card) && copies.size() < MAX_ANCESTOR_CARDS;
        }

        private boolean replayable(Card copy) {
            Card card = original(copy);
            if (!mayRepeat(card)) {
                return false;
            }
            return unitsOfParent.getOrDefault(card, List.of()).stream().anyMatch(unit -> !kidsOf(unit).isEmpty());
        }

        private static double kidLow(Group group) {
            double lo = Double.MAX_VALUE;
            for (Card kid : group.kids) {
                lo = Math.min(lo, kid.x);
            }
            return lo;
        }

        private static double kidHigh(Group group) {
            double hi = -Double.MAX_VALUE;
            for (Card kid : group.kids) {
                hi = Math.max(hi, kid.x);
            }
            return hi;
        }

        private Shape block(Card person) {
            int gen = person.gen;
            List<Card> spouses = new ArrayList<>();
            Map<Card, Card> inRow = new HashMap<>();
            inRow.put(original(person), person);
            for (UUID id : marriageOrder(person.id(), partnersOf(person.id()))) {
                Card mate = cardOf(id);
                Card spouse = blood.contains(mate) || claimed.contains(mate) ? copyOf(mate, gen) : occurrence(mate, gen);
                spouses.add(spouse);
                inRow.put(mate, spouse);
            }
            List<Card> row = new ArrayList<>();
            if (TreeTexts.isFemale(person.person.gender())) {
                for (int i = spouses.size() - 1; i >= 0; i--) {
                    row.add(spouses.get(i));
                }
                row.add(person);
            } else {
                row.add(person);
                row.addAll(spouses);
            }
            couple(row, person, spouses);
            int self = row.indexOf(person);

            List<Unit> list = new ArrayList<>();
            Map<Unit, Double> keys = new HashMap<>();
            Map<Unit, List<Card>> parentsInRow = new HashMap<>();
            for (Unit unit : unitsOfParent.getOrDefault(original(person), List.of())) {
                if (maxDown >= 0 && gen >= maxDown || kidsOf(unit).isEmpty() || inProgress.contains(unit)
                        || !consumed.add(unit) && !mayRepeat(original(person))) {
                    continue;
                }
                double key = self;
                List<Card> parents = new ArrayList<>();
                for (Card parent : unit.parents) {
                    Card occ = inRow.get(parent);
                    if (occ == null || unit.parents.size() > 2) {
                        throw new NotTree("parentRow");
                    }
                    parents.add(occ);
                    if (occ != person) {
                        int at = row.indexOf(occ);
                        key = at > self ? at - 0.5 : at + 0.5;
                    }
                }
                list.add(unit);
                keys.put(unit, key);
                parentsInRow.put(unit, parents);
            }
            list.sort(Comparator.comparingDouble(keys::get));
            List<Group> groups = new ArrayList<>();
            for (Unit unit : list) {
                inProgress.add(unit);
                groups.add(group(unit, gen + 1, parentsInRow.get(unit)));
                inProgress.remove(unit);
            }

            int n = row.size();
            double[] extra = new double[n];
            double[] anchors = new double[list.size()];
            boolean done = false;
            for (int round = 0; round < 100 && !done; round++) {
                row.get(0).x = 0;
                for (int i = 1; i < n; i++) {
                    row.get(i).x = row.get(i - 1).x + separation(row.get(i - 1), row.get(i)) + extra[i - 1];
                }
                for (int i = 0; i < list.size(); i++) {
                    double key = keys.get(list.get(i));
                    if (key == Math.rint(key)) {
                        anchors[i] = row.get((int) key).x;
                    } else {
                        Card a = row.get((int) Math.floor(key));
                        Card b = row.get((int) Math.ceil(key));
                        anchors[i] = (a.x + a.width / 2.0 + b.x - b.width / 2.0) / 2;
                    }
                }
                done = true;
                Shape acc = new Shape();
                for (int i = 0; i < list.size(); i++) {
                    Group group = groups.get(i);
                    group.shape.shift(anchors[i] - (kidLow(group) + kidHigh(group)) / 2);
                    if (i > 0) {
                        group.shape.shift(Math.max(0, Math.max(need(acc, group.shape), anchors[i - 1] + BAR_GAP - kidLow(group))));
                    }
                    if (i + 1 < list.size()) {
                        double over = kidHigh(group) - (anchors[i + 1] - BAR_GAP);
                        if (over > 0) {
                            double key = keys.get(list.get(i + 1));
                            int gap = key == Math.rint(key) ? (int) key - 1 : (int) Math.floor(key);
                            if (gap < 0) {
                                throw new NotTree("barGap");
                            }
                            extra[gap] += 2 * over + 1;
                            if (extra[gap] > maxSpread) {
                                throw new NotTree("spread");
                            }
                            done = false;
                            break;
                        }
                    }
                    acc.absorb(group.shape);
                }
            }
            if (!done) {
                throw new NotTree("rounds");
            }
            Shape shape = new Shape();
            row.forEach(shape::add);
            for (int i = 0; i < list.size(); i++) {
                Group group = groups.get(i);
                shape.absorb(group.shape);
                shape.span(2 * gen + 1, Math.min(anchors[i], kidLow(group)), Math.max(anchors[i], kidHigh(group)));
            }
            return shape;
        }

        private void snap(Card child) {
            Brood brood = broodOf.get(child);
            if (brood == null) {
                return;
            }
            List<Card> parents = new ArrayList<>(brood.parents());
            parents.sort(Comparator.comparingInt(c -> c.cx));
            // Les parents de la carte centrale se placent au milieu de toute la fratrie, pas au-dessus d'elle seule.
            int target = child.cx;
            if (child == layout.root) {
                int lo = Integer.MAX_VALUE;
                int hi = Integer.MIN_VALUE;
                for (Card kid : brood.kids()) {
                    lo = Math.min(lo, kid.cx);
                    hi = Math.max(hi, kid.cx);
                }
                target = betweenCard != null ? lo : Math.floorDiv(lo + hi, 2);
            }
            int delta;
            if (parents.size() == 2) {
                Card left = child == layout.root && betweenCard != null ? betweenCard : parents.get(0);
                delta = target - (left.right() + parents.get(1).left()) / 2;
            } else {
                delta = target - parents.get(0).cx;
            }
            for (Card parent : parents) {
                if (delta != 0) {
                    ancestry.get(parent).members.forEach(c -> c.cx += delta);
                }
                snap(parent);
            }
        }

        private void pedigreeConnectors() {
            List<Link> links = new ArrayList<>();
            Map<Pair, Link> byPair = new HashMap<>();
            for (Couple couple : couples) {
                if (couple.spouses().isEmpty()) {
                    continue;
                }
                Card hub = couple.hub();
                int reach = 1;
                int lowest = Integer.MAX_VALUE;
                for (Card card : couple.row()) {
                    lowest = Math.min(lowest, card.height);
                    reach = Math.max(reach, Math.abs(card.index - hub.index));
                }
                int step = 0;
                int base = COUPLE_LINE_Y;
                if (reach > 1) {
                    step = Math.max(2, Math.min(LINK_STEP, (lowest - 4 - REMOTE_LINE_Y) / (reach - 1)));
                    base = REMOTE_LINE_Y + (reach - 1) * step;
                }
                List<Card> row = rows.get(hub.gen);
                for (Card spouse : couple.spouses()) {
                    Card a = hub.index < spouse.index ? hub : spouse;
                    Card b = a == hub ? spouse : hub;
                    int distance = b.index - a.index;
                    int anchorX = (a.right() + b.left()) / 2;
                    int iconX = anchorX;
                    if (distance > 1) {
                        int toHub = hub.index > spouse.index ? 1 : -1;
                        anchorX = gapCenter(spouse, row.get(spouse.index + toHub));
                        iconX = gapCenter(hub, row.get(hub.index - toHub));
                    }
                    int dy = base - (distance - 1) * step;
                    // Les deux parents remariés : leurs lignes passent au-dessus du même intervalle.
                    if (distance > 1) {
                        for (Link other : links) {
                            if (other.left().gen == a.gen && other.dy() == dy && other.left().cx < b.cx && a.cx < other.right().cx) {
                                dy += 3;
                            }
                        }
                    }
                    Link link = new Link(a, b, dy, anchorX, iconX);
                    links.add(link);
                    byPair.put(new Pair(hub, spouse), link);
                    byPair.put(new Pair(spouse, hub), link);
                }
            }
            List<Family> families = new ArrayList<>();
            for (Brood brood : broods) {
                List<Card> parents = brood.parents();
                Link link = parents.size() == 2 ? byPair.get(new Pair(parents.get(0), parents.get(1))) : null;
                families.add(new Family(parents, brood.kids(), link));
            }
            connect(links, families);
        }

        private void orderRows() {
            for (Map.Entry<UUID, List<UUID>> entry : clusters.entrySet()) {
                List<Card> line = new ArrayList<>();
                for (UUID id : arrange(entry.getValue(), startEntry(entry.getValue()))) {
                    line.add(layout.byId.get(id));
                }
                for (int i = 0; i < line.size(); i++) {
                    line.get(i).index = i;
                }
                lines.put(entry.getKey(), line);
                lineIds.put(entry.getKey(), lineIds.size());
            }
            for (Unit unit : units.values()) {
                prepare(unit);
                unitsByGen.computeIfAbsent(unit.home.gen, g -> new ArrayList<>()).add(unit);
            }
            List<UUID> keys = new ArrayList<>(lines.keySet());
            keys.sort(Comparator.<UUID>comparingInt(k -> lines.get(k).get(0).gen)
                    .thenComparing(k -> lines.get(k).get(0).id(), birthOrder()));
            List<UUID> starts = new ArrayList<>();
            for (UUID key : keys) {
                if (lines.get(key).stream().allMatch(c -> unitOfChild.get(c) == null)) {
                    starts.add(key);
                }
            }
            if (starts.isEmpty()) {
                starts.add(keys.get(0));
            }
            List<UUID> tried = starts.subList(0, Math.min(starts.size(), MAX_STARTS));
            TreeMap<Integer, List<List<Card>>> best = null;
            double bestCost = Double.MAX_VALUE;
            for (int attempt = 0; attempt < ports.size() * tried.size(); attempt++) {
                int variant = attempt / tried.size();
                TreeMap<Integer, List<List<Card>>> order = new TreeMap<>();
                Set<UUID> seen = new HashSet<>();
                walk(tried.get(attempt % tried.size()), null, order, seen, variant);
                for (UUID key : keys) {
                    if (!seen.contains(key)) {
                        walk(key, null, order, seen, variant);
                    }
                }
                if (mirrored(variant)) {
                    order.values().forEach(Collections::reverse);
                }
                double cost = totalCost(order);
                if (cost < bestCost) {
                    bestCost = cost;
                    best = order;
                }
            }
            totalCost(best);
            improve(best);
            for (Map.Entry<Integer, List<List<Card>>> entry : best.entrySet()) {
                List<Card> row = new ArrayList<>();
                entry.getValue().forEach(row::addAll);
                rows.put(entry.getKey(), row);
                index(entry.getValue());
            }
            for (Unit unit : units.values()) {
                unit.parents.sort(Comparator.comparingInt((Card c) -> c.gen).thenComparingInt(c -> c.index));
                unit.kids.sort(Comparator.comparingInt((Card c) -> c.gen).thenComparingInt(c -> c.index));
            }
        }

        private void prepare(Unit unit) {
            Card home = unit.parents.get(0);
            for (Card parent : unit.parents) {
                if (parent.gen > home.gen) {
                    home = parent;
                }
            }
            unit.home = home;
            for (Card kid : unit.kids) {
                unit.homes.set(lineIds.get(find(kid.id())));
            }
            List<Card> sorted = new ArrayList<>(unit.kids);
            sorted.sort(Comparator.comparing(Card::id, birthOrder()));
            unit.births.addAll(sorted);
            List<Card> left = new ArrayList<>();
            List<Card> middle = new ArrayList<>();
            List<Card> right = new ArrayList<>();
            for (Card kid : sorted) {
                boolean towardLeft = false;
                boolean towardRight = false;
                for (Card mate : lines.get(find(kid.id()))) {
                    Unit other = unitOfChild.get(mate);
                    if (mate != kid && other != null && other != unit) {
                        towardLeft |= mate.index < kid.index;
                        towardRight |= mate.index > kid.index;
                    }
                }
                (towardLeft == towardRight ? middle : towardLeft ? left : right).add(kid);
            }
            unit.order.addAll(left);
            unit.order.addAll(middle);
            unit.order.addAll(right);
        }

        private record Port(Unit unit, Card kid, boolean down, double key) {
            boolean same(@Nullable Port other) {
                return other != null && unit == other.unit && kid == other.kid;
            }
        }

        private static boolean mirrored(int variant) {
            return (variant & 1) != 0;
        }

        private List<Port> ports(UUID key, int variant) {
            return ports.get(variant).computeIfAbsent(key, k -> {
                List<Card> line = lines.get(k);
                for (int i = 0; i < line.size(); i++) {
                    line.get(i).index = i;
                }
                List<Port> down = new ArrayList<>();
                Set<Unit> seen = new HashSet<>();
                for (Card card : line) {
                    for (Unit unit : unitsOfParent.getOrDefault(card, List.of())) {
                        if (find(unit.home.id()).equals(k) && seen.add(unit)) {
                            double anchor = anchorIndex(unit);
                            List<Card> kids = variant < 2 ? unit.order : unit.births;
                            for (int i = 0; i < kids.size(); i++) {
                                down.add(new Port(unit, kids.get(i), true, anchor + i * 1e-4));
                            }
                        }
                    }
                }
                down.sort(Comparator.comparingDouble(Port::key));
                List<Port> list = new ArrayList<>(down);
                for (int i = line.size() - 1; i >= 0; i--) {
                    Unit unit = unitOfChild.get(line.get(i));
                    if (unit != null && !find(unit.home.id()).equals(k)) {
                        list.add(new Port(unit, line.get(i), false, 0));
                    }
                }
                if (mirrored(variant)) {
                    Collections.reverse(list);
                    list.addAll(0, list.subList(list.size() - down.size(), list.size()));
                    list.subList(list.size() - down.size(), list.size()).clear();
                }
                return list;
            });
        }

        private List<Port> walk(UUID key, @Nullable Port entry, TreeMap<Integer, List<List<Card>>> order, Set<UUID> seen, int variant) {
            seen.add(key);
            List<Card> line = lines.get(key);
            order.computeIfAbsent(line.get(0).gen, g -> new ArrayList<>()).add(line);
            List<Port> list = ports(key, variant);
            int at = -1;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).same(entry)) {
                    at = i;
                }
            }
            List<Port> own = new ArrayList<>();
            List<Port> later = new ArrayList<>();
            for (int i = 1; i <= list.size(); i++) {
                int k = (at + i) % list.size();
                Port port = list.get(k);
                if (port.same(entry)) {
                    continue;
                }
                if (!port.down() && (entry == null || entry.down() || k < at)) {
                    own.add(port);
                    continue;
                }
                UUID next = find(port.down() ? port.kid().id() : port.unit().home.id());
                if (!seen.contains(next)) {
                    later.addAll(walk(next, port, order, seen, variant));
                }
            }
            own.addAll(later);
            if (entry != null) {
                return own;
            }
            ArrayDeque<Port> queue = new ArrayDeque<>(own);
            while (!queue.isEmpty()) {
                Port port = queue.poll();
                UUID next = find(port.unit().home.id());
                if (!seen.contains(next)) {
                    queue.addAll(walk(next, port, order, seen, variant));
                }
            }
            return List.of();
        }

        private void index(List<List<Card>> blocks) {
            int i = 0;
            for (List<Card> block : blocks) {
                for (Card card : block) {
                    card.index = i++;
                }
            }
        }

        private Card hubOf(Card a, Card b) {
            return partnersOf(b.id()).size() > partnersOf(a.id()).size() ? b : a;
        }

        private double anchorIndex(Unit unit) {
            Card a = null;
            Card b = null;
            for (Card parent : unit.parents) {
                if (parent.gen == unit.home.gen) {
                    if (a == null) {
                        a = parent;
                    } else {
                        b = parent;
                    }
                }
            }
            if (b == null) {
                return a.index;
            }
            if (Math.abs(a.index - b.index) <= 1) {
                return (a.index + b.index) / 2.0;
            }
            Card hub = hubOf(a, b);
            Card spouse = hub == a ? b : a;
            return spouse.index + 0.5 * Math.signum(hub.index - spouse.index);
        }

        private double pairCost(int gen) {
            List<Unit> list = unitsByGen.getOrDefault(gen, List.of());
            if (list.isEmpty()) {
                return 0;
            }
            int count = 0;
            for (Unit unit : list) {
                count += unit.kids.size();
            }
            double[] anchor = new double[count];
            int[] kid = new int[count];
            int n = 0;
            int foreign = 0;
            int births = 0;
            int[] row = null;
            for (int u = 0; u < list.size(); u++) {
                Unit unit = list.get(u);
                double a = anchorIndex(unit);
                int lo = Integer.MAX_VALUE;
                int hi = -1;
                for (Card k : unit.kids) {
                    if (k.gen == gen + 1) {
                        anchor[n] = a;
                        kid[n++] = k.index;
                        lo = Math.min(lo, k.index);
                        hi = Math.max(hi, k.index);
                        for (Card o : unit.kids) {
                            if (o.gen == k.gen && o.index > k.index && birth(o) < birth(k)) {
                                births++;
                            }
                        }
                    }
                }
                if (hi - lo > 1) {
                    if (row == null) {
                        row = rowCards(gen + 1);
                    }
                    for (int i = lo + 1; i < hi; i++) {
                        if (!unit.homes.get(row[i])) {
                            foreign++;
                        }
                    }
                }
            }
            long[] keys = new long[n];
            int width = 1;
            for (int i = 0; i < n; i++) {
                width = Math.max(width, kid[i] + 1);
            }
            for (int i = 0; i < n; i++) {
                keys[i] = (long) Math.round(anchor[i] * 2) * width + kid[i];
            }
            Arrays.sort(keys);
            int[] tree = new int[width + 1];
            int crossings = 0;
            for (int i = 0; i < n; i++) {
                int k = (int) (keys[i] % width) + 1;
                int atMost = 0;
                for (int j = k; j > 0; j -= j & -j) {
                    atMost += tree[j];
                }
                crossings += i - atMost;
                for (int j = k; j <= width; j += j & -j) {
                    tree[j]++;
                }
            }
            return 10.0 * crossings + 4.0 * foreign + births;
        }

        private int[] rowCards(int gen) {
            List<List<Card>> blocks = current.get(gen);
            int size = 0;
            for (List<Card> block : blocks) {
                size += block.size();
            }
            int[] row = new int[size];
            int i = 0;
            for (List<Card> block : blocks) {
                int id = lineIds.get(find(block.get(0).id()));
                for (int j = 0; j < block.size(); j++) {
                    row[i++] = id;
                }
            }
            return row;
        }

        private long birth(Card card) {
            Long birth = card.person.birthDay();
            return birth == null ? Long.MAX_VALUE : birth;
        }

        private double totalCost(TreeMap<Integer, List<List<Card>>> order) {
            current = order;
            for (List<List<Card>> blocks : order.values()) {
                index(blocks);
            }
            double cost = 0;
            for (int gen : order.keySet()) {
                cost += pairCost(gen);
            }
            return cost;
        }

        private double rowCost(int gen) {
            return pairCost(gen - 1) + pairCost(gen);
        }

        private void improve(TreeMap<Integer, List<List<Card>>> order) {
            for (int pass = 0; pass < IMPROVE_PASSES; pass++) {
                boolean changed = false;
                for (int gen : order.keySet()) {
                    List<List<Card>> blocks = order.get(gen);
                    for (List<Card> block : new ArrayList<>(blocks)) {
                        changed |= move(gen, blocks, blocks.indexOf(block));
                    }
                }
                if (!changed) {
                    break;
                }
            }
        }

        private boolean move(int gen, List<List<Card>> blocks, int from) {
            double before = rowCost(gen);
            List<Card> block = blocks.remove(from);
            int bestAt = from;
            double bestCost = before;
            int lo = Math.max(0, from - SIFT_WINDOW);
            int hi = Math.min(blocks.size(), from + SIFT_WINDOW);
            for (int at = lo; at <= hi; at++) {
                if (at == from) {
                    continue;
                }
                blocks.add(at, block);
                index(blocks);
                double cost = rowCost(gen);
                blocks.remove(at);
                if (cost < bestCost - 1e-9) {
                    bestCost = cost;
                    bestAt = at;
                }
            }
            blocks.add(bestAt, block);
            index(blocks);
            return bestAt != from;
        }

        private Comparator<UUID> birthOrder() {
            return Comparator.<UUID>comparingLong(id -> {
                Long birth = persons.get(id).birthDay();
                return birth == null ? Long.MAX_VALUE : birth;
            }).thenComparing(id -> persons.get(id).name());
        }

        private UUID startEntry(List<UUID> members) {
            UUID best = members.get(0);
            for (UUID id : members) {
                int diff = partnersOf(id).size() - partnersOf(best).size();
                if (diff > 0 || diff == 0 && !TreeTexts.isFemale(persons.get(id).gender()) && TreeTexts.isFemale(persons.get(best).gender())) {
                    best = id;
                }
            }
            return best;
        }

        private List<UUID> arrange(List<UUID> members, UUID entry) {
            if (members.size() < 2 || members.size() > MAX_PERMUTED) {
                return chain(members, entry);
            }
            List<UUID> sorted = new ArrayList<>(members);
            sorted.sort(Comparator.<UUID, Boolean>comparing(id -> TreeTexts.isFemale(persons.get(id).gender()))
                    .thenComparing(birthOrder()).thenComparing(UUID::compareTo));
            Map<UUID, List<UUID>> mates = new HashMap<>();
            for (UUID id : sorted) {
                mates.put(id, marriageOrder(id, members));
            }
            int n = sorted.size();
            int[] perm = new int[n];
            int[] best = new int[n];
            int[] bestScore = {Integer.MAX_VALUE};
            permute(sorted, mates, perm, new boolean[n], 0, best, bestScore);
            List<UUID> line = new ArrayList<>();
            for (int i : best) {
                line.add(sorted.get(i));
            }
            return line;
        }

        private List<UUID> marriageOrder(UUID id, List<UUID> members) {
            List<UUID> declared = new ArrayList<>(persons.get(id).spouses());
            if (!declared.isEmpty()) {
                declared.add(declared.remove(0));
            }
            List<UUID> list = new ArrayList<>();
            for (UUID spouse : declared) {
                if (members.contains(spouse) && partnersOf(id).contains(spouse)) {
                    list.add(spouse);
                }
            }
            for (UUID partner : partnersOf(id)) {
                if (members.contains(partner) && !list.contains(partner)) {
                    list.add(partner);
                }
            }
            return list;
        }

        private void permute(List<UUID> sorted, Map<UUID, List<UUID>> mates, int[] perm, boolean[] used, int depth, int[] best, int[] bestScore) {
            int n = sorted.size();
            if (depth == n) {
                Map<UUID, Integer> pos = new HashMap<>();
                for (int i = 0; i < n; i++) {
                    pos.put(sorted.get(perm[i]), i);
                }
                int score = 0;
                for (UUID id : sorted) {
                    List<UUID> list = mates.get(id);
                    int at = pos.get(id);
                    boolean female = TreeTexts.isFemale(persons.get(id).gender());
                    int preferred = female ? -1 : 1;
                    int right = 0;
                    for (int i = 0; i < list.size(); i++) {
                        UUID mate = list.get(i);
                        int distance = pos.get(mate) - at;
                        right += distance > 0 ? 1 : 0;
                        score += Math.abs(distance);
                        if (TreeTexts.isFemale(persons.get(mate).gender()) != female && Integer.signum(distance) != preferred) {
                            score += 8;
                        }
                        for (int j = i + 1; j < list.size(); j++) {
                            if (Math.abs(pos.get(list.get(j)) - at) < Math.abs(distance)) {
                                score += 3;
                            }
                        }
                    }
                    score += 5 * Math.min(right, list.size() - right);
                }
                if (score < bestScore[0]) {
                    bestScore[0] = score;
                    System.arraycopy(perm, 0, best, 0, n);
                }
                return;
            }
            for (int i = 0; i < n; i++) {
                if (!used[i]) {
                    used[i] = true;
                    perm[depth] = i;
                    permute(sorted, mates, perm, used, depth + 1, best, bestScore);
                    used[i] = false;
                }
            }
        }

        private List<UUID> chain(List<UUID> members, UUID entry) {
            ArrayDeque<UUID> line = new ArrayDeque<>();
            Set<UUID> placed = new HashSet<>();
            line.add(entry);
            placed.add(entry);
            List<UUID> spouses = new ArrayList<>();
            for (UUID partner : partnersOf(entry)) {
                if (members.contains(partner)) {
                    spouses.add(partner);
                }
            }
            List<UUID> declared = persons.get(entry).spouses();
            if (spouses.size() > 1 && !declared.isEmpty() && spouses.remove(declared.get(0))) {
                spouses.add(declared.get(0));
            }
            Boolean side = null;
            for (UUID partner : spouses) {
                if (!placed.contains(partner)) {
                    if (side == null) {
                        Boolean bySex = side(entry, partner);
                        side = bySex == null || bySex;
                    }
                    extend(line, placed, members, partner, side);
                }
            }
            for (UUID member : members) {
                if (!placed.contains(member)) {
                    extend(line, placed, members, member, true);
                }
            }
            return new ArrayList<>(line);
        }

        private void extend(ArrayDeque<UUID> line, Set<UUID> placed, List<UUID> members, UUID id, boolean right) {
            placed.add(id);
            if (right) {
                line.addLast(id);
            } else {
                line.addFirst(id);
            }
            for (UUID partner : partnersOf(id)) {
                if (members.contains(partner) && !placed.contains(partner)) {
                    extend(line, placed, members, partner, right);
                }
            }
        }

        private Boolean side(UUID placedId, UUID partner) {
            boolean placedFemale = TreeTexts.isFemale(persons.get(placedId).gender());
            boolean partnerFemale = TreeTexts.isFemale(persons.get(partner).gender());
            return placedFemale == partnerFemale ? null : partnerFemale;
        }

        private void buildUnits() {
            for (Card card : layout.cards) {
                List<UUID> list = parentsOf(card.id());
                if (list.isEmpty()) {
                    continue;
                }
                List<String> keys = new ArrayList<>();
                list.forEach(id -> keys.add(id.toString()));
                Collections.sort(keys);
                Unit unit = units.computeIfAbsent(String.join("+", keys), k -> {
                    Unit u = new Unit();
                    list.forEach(id -> u.parents.add(layout.byId.get(id)));
                    u.parents.forEach(p -> unitsOfParent.computeIfAbsent(p, x -> new ArrayList<>()).add(u));
                    return u;
                });
                unit.kids.add(card);
                unitOfChild.put(card, unit);
            }
        }

        private Card[] remote(Unit unit) {
            if (unit.parents.size() != 2) {
                return null;
            }
            Card a = unit.parents.get(0);
            Card b = unit.parents.get(1);
            if (a.gen != b.gen || Math.abs(a.index - b.index) <= 1) {
                return null;
            }
            Card hub = hubOf(a, b);
            return new Card[]{hub, hub == a ? b : a};
        }

        private static double remoteAnchor(Card hub, Card spouse) {
            double dir = Math.signum(spouse.x - hub.x);
            return spouse.x - dir * (spouse.width / 2.0 + COUPLE_GAP / 2.0);
        }

        private double separation(Card a, Card b) {
            int gap;
            if (arePartners(a, b) || find(a.id()).equals(find(b.id()))) {
                gap = COUPLE_GAP;
            } else if (unitOfChild.get(a) != null && unitOfChild.get(a) == unitOfChild.get(b)) {
                gap = CARD_GAP;
            } else {
                gap = FAMILY_GAP;
            }
            return a.width / 2.0 + gap + b.width / 2.0;
        }

        private void placeCards() {
            for (List<Card> row : rows.values()) {
                double x = 0;
                for (int i = 0; i < row.size(); i++) {
                    if (i > 0) {
                        x += separation(row.get(i - 1), row.get(i));
                    }
                    row.get(i).x = x;
                }
            }
            List<Integer> gens = new ArrayList<>(rows.keySet());
            for (int pass = 0; pass < PASSES; pass++) {
                for (int gen : gens) {
                    childrenUnderParents(gen, rows.get(gen));
                }
                for (int i = gens.size() - 1; i >= 0; i--) {
                    parentsAboveChildren(gens.get(i), rows.get(gens.get(i)));
                }
            }

            double shift = layout.root != null ? layout.root.x : 0;
            for (Card card : layout.cards) {
                card.cx = (int) Math.round(card.x - shift);
            }
        }

        private void childrenUnderParents(int gen, List<Card> row) {
            int n = row.size();
            double[] sum = new double[n];
            int[] count = new int[n];
            Set<Unit> done = new HashSet<>();
            for (Card card : row) {
                Unit unit = unitOfChild.get(card);
                if (unit == null || !done.add(unit)) {
                    continue;
                }
                double anchor = 0;
                int parentsAbove = 0;
                for (Card parent : unit.parents) {
                    if (parent.gen < gen) {
                        anchor += parent.x;
                        parentsAbove++;
                    }
                }
                if (parentsAbove == 0) {
                    continue;
                }
                anchor /= parentsAbove;
                Card[] remote = remote(unit);
                if (remote != null && remote[0].gen < gen) {
                    anchor = remoteAnchor(remote[0], remote[1]);
                }

                int lo = Integer.MAX_VALUE;
                int hi = -1;
                for (Card kid : unit.kids) {
                    if (kid.gen == gen) {
                        lo = Math.min(lo, kid.index);
                        hi = Math.max(hi, kid.index);
                    }
                }
                while (lo > 0 && arePartners(row.get(lo - 1), row.get(lo)) && unitOfChild.get(row.get(lo - 1)) == null) {
                    lo--;
                }
                while (hi < n - 1 && arePartners(row.get(hi), row.get(hi + 1)) && unitOfChild.get(row.get(hi + 1)) == null) {
                    hi++;
                }
                double[] rel = new double[hi - lo + 1];
                for (int i = lo + 1; i <= hi; i++) {
                    rel[i - lo] = rel[i - lo - 1] + separation(row.get(i - 1), row.get(i));
                }
                double half = rel[hi - lo] / 2;
                for (int i = lo; i <= hi; i++) {
                    sum[i] += anchor - half + rel[i - lo];
                    count[i]++;
                }
            }
            solve(row, followPartners(row, sum, count));
        }

        private void parentsAboveChildren(int gen, List<Card> row) {
            int n = row.size();
            double[] sum = new double[n];
            int[] count = new int[n];
            Set<Unit> done = new HashSet<>();
            for (Card card : row) {
                for (Unit unit : unitsOfParent.getOrDefault(card, List.of())) {
                    if (!done.add(unit)) {
                        continue;
                    }
                    double min = Double.MAX_VALUE;
                    double max = -Double.MAX_VALUE;
                    for (Card kid : unit.kids) {
                        if (kid.gen > gen) {
                            min = Math.min(min, kid.x);
                            max = Math.max(max, kid.x);
                        }
                    }
                    if (max < min) {
                        continue;
                    }
                    double anchor = 0;
                    int inRow = 0;
                    for (Card parent : unit.parents) {
                        if (parent.gen == gen) {
                            anchor += parent.x;
                            inRow++;
                        }
                    }
                    Card[] remote = remote(unit);
                    if (remote != null && remote[0].gen == gen) {
                        Card spouse = remote[1];
                        double dir = Math.signum(spouse.x - remote[0].x);
                        sum[spouse.index] += (min + max) / 2 + dir * (spouse.width / 2.0 + COUPLE_GAP / 2.0);
                        count[spouse.index]++;
                        continue;
                    }
                    double delta = (min + max) / 2 - anchor / inRow;
                    for (Card parent : unit.parents) {
                        if (parent.gen == gen) {
                            sum[parent.index] += parent.x + delta;
                            count[parent.index]++;
                        }
                    }
                }
            }
            solve(row, followPartners(row, sum, count));
        }

        private double[] followPartners(List<Card> row, double[] sum, int[] count) {
            int n = row.size();
            double[] desired = new double[n];
            for (int i = 0; i < n; i++) {
                desired[i] = count[i] > 0 ? sum[i] / count[i] : row.get(i).x;
            }
            for (int i = 0; i < n; i++) {
                if (count[i] > 0) {
                    continue;
                }
                Card card = row.get(i);
                double delta = 0;
                int moved = 0;
                for (UUID partner : partnersOf(card.id())) {
                    Card other = layout.byId.get(partner);
                    if (other.gen == card.gen && count[other.index] > 0) {
                        delta += desired[other.index] - other.x;
                        moved++;
                    }
                }
                if (moved > 0) {
                    desired[i] = card.x + delta / moved;
                }
            }
            return desired;
        }

        private void solve(List<Card> row, double[] desired) {
            int n = row.size();
            if (n == 0) {
                return;
            }
            double[] offset = new double[n];
            for (int i = 1; i < n; i++) {
                offset[i] = offset[i - 1] + separation(row.get(i - 1), row.get(i));
            }
            double[] blockSum = new double[n];
            int[] blockCount = new int[n];
            int[] blockStart = new int[n];
            int blocks = 0;
            for (int i = 0; i < n; i++) {
                if (i > 0 && find(row.get(i - 1).id()).equals(find(row.get(i).id()))) {
                    blockSum[blocks - 1] += desired[i] - offset[i];
                    blockCount[blocks - 1]++;
                } else {
                    blockSum[blocks] = desired[i] - offset[i];
                    blockCount[blocks] = 1;
                    blockStart[blocks] = i;
                    blocks++;
                }
                while (blocks > 1 && blockSum[blocks - 2] / blockCount[blocks - 2] > blockSum[blocks - 1] / blockCount[blocks - 1]) {
                    blockSum[blocks - 2] += blockSum[blocks - 1];
                    blockCount[blocks - 2] += blockCount[blocks - 1];
                    blocks--;
                }
            }
            for (int b = 0; b < blocks; b++) {
                double mean = blockSum[b] / blockCount[b];
                int end = b + 1 < blocks ? blockStart[b + 1] : n;
                for (int i = blockStart[b]; i < end; i++) {
                    row.get(i).x = mean + offset[i];
                }
            }
        }

        private record Link(Card left, Card right, int dy, int anchorX, int iconX) {
        }

        private record Family(List<Card> parents, List<Card> kids, @Nullable Link link) {
        }

        private static final class Bar {
            final Family family;
            final List<Card> kids;
            final int gen;
            int anchorX;
            int lo;
            int hi;
            int level;

            Bar(Family family, List<Card> kids, int gen) {
                this.family = family;
                this.kids = kids;
                this.gen = gen;
            }
        }

        private void buildConnectors() {
            Map<String, Link> links = new HashMap<>();
            for (List<Card> line : lines.values()) {
                int reach = 1;
                int lowest = Integer.MAX_VALUE;
                for (Card a : line) {
                    lowest = Math.min(lowest, a.height);
                    for (UUID partner : partnersOf(a.id())) {
                        Card b = layout.byId.get(partner);
                        if (b.gen == a.gen) {
                            reach = Math.max(reach, Math.abs(b.index - a.index));
                        }
                    }
                }
                int step = 0;
                int base = COUPLE_LINE_Y;
                if (reach > 1) {
                    step = Math.max(2, Math.min(LINK_STEP, (lowest - 4 - REMOTE_LINE_Y) / (reach - 1)));
                    base = REMOTE_LINE_Y + (reach - 1) * step;
                }
                List<Card> row = rows.get(line.get(0).gen);
                for (Card a : line) {
                    for (UUID partner : partnersOf(a.id())) {
                        Card b = layout.byId.get(partner);
                        if (b.gen != a.gen || b.index <= a.index) {
                            continue;
                        }
                        int distance = b.index - a.index;
                        int dy = base - (distance - 1) * step;
                        int anchorX = (a.right() + b.left()) / 2;
                        int iconX = anchorX;
                        if (distance > 1) {
                            Card hub = hubOf(a, b);
                            Card spouse = hub == a ? b : a;
                            int toHub = hub.index > spouse.index ? 1 : -1;
                            anchorX = gapCenter(spouse, row.get(spouse.index + toHub));
                            iconX = gapCenter(hub, row.get(hub.index - toHub));
                        }
                        links.put(pairKey(a, b), new Link(a, b, dy, anchorX, iconX));
                    }
                }
            }
            List<Family> families = new ArrayList<>();
            for (Unit unit : units.values()) {
                Link link = unit.parents.size() == 2 ? links.get(pairKey(unit.parents.get(0), unit.parents.get(1))) : null;
                families.add(new Family(unit.parents, unit.kids, link));
            }
            connect(links.values(), families);
        }

        private void connect(Collection<Link> links, List<Family> families) {
            Map<Integer, List<Bar>> barsByGen = new TreeMap<>();
            for (Family family : families) {
                Link link = family.link();
                Card lowest = family.parents().get(family.parents().size() - 1);
                Map<Integer, List<Card>> byGen = new TreeMap<>();
                for (Card kid : family.kids()) {
                    if (kid.gen > lowest.gen) {
                        byGen.computeIfAbsent(kid.gen, k -> new ArrayList<>()).add(kid);
                    }
                }
                for (Map.Entry<Integer, List<Card>> entry : byGen.entrySet()) {
                    Bar bar = new Bar(family, entry.getValue(), entry.getKey());
                    bar.anchorX = link != null ? link.anchorX() : lowest.centerX();
                    bar.lo = bar.anchorX;
                    bar.hi = bar.anchorX;
                    for (Card kid : bar.kids) {
                        bar.lo = Math.min(bar.lo, kid.centerX());
                        bar.hi = Math.max(bar.hi, kid.centerX());
                    }
                    barsByGen.computeIfAbsent(bar.gen, g -> new ArrayList<>()).add(bar);
                }
            }

            Map<Integer, Integer> levels = new HashMap<>();
            for (Map.Entry<Integer, List<Bar>> entry : barsByGen.entrySet()) {
                List<Bar> bars = entry.getValue();
                bars.sort(Comparator.comparingInt((Bar b) -> b.lo).thenComparingInt(b -> b.hi));
                List<Integer> levelEnd = new ArrayList<>();
                for (Bar bar : bars) {
                    int level = 0;
                    while (level < levelEnd.size() && levelEnd.get(level) >= bar.lo - BAR_MARGIN) {
                        level++;
                    }
                    if (level == levelEnd.size()) {
                        levelEnd.add(bar.hi);
                    } else {
                        levelEnd.set(level, bar.hi);
                    }
                    bar.level = level;
                }
                levels.put(entry.getKey(), levelEnd.size());
            }

            int top = 0;
            boolean first = true;
            for (Map.Entry<Integer, List<Card>> entry : rows.entrySet()) {
                if (!first) {
                    int needed = levels.getOrDefault(entry.getKey(), 1);
                    top += Math.max(ROW_GAP, BAR_TOP + BAR_BOTTOM + (needed - 1) * BAR_STEP);
                }
                first = false;
                int height = 0;
                for (Card card : entry.getValue()) {
                    card.y = top;
                    height = Math.max(height, card.height);
                }
                top += height;
            }

            for (Link link : links) {
                coupleLine(layout, link.left(), link.right(), link.left().y + link.dy(), link.iconX());
            }
            for (List<Bar> bars : barsByGen.values()) {
                for (Bar bar : bars) {
                    Link link = bar.family.link();
                    int anchorY = link != null ? link.left().y + link.dy() : bar.family.parents().get(bar.family.parents().size() - 1).bottom();
                    int barY = bar.kids.get(0).y - BAR_BOTTOM - bar.level * BAR_STEP;
                    layout.segments.add(new Segment(bar.anchorX, anchorY, bar.anchorX, barY, LINE_COLOR, false));
                    if (bar.hi > bar.lo) {
                        layout.segments.add(new Segment(bar.lo, barY, bar.hi, barY, LINE_COLOR, false));
                    }
                    for (Card kid : bar.kids) {
                        layout.segments.add(new Segment(kid.centerX(), barY, kid.centerX(), kid.y, LINE_COLOR, false));
                    }
                }
            }
        }

        private static int gapCenter(Card a, Card b) {
            return a.centerX() < b.centerX() ? (a.right() + b.left()) / 2 : (b.right() + a.left()) / 2;
        }

        private static String pairKey(Card a, Card b) {
            String x = a.id().toString();
            String y = b.id().toString();
            return x.compareTo(y) < 0 ? x + "+" + y : y + "+" + x;
        }
    }

    private static void coupleLine(TreeLayout layout, Card a, Card b, int y, int iconX) {
        Card left = a.centerX() <= b.centerX() ? a : b;
        Card right = left == a ? b : a;
        boolean married = left.person.spouses().contains(right.id()) || right.person.spouses().contains(left.id());
        boolean current = married && (right.id().equals(left.person.spouse()) || left.id().equals(right.person.spouse()));
        boolean divorced = left.person.divorced().contains(right.id()) || right.person.divorced().contains(left.id());
        boolean widowed = left.person.deceased() || right.person.deceased();

        int color;
        boolean dashed;
        String icon;
        if (divorced) {
            // bague cassée : divorce
            color = DIM_COLOR;
            dashed = true;
            icon = "widow";
        } else if (!married || !current) {
            color = DIM_COLOR;
            dashed = true;
            boolean former = left.person.formerSpouses().contains(right.id()) || right.person.formerSpouses().contains(left.id());
            icon = former ? "married_past" : null;
        } else if (widowed) {
            // veuvage : toujours mariés, alliance sur un trait gris
            color = WIDOW_COLOR;
            dashed = false;
            icon = "married";
        } else {
            color = LINE_COLOR;
            dashed = false;
            icon = "married";
        }

        layout.segments.add(new Segment(left.right(), y, right.left(), y, color, dashed));
        if (icon != null) {
            layout.marks.add(new Mark(iconX, y, icon));
        }
    }

    private static void computeBounds(TreeLayout layout) {
        layout.minX = Integer.MAX_VALUE;
        layout.minY = Integer.MAX_VALUE;
        layout.maxX = Integer.MIN_VALUE;
        layout.maxY = Integer.MIN_VALUE;
        for (Card card : layout.cards) {
            layout.minX = Math.min(layout.minX, card.left());
            layout.minY = Math.min(layout.minY, card.y);
            layout.maxX = Math.max(layout.maxX, card.right());
            layout.maxY = Math.max(layout.maxY, card.bottom() + 6);
        }
        if (layout.cards.isEmpty()) {
            layout.minX = layout.minY = layout.maxX = layout.maxY = 0;
        }
    }

    private static final int UP = -1;
    private static final int SAME = 0;
    private static final int DOWN = 1;

    public static TreeLayout path(List<FamilyPerson> persons, List<UUID> path, TextRenderer font, long today) {
        TreeLayout layout = new TreeLayout();
        Map<UUID, FamilyPerson> byId = new HashMap<>();
        persons.forEach(p -> byId.putIfAbsent(p.id(), p));
        List<Card> chain = new ArrayList<>();
        for (UUID id : path) {
            FamilyPerson person = byId.get(id);
            if (person != null && !layout.byId.containsKey(id)) {
                Card card = card(person, font, today);
                chain.add(card);
                layout.cards.add(card);
                layout.byId.put(id, card);
            }
        }
        if (chain.isEmpty()) {
            return layout;
        }
        chain.get(0).highlighted = true;
        chain.get(chain.size() - 1).highlighted = true;
        layout.root = chain.get(0);

        int n = chain.size();
        int[] steps = new int[Math.max(0, n - 1)];
        for (int i = 0; i < n - 1; i++) {
            steps[i] = step(chain.get(i).person, chain.get(i + 1).person);
        }
        int maxWidth = 0;
        int maxHeight = 0;
        for (Card card : chain) {
            maxWidth = Math.max(maxWidth, card.width);
            maxHeight = Math.max(maxHeight, card.height);
        }
        int column = maxWidth + 30;

        int[] gens = new int[n];
        int[] xs = new int[n];
        for (int i = 0; i < n - 1; i++) {
            int step = steps[i];
            gens[i + 1] = gens[i] + step;
            if (step == SAME) {
                xs[i + 1] = xs[i] + column;
            } else {
                int shift = 0;
                if (i > 0 && steps[i - 1] != SAME && steps[i - 1] != step) {
                    shift += column / 2;
                }
                if (i + 1 < n - 1 && steps[i + 1] != SAME && steps[i + 1] != step) {
                    shift += column / 2;
                }
                xs[i + 1] = xs[i] + shift;
            }
        }
        int minGen = Arrays.stream(gens).min().orElse(0);
        for (int i = 0; i < n; i++) {
            Card card = chain.get(i);
            card.gen = gens[i];
            card.cx = xs[i];
            card.x = xs[i];
            card.y = (gens[i] - minGen) * (maxHeight + ROW_GAP);
        }

        // Ancêtre commun : affiche le couple (l'autre parent partagé par les deux branches).
        Map<Card, int[]> drops = new HashMap<>();
        for (int i = 1; i < n - 1; i++) {
            if (steps[i - 1] != UP || steps[i] != DOWN) {
                continue;
            }
            Card top = chain.get(i);
            FamilyPerson left = chain.get(i - 1).person;
            FamilyPerson right = chain.get(i + 1).person;
            FamilyPerson other = null;
            for (UUID candidate : List.of(left.father(), left.mother())) {
                if (!candidate.equals(top.id()) && (candidate.equals(right.father()) || candidate.equals(right.mother()))
                        && byId.containsKey(candidate) && !layout.byId.containsKey(candidate)) {
                    other = byId.get(candidate);
                    break;
                }
            }
            if (other == null) {
                continue;
            }
            Card mate = card(other, font, today);
            int gap = 24;
            int start = top.centerX() - (top.width + gap + mate.width) / 2;
            top.cx = start + top.width / 2;
            top.x = top.cx;
            mate.cx = top.right() + gap + mate.width / 2;
            mate.x = mate.cx;
            mate.gen = top.gen;
            mate.y = top.y;
            layout.cards.add(mate);
            layout.byId.put(mate.id(), mate);
            int middle = (top.right() + mate.left()) / 2;
            int lineY = top.y + COUPLE_LINE_Y;
            coupleLine(layout, top, mate, lineY, middle);
            drops.put(top, new int[]{middle, lineY});
        }

        for (int i = 0; i < n - 1; i++) {
            Card a = chain.get(i);
            Card b = chain.get(i + 1);
            if (steps[i] == SAME) {
                boolean spouses = a.person.spouses().contains(b.id()) || b.person.spouses().contains(a.id());
                if (spouses) {
                    Card left = a.centerX() <= b.centerX() ? a : b;
                    Card right = left == a ? b : a;
                    int middle = (left.right() + right.left()) / 2;
                    coupleLine(layout, a, b, a.y + COUPLE_LINE_Y, middle);
                } else {
                    int y = a.y + COUPLE_LINE_Y;
                    Card left = a.centerX() <= b.centerX() ? a : b;
                    Card right = left == a ? b : a;
                    layout.segments.add(new Segment(left.right(), y, right.left(), y, DIM_COLOR, true));
                }
            } else {
                Card parent = steps[i] == UP ? b : a;
                Card child = parent == a ? b : a;
                int[] drop = drops.get(parent);
                int px = drop != null ? drop[0] : parent.centerX();
                int py = drop != null ? drop[1] : parent.bottom();
                if (px == child.centerX()) {
                    layout.segments.add(new Segment(px, py, child.centerX(), child.y, LINE_COLOR, false));
                } else {
                    int midY = child.y - 10;
                    layout.segments.add(new Segment(px, py, px, midY, LINE_COLOR, false));
                    layout.segments.add(new Segment(Math.min(px, child.centerX()), midY,
                            Math.max(px, child.centerX()), midY, LINE_COLOR, false));
                    layout.segments.add(new Segment(child.centerX(), midY, child.centerX(), child.y, LINE_COLOR, false));
                }
            }
        }
        computeBounds(layout);
        return layout;
    }

    private static int step(FamilyPerson from, FamilyPerson to) {
        UUID f = from.id();
        UUID t = to.id();
        if (from.father().equals(t) || from.mother().equals(t) || to.children().contains(f)) {
            return UP;
        }
        if (to.father().equals(f) || to.mother().equals(f) || from.children().contains(t)) {
            return DOWN;
        }
        return SAME;
    }
}
