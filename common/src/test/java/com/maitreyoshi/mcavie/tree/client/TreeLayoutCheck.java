package com.maitreyoshi.mcavie.tree.client;

import com.maitreyoshi.mcavie.tree.FamilyPerson;
import com.maitreyoshi.mcavie.tree.TreeSelection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;

public final class TreeLayoutCheck {
    static final int MALE = 1;
    static final int FEMALE = 2;
    static final UUID NIL = new UUID(0, 0);

    static final class P {
        final UUID id;
        final String name;
        final int gender;
        final long birth;
        boolean deceased;
        P father;
        P mother;
        P current;
        final List<P> history = new ArrayList<>();
        final List<P> kids = new ArrayList<>();

        P(int n, String name, int gender, long birth) {
            this.id = new UUID(1, n);
            this.name = name;
            this.gender = gender;
            this.birth = birth;
        }
    }

    static final class Fam {
        final List<P> all = new ArrayList<>();
        final Map<String, P> byName = new HashMap<>();
        P root;

        P add(String name, int gender, long birth) {
            P p = new P(all.size() + 1, name, gender, birth);
            all.add(p);
            byName.put(name, p);
            if (root == null) {
                root = p;
            }
            return p;
        }

        P m(String name, long birth) {
            return add(name, MALE, birth);
        }

        P f(String name, long birth) {
            return add(name, FEMALE, birth);
        }

        void marry(P a, P b) {
            a.history.add(b);
            b.history.add(a);
            a.current = b;
            b.current = a;
        }

        P child(P father, P mother, String name, int gender, long birth) {
            P c = add(name, gender, birth);
            c.father = father;
            c.mother = mother;
            if (father != null) {
                father.kids.add(c);
            }
            if (mother != null) {
                mother.kids.add(c);
            }
            return c;
        }

        P get(String name) {
            return byName.get(name);
        }

        List<FamilyPerson> persons(boolean reverse) {
            List<FamilyPerson> list = new ArrayList<>();
            for (P p : all) {
                LinkedHashSet<UUID> spouses = new LinkedHashSet<>();
                if (p.current != null) {
                    spouses.add(p.current.id);
                }
                p.history.forEach(s -> spouses.add(s.id));
                for (P k : p.kids) {
                    P other = k.father == p ? k.mother : k.father;
                    if (other != null) {
                        spouses.add(other.id);
                    }
                }
                list.add(new FamilyPerson(p.id, p.name, "", p.gender, false, p.deceased,
                        p.father == null ? NIL : p.father.id, p.mother == null ? NIL : p.mother.id,
                        List.copyOf(spouses), p.kids.stream().map(k -> k.id).toList(), "",
                        p.birth, p.deceased ? p.birth + 20000 : null, 0, null, null));
            }
            if (reverse) {
                Collections.reverse(list);
            }
            return list;
        }
    }

    static TreeLayout layout(List<FamilyPerson> persons, UUID root) {
        return TreeLayout.family(persons, root, person -> {
            List<String> lines = new ArrayList<>();
            lines.add(person.name());
            lines.add(String.valueOf(person.birthDay()));
            return TreeLayout.card(person, lines, List.of(0, 0), s -> s.length() * 6);
        });
    }

    static final class Report {
        final String name;
        int overlaps;
        int coupleSide;
        int birthOrder;
        int birthExceptions;
        int anchors;
        int crossings;
        int collinear;
        int remoteSameLevel;
        int iconMisplaced;
        int barOverlaps;
        int ancestors;
        int siblings;
        int rootSpouses;
        int cases;
        int fallback;
        int fallbackErrors;
        int withCopies;
        int disconnected;
        int fragments;
        String fallbackReason = "";
        long millis;
        final List<String> details = new ArrayList<>();
        final List<String> dump = new ArrayList<>();

        Report(String name) {
            this.name = name;
        }

        int errors() {
            return overlaps + coupleSide + birthOrder + anchors + crossings + collinear + remoteSameLevel + iconMisplaced + barOverlaps
                    + ancestors + siblings + rootSpouses + disconnected + fragments;
        }

        void note(String text) {
            if (details.size() < 40) {
                details.add(text);
            }
        }

        @Override
        public String toString() {
            return String.format("%-22s err=%-3d overlap=%d side=%d birth=%d(exc %d) anchor=%d cross=%d collinear=%d remoteLevel=%d icon=%d barOverlap=%d asc=%d sib=%d rootSp=%d disc=%d frag=%d %dms",
                    name, errors(), overlaps, coupleSide, birthOrder, birthExceptions, anchors, crossings, collinear,
                    remoteSameLevel, iconMisplaced, barOverlaps, ancestors, siblings, rootSpouses, disconnected, fragments, millis)
                    + (cases > 0 ? " (" + cases + " cases, " + withCopies + " with repeated persons, " + fallback + " fallback with " + fallbackErrors + " err)" : "");
        }

        void add(Report o) {
            overlaps += o.overlaps;
            coupleSide += o.coupleSide;
            birthOrder += o.birthOrder;
            birthExceptions += o.birthExceptions;
            anchors += o.anchors;
            crossings += o.crossings;
            collinear += o.collinear;
            remoteSameLevel += o.remoteSameLevel;
            iconMisplaced += o.iconMisplaced;
            barOverlaps += o.barOverlaps;
            ancestors += o.ancestors;
            siblings += o.siblings;
            rootSpouses += o.rootSpouses;
            disconnected += o.disconnected;
            fragments += o.fragments;
            fallback += o.fallback;
            fallbackErrors += o.fallbackErrors;
            withCopies += o.withCopies;
            millis += o.millis;
        }
    }

    static boolean partners(FamilyPerson a, FamilyPerson b, Map<UUID, FamilyPerson> byId) {
        if (a.spouses().contains(b.id()) || b.spouses().contains(a.id())) {
            return true;
        }
        for (UUID c : a.children()) {
            FamilyPerson k = byId.get(c);
            if (k != null && (k.father().equals(b.id()) || k.mother().equals(b.id()))) {
                return true;
            }
        }
        return false;
    }

    static List<TreeLayout.Card> rowOf(TreeLayout layout, TreeLayout.Card card) {
        List<TreeLayout.Card> row = new ArrayList<>();
        for (TreeLayout.Card c : layout.cards) {
            if (c.y == card.y) {
                row.add(c);
            }
        }
        row.sort(Comparator.comparingInt(TreeLayout.Card::centerX));
        return row;
    }

    static boolean underCard(TreeLayout layout, int x, int y) {
        for (TreeLayout.Card c : layout.cards) {
            if (x > c.left() && x < c.right() && y > c.y && y < c.bottom()) {
                return true;
            }
        }
        return false;
    }

    static Report check(String name, List<FamilyPerson> persons, UUID rootId, boolean geneanet) {
        return check(name, persons, rootId, geneanet, false);
    }

    static long birth(FamilyPerson p) {
        return p.birthDay() == null ? Long.MAX_VALUE : p.birthDay();
    }

    static Report check(String name, List<FamilyPerson> persons, UUID rootId, boolean geneanet, boolean forceSvg) {
        Report r = new Report(name);
        long start = System.nanoTime();
        TreeLayout layout = layout(persons, rootId);
        r.millis = (System.nanoTime() - start) / 1_000_000;
        Map<UUID, FamilyPerson> byId = new HashMap<>();
        layout.cards.forEach(c -> byId.put(c.id(), c.person));

        List<TreeLayout.Card> cards = layout.cards;
        for (int i = 0; i < cards.size(); i++) {
            for (int j = i + 1; j < cards.size(); j++) {
                TreeLayout.Card a = cards.get(i);
                TreeLayout.Card b = cards.get(j);
                if (a.left() < b.right() && b.left() < a.right() && a.y < b.bottom() && b.y < a.bottom()) {
                    r.overlaps++;
                    r.note("overlap " + a.person.name() + " / " + b.person.name());
                }
            }
        }

        Map<TreeLayout.Card, List<TreeLayout.Card>> linked = linked(layout, byId);
        for (TreeLayout.Card a : cards) {
            List<TreeLayout.Card> mates = linked.getOrDefault(a, List.of());
            if (mates.isEmpty()) {
                continue;
            }
            for (TreeLayout.Card b : mates) {
                if (a.person.gender() == b.person.gender() || !TreeTexts.isFemale(b.person.gender())) {
                    continue;
                }
                long matesOfB = linked.getOrDefault(b, List.of()).size();
                boolean simple = mates.size() == 1 && matesOfB == 1;
                boolean hubMan = mates.size() > 1 && matesOfB == 1;
                boolean hubWoman = mates.size() == 1 && matesOfB > 1;
                if ((simple || hubMan || hubWoman) && a.centerX() > b.centerX()) {
                    r.coupleSide++;
                    r.note("side: " + a.person.name() + " right of " + b.person.name());
                }
            }
            if (mates.size() > 1) {
                List<UUID> order = new ArrayList<>(a.person.spouses());
                if (!order.isEmpty()) {
                    order.add(order.remove(0));
                }
                List<TreeLayout.Card> sorted = new ArrayList<>(mates);
                sorted.sort(Comparator.comparingInt(c -> Math.abs(c.centerX() - a.centerX())));
                boolean sameSide = mates.stream().allMatch(c -> Integer.signum(c.centerX() - a.centerX()) == Integer.signum(mates.get(0).centerX() - a.centerX()));
                boolean allSimple = mates.stream().allMatch(c -> linked.getOrDefault(c, List.of()).size() == 1);
                if (allSimple) {
                    List<UUID> got = sorted.stream().map(TreeLayout.Card::id).toList();
                    List<UUID> want = order.stream().filter(got::contains).toList();
                    if (!sameSide || !got.equals(want)) {
                        r.coupleSide++;
                        r.note("remarriage order/side wrong for " + a.person.name());
                    }
                }
            }
        }

        Map<UUID, List<UUID>> parentsOf = new HashMap<>();
        for (TreeLayout.Card c : cards) {
            FamilyPerson p = c.person;
            if (parentsOf.containsKey(p.id())) {
                continue;
            }
            List<UUID> list = parentsOf.computeIfAbsent(p.id(), k -> new ArrayList<>());
            if (byId.containsKey(p.father())) {
                list.add(p.father());
            }
            if (byId.containsKey(p.mother()) && !p.mother().equals(p.father())) {
                list.add(p.mother());
            }
        }
        for (TreeLayout.Card c : cards) {
            for (UUID kid : c.person.children()) {
                List<UUID> list = parentsOf.get(kid);
                if (list != null && !list.contains(c.id()) && list.size() < 2) {
                    list.add(c.id());
                }
            }
        }
        Set<UUID> blood = geneanet ? blood(layout, parentsOf) : null;
        if (geneanet) {
            Set<String> pairs = new HashSet<>();
            linked.forEach((a, list) -> list.forEach(b -> pairs.add(a.id() + "+" + b.id())));
            for (FamilyPerson a : byId.values()) {
                for (FamilyPerson b : byId.values()) {
                    boolean shown = walked.contains(a.id()) || walked.contains(b.id()) || parentPair(a, b, parentsOf, blood);
                    if (a.id().compareTo(b.id()) < 0 && shown && partners(a, b, byId) && !pairs.contains(a.id() + "+" + b.id())) {
                        r.coupleSide++;
                        r.note("no couple link between " + a.name() + " and " + b.name());
                    }
                }
            }
        }
        Map<String, List<TreeLayout.Card>> units = new LinkedHashMap<>();
        for (TreeLayout.Card c : cards) {
            if (blood != null && !blood.contains(c.id())) {
                continue;
            }
            List<String> keys = new ArrayList<>();
            parentsOf.get(c.id()).forEach(id -> keys.add(id.toString()));
            if (keys.isEmpty()) {
                continue;
            }
            Collections.sort(keys);
            units.computeIfAbsent(String.join("+", keys), k -> new ArrayList<>()).add(c);
        }
        for (List<TreeLayout.Card> kids : units.values()) {
            Map<String, List<TreeLayout.Card>> byRow = new TreeMap<>();
            kids.stream().filter(k -> drop(layout, k) != null).forEach(k -> byRow.computeIfAbsent(barKey(layout, k), y -> new ArrayList<>()).add(k));
            for (List<TreeLayout.Card> row : byRow.values()) {
                row.sort(Comparator.comparingInt(TreeLayout.Card::centerX));
                for (int i = 0; i < row.size(); i++) {
                    for (int j = i + 1; j < row.size(); j++) {
                        if (birth(row.get(i).person) > birth(row.get(j).person)) {
                            TreeLayout.Card moved = j == row.size() - 1 && marriedIntoFamily(row.get(j), byId) || i == 0 && marriedIntoFamily(row.get(i), byId) ? row.get(j) : null;
                            if (moved != null) {
                                r.birthExceptions++;
                            } else {
                                r.birthOrder++;
                                r.note("birth: " + row.get(i).person.name() + " before " + row.get(j).person.name());
                            }
                        }
                    }
                }
            }
        }

        List<TreeLayout.Segment> segs = layout.segments;
        parentCards.clear();
        Map<String, Set<TreeLayout.Segment>> barsOfUnit = new HashMap<>();
        Set<UUID> dropped = new HashSet<>();
        List<TreeLayout.Card> undropped = new ArrayList<>();
        for (Map.Entry<String, List<TreeLayout.Card>> e : units.entrySet()) {
            for (TreeLayout.Card kid : e.getValue()) {
                FamilyPerson p = kid.person;
                List<UUID> ps = parentsOf.get(p.id());
                TreeLayout.Segment drop = drop(layout, kid);
                if (drop == null) {
                    undropped.add(kid);
                    continue;
                }
                dropped.add(kid.id());
                int barY = Math.min(drop.y1(), drop.y2());
                int lo = kid.centerX();
                int hi = kid.centerX();
                TreeLayout.Segment bar = null;
                for (TreeLayout.Segment s : segs) {
                    if (s.y1() == s.y2() && s.y1() == barY && Math.min(s.x1(), s.x2()) <= kid.centerX() && Math.max(s.x1(), s.x2()) >= kid.centerX()
                            && !isCoupleLink(layout, s)) {
                        if (bar != null) {
                            r.anchors++;
                            r.note("ambiguous bar for " + p.name());
                        }
                        bar = s;
                        lo = Math.min(s.x1(), s.x2());
                        hi = Math.max(s.x1(), s.x2());
                    }
                }
                TreeLayout.Segment up = null;
                int found = 0;
                for (TreeLayout.Segment s : segs) {
                    if (s.x1() == s.x2() && Math.max(s.y1(), s.y2()) == barY && s.x1() >= lo && s.x1() <= hi && Math.min(s.y1(), s.y2()) < barY) {
                        up = s;
                        found++;
                    }
                }
                if (found != 1) {
                    r.anchors++;
                    r.note("anchor drops=" + found + " for " + p.name());
                    continue;
                }
                int ax = up.x1();
                int ay = Math.min(up.y1(), up.y2());
                TreeLayout.Card[] found2 = parentsAt(layout, ps, ax, ay);
                TreeLayout.Card father = found2[0];
                TreeLayout.Card mother = found2[1];
                parentCards.put(kid, mother == null ? List.of(father) : List.of(father, mother));
                if (bar != null) {
                    int h1 = System.identityHashCode(father);
                    int h2 = System.identityHashCode(mother);
                    barsOfUnit.computeIfAbsent(e.getKey() + "@" + Math.min(h1, h2) + "," + Math.max(h1, h2), k -> new HashSet<>()).add(bar);
                }
                if (father != null && mother != null && father.y == mother.y) {
                    TreeLayout.Card left = father.centerX() < mother.centerX() ? father : mother;
                    TreeLayout.Card right = left == father ? mother : father;
                    boolean onLink = false;
                    for (TreeLayout.Segment s : segs) {
                        if (s.y1() == s.y2() && s.y1() == ay && Math.min(s.x1(), s.x2()) == left.right() && Math.max(s.x1(), s.x2()) == right.left()) {
                            onLink = true;
                        }
                    }
                    List<TreeLayout.Card> row = rowOf(layout, left);
                    int li = row.indexOf(left);
                    int ri = row.indexOf(right);
                    boolean ok = onLink && ax > left.right() && ax < right.left() && !underCard(layout, ax, ay);
                    if (ri == li + 1) {
                        ok &= Math.abs(ax - (left.right() + right.left()) / 2) <= 1;
                    } else {
                        TreeLayout.Card gapL = null;
                        for (TreeLayout.Card c : row) {
                            if (c.right() <= ax) {
                                gapL = c;
                            }
                        }
                        int gi = row.indexOf(gapL);
                        ok &= gi == li || gi + 1 == ri;
                    }
                    if (!ok) {
                        r.anchors++;
                        r.note("bad anchor for " + p.name() + " at " + ax + "," + ay);
                    }
                } else {
                    TreeLayout.Card parent = father != null ? father : mother;
                    if (father != null && mother != null) {
                        parent = father.y > mother.y ? father : mother;
                    }
                    if (!(ax == parent.centerX() && ay == parent.bottom())) {
                        r.anchors++;
                        r.note("bad single anchor for " + p.name());
                    }
                }
            }
        }
        for (TreeLayout.Card kid : undropped) {
            if (!dropped.contains(kid.id())) {
                r.anchors++;
                r.note("no drop for " + kid.person.name());
            }
        }

        for (int i = 0; i < segs.size(); i++) {
            for (int j = i + 1; j < segs.size(); j++) {
                TreeLayout.Segment a = segs.get(i);
                TreeLayout.Segment b = segs.get(j);
                boolean ah = a.y1() == a.y2();
                boolean bh = b.y1() == b.y2();
                if (ah != bh) {
                    TreeLayout.Segment h = ah ? a : b;
                    TreeLayout.Segment v = ah ? b : a;
                    int hx0 = Math.min(h.x1(), h.x2());
                    int hx1 = Math.max(h.x1(), h.x2());
                    int vy0 = Math.min(v.y1(), v.y2());
                    int vy1 = Math.max(v.y1(), v.y2());
                    if (v.x1() > hx0 && v.x1() < hx1 && h.y1() > vy0 && h.y1() < vy1 && !underCard(layout, v.x1(), h.y1())) {
                        r.crossings++;
                        r.note("cross at " + v.x1() + "," + h.y1());
                    }
                } else if (ah) {
                    if (a.y1() == b.y1()) {
                        int lo = Math.max(Math.min(a.x1(), a.x2()), Math.min(b.x1(), b.x2()));
                        int hi = Math.min(Math.max(a.x1(), a.x2()), Math.max(b.x1(), b.x2()));
                        if (hi > lo && visible(layout, lo, hi, a.y1(), true)) {
                            boolean la = isCoupleLink(layout, a);
                            boolean lb = isCoupleLink(layout, b);
                            if (la && lb) {
                                r.remoteSameLevel++;
                            } else {
                                r.collinear++;
                            }
                            r.note("collinear H at y=" + a.y1() + " x " + lo + ".." + hi);
                        }
                    }
                } else if (a.x1() == b.x1()) {
                    int lo = Math.max(Math.min(a.y1(), a.y2()), Math.min(b.y1(), b.y2()));
                    int hi = Math.min(Math.max(a.y1(), a.y2()), Math.max(b.y1(), b.y2()));
                    if (hi > lo) {
                        r.collinear++;
                        r.note("collinear V at x=" + a.x1());
                    }
                }
            }
        }

        for (TreeLayout.Mark mark : layout.marks) {
            TreeLayout.Card left = null;
            TreeLayout.Card right = null;
            for (TreeLayout.Card c : cards) {
                if (mark.y() >= c.y && mark.y() <= c.bottom()) {
                    if (c.right() <= mark.x() && (left == null || c.right() > left.right())) {
                        left = c;
                    }
                    if (c.left() >= mark.x() && (right == null || c.left() < right.left())) {
                        right = c;
                    }
                }
            }
            boolean ok = false;
            for (TreeLayout.Segment link : segs) {
                if (link.y1() != link.y2() || link.y1() != mark.y() || Math.min(link.x1(), link.x2()) > mark.x() || Math.max(link.x1(), link.x2()) < mark.x()) {
                    continue;
                }
                TreeLayout.Card a = null;
                TreeLayout.Card b = null;
                for (TreeLayout.Card c : cards) {
                    if (mark.y() > c.y && mark.y() < c.bottom()) {
                        if (c.right() == Math.min(link.x1(), link.x2())) {
                            a = c;
                        }
                        if (c.left() == Math.max(link.x1(), link.x2())) {
                            b = c;
                        }
                    }
                }
                if (a == null || b == null) {
                    continue;
                }
                if (a == left && b == right) {
                    ok = true;
                    continue;
                }
                long pa = linked.getOrDefault(a, List.of()).size();
                long pb = linked.getOrDefault(b, List.of()).size();
                TreeLayout.Card hub = pa >= pb ? a : b;
                if (left == hub || right == hub) {
                    ok = true;
                }
            }
            if (!ok) {
                r.iconMisplaced++;
                r.note("icon between " + (left == null ? "-" : left.person.name()) + " and " + (right == null ? "-" : right.person.name()));
            }
        }

        List<Map.Entry<String, Set<TreeLayout.Segment>>> barList = new ArrayList<>(barsOfUnit.entrySet());
        for (int i = 0; i < barList.size(); i++) {
            for (int j = i + 1; j < barList.size(); j++) {
                for (TreeLayout.Segment a : barList.get(i).getValue()) {
                    for (TreeLayout.Segment b : barList.get(j).getValue()) {
                        if (Math.abs(a.y1() - b.y1()) > 20) {
                            continue;
                        }
                        int lo = Math.max(Math.min(a.x1(), a.x2()), Math.min(b.x1(), b.x2()));
                        int hi = Math.min(Math.max(a.x1(), a.x2()), Math.max(b.x1(), b.x2()));
                        if (hi >= lo - 2) {
                            r.barOverlaps++;
                            r.note("bars overlap x " + lo + ".." + hi + " y " + a.y1() + "/" + b.y1() + " " + names(byId, barList.get(i).getKey()) + " / " + names(byId, barList.get(j).getKey()));
                        }
                    }
                }
            }
        }
        if (geneanet) {
            geneanet(r, layout);
            connectivity(r, layout, linked, parentsOf);
        }
        if (layout.cards.stream().anyMatch(c -> c.copy)) {
            r.withCopies = 1;
        }
        if (!layout.pedigree) {
            r.fallback = 1;
            r.fallbackReason = layout.fallback;
            r.fallbackErrors = r.errors();
        }
        if (forceSvg || r.errors() > 0 || !geneanet || name.endsWith("_u2d2")) {
            svg(name, layout);
        }
        TreeMap<Integer, List<TreeLayout.Card>> rows = new TreeMap<>();
        cards.forEach(c -> rows.computeIfAbsent(c.y, y -> new ArrayList<>()).add(c));
        for (List<TreeLayout.Card> row : rows.values()) {
            row.sort(Comparator.comparingInt(TreeLayout.Card::centerX));
            StringBuilder sb = new StringBuilder("  |");
            row.forEach(c -> sb.append(' ').append(c.person.name()).append(c.copy ? "*" : "").append('@').append(c.centerX()));
            r.dump.add(sb.toString());
        }
        return r;
    }

    static void connectivity(Report r, TreeLayout layout, Map<TreeLayout.Card, List<TreeLayout.Card>> linked, Map<UUID, List<UUID>> parentsOf) {
        Map<TreeLayout.Card, Set<TreeLayout.Card>> edges = new HashMap<>();
        linked.forEach((a, list) -> list.forEach(b -> {
            edges.computeIfAbsent(a, k -> new HashSet<>()).add(b);
            edges.computeIfAbsent(b, k -> new HashSet<>()).add(a);
        }));
        for (TreeLayout.Card kid : layout.cards) {
            List<TreeLayout.Card> above = parentCards.get(kid);
            if (above == null) {
                above = traceParents(layout, kid, parentsOf.getOrDefault(kid.id(), List.of()));
            }
            for (TreeLayout.Card parent : above) {
                if (parent != null) {
                    edges.computeIfAbsent(kid, k -> new HashSet<>()).add(parent);
                    edges.computeIfAbsent(parent, k -> new HashSet<>()).add(kid);
                }
            }
        }
        Set<TreeLayout.Card> seen = new HashSet<>();
        int components = 0;
        List<TreeLayout.Card> order = new ArrayList<>(layout.cards);
        order.remove(layout.root);
        order.add(0, layout.root);
        for (TreeLayout.Card start : order) {
            if (seen.contains(start)) {
                continue;
            }
            Deque<TreeLayout.Card> todo = new ArrayDeque<>(List.of(start));
            List<String> names = new ArrayList<>();
            while (!todo.isEmpty()) {
                TreeLayout.Card c = todo.poll();
                if (seen.add(c)) {
                    names.add(c.person.name() + (c.copy ? "*" : ""));
                    todo.addAll(edges.getOrDefault(c, Set.of()));
                }
            }
            if (components++ > 0) {
                r.fragments++;
                r.disconnected += names.size();
                r.note("fragment not linked to root: " + names);
            }
        }
    }

    static List<TreeLayout.Card> traceParents(TreeLayout layout, TreeLayout.Card kid, List<UUID> ps) {
        TreeLayout.Segment drop = drop(layout, kid);
        if (drop == null || ps.isEmpty()) {
            return List.of();
        }
        int barY = Math.min(drop.y1(), drop.y2());
        int lo = kid.centerX();
        int hi = kid.centerX();
        for (TreeLayout.Segment s : layout.segments) {
            if (s.y1() == s.y2() && s.y1() == barY && Math.min(s.x1(), s.x2()) <= kid.centerX() && Math.max(s.x1(), s.x2()) >= kid.centerX()
                    && !isCoupleLink(layout, s)) {
                lo = Math.min(s.x1(), s.x2());
                hi = Math.max(s.x1(), s.x2());
            }
        }
        for (TreeLayout.Segment s : layout.segments) {
            if (s.x1() == s.x2() && Math.max(s.y1(), s.y2()) == barY && s.x1() >= lo && s.x1() <= hi && Math.min(s.y1(), s.y2()) < barY) {
                return Arrays.asList(parentsAt(layout, ps, s.x1(), Math.min(s.y1(), s.y2())));
            }
        }
        return List.of();
    }

    static final Set<UUID> walked = new HashSet<>();

    static boolean parentPair(FamilyPerson a, FamilyPerson b, Map<UUID, List<UUID>> parentsOf, Set<UUID> blood) {
        for (Map.Entry<UUID, List<UUID>> e : parentsOf.entrySet()) {
            List<UUID> ps = e.getValue();
            if (blood.contains(e.getKey()) && ps.contains(a.id()) && ps.contains(b.id())) {
                return true;
            }
        }
        return false;
    }

    static Set<UUID> blood(TreeLayout layout, Map<UUID, List<UUID>> parentsOf) {
        walked.clear();
        Set<UUID> blood = new HashSet<>();
        UUID root = layout.root.id();
        Deque<UUID> up = new ArrayDeque<>(List.of(root));
        while (!up.isEmpty()) {
            UUID id = up.poll();
            if (blood.add(id)) {
                up.addAll(parentsOf.get(id));
            }
        }
        Deque<UUID> walk = new ArrayDeque<>(List.of(root));
        for (Map.Entry<UUID, List<UUID>> e : parentsOf.entrySet()) {
            for (UUID p : parentsOf.get(root)) {
                if (e.getValue().contains(p)) {
                    walk.add(e.getKey());
                }
            }
        }
        Set<UUID> seen = new HashSet<>();
        while (!walk.isEmpty()) {
            UUID id = walk.poll();
            if (seen.add(id)) {
                blood.add(id);
                walked.add(id);
                for (Map.Entry<UUID, List<UUID>> e : parentsOf.entrySet()) {
                    if (e.getValue().contains(id)) {
                        walk.add(e.getKey());
                    }
                }
            }
        }
        return blood;
    }

    static final Map<TreeLayout.Card, List<TreeLayout.Card>> parentCards = new HashMap<>();

    static String names(Map<UUID, FamilyPerson> byId, String key) {
        StringBuilder sb = new StringBuilder();
        for (String part : key.split("@")[0].split("[+]")) {
            FamilyPerson p = byId.get(UUID.fromString(part));
            sb.append(p == null ? "?" : p.name()).append('+');
        }
        return sb.toString();
    }

    static String barKey(TreeLayout layout, TreeLayout.Card kid) {
        TreeLayout.Segment drop = drop(layout, kid);
        int barY = Math.min(drop.y1(), drop.y2());
        for (TreeLayout.Segment s : layout.segments) {
            if (s.y1() == s.y2() && s.y1() == barY && Math.min(s.x1(), s.x2()) <= kid.centerX() && Math.max(s.x1(), s.x2()) >= kid.centerX()
                    && !isCoupleLink(layout, s)) {
                return kid.y + ":" + barY + ":" + Math.min(s.x1(), s.x2());
            }
        }
        return kid.y + ":" + barY + ":" + kid.centerX();
    }

    static int svgBudget = Integer.MAX_VALUE;

    static TreeLayout.Segment drop(TreeLayout layout, TreeLayout.Card kid) {
        TreeLayout.Segment drop = null;
        for (TreeLayout.Segment s : layout.segments) {
            if (s.x1() == s.x2() && s.x1() == kid.centerX() && Math.max(s.y1(), s.y2()) == kid.y) {
                drop = s;
            }
        }
        return drop;
    }

    static boolean link(TreeLayout layout, TreeLayout.Card left, TreeLayout.Card right, int y) {
        for (TreeLayout.Segment s : layout.segments) {
            if (s.y1() == s.y2() && s.y1() == y && Math.min(s.x1(), s.x2()) == left.right() && Math.max(s.x1(), s.x2()) == right.left()) {
                return true;
            }
        }
        return false;
    }

    static TreeLayout.Card[] parentsAt(TreeLayout layout, List<UUID> ps, int ax, int ay) {
        List<TreeLayout.Card> fathers = new ArrayList<>();
        List<TreeLayout.Card> mothers = new ArrayList<>();
        for (TreeLayout.Card c : layout.cards) {
            if (c.id().equals(ps.get(0))) {
                fathers.add(c);
            }
            if (ps.size() > 1 && c.id().equals(ps.get(1))) {
                mothers.add(c);
            }
        }
        if (mothers.isEmpty()) {
            for (TreeLayout.Card f : fathers) {
                if (f.centerX() == ax && f.bottom() == ay) {
                    return new TreeLayout.Card[]{f, null};
                }
            }
            return new TreeLayout.Card[]{fathers.get(0), null};
        }
        TreeLayout.Card[] fallback = {fathers.get(0), mothers.get(0)};
        for (TreeLayout.Card f : fathers) {
            for (TreeLayout.Card m : mothers) {
                if (f.y != m.y) {
                    continue;
                }
                TreeLayout.Card left = f.centerX() < m.centerX() ? f : m;
                TreeLayout.Card right = left == f ? m : f;
                if (ay > f.y && ay < f.bottom() && ax > left.right() && ax < right.left() && link(layout, left, right, ay)) {
                    return new TreeLayout.Card[]{f, m};
                }
                fallback = new TreeLayout.Card[]{f, m};
            }
        }
        return fallback;
    }

    static Map<TreeLayout.Card, List<TreeLayout.Card>> linked(TreeLayout layout, Map<UUID, FamilyPerson> byId) {
        Map<TreeLayout.Card, List<TreeLayout.Card>> linked = new HashMap<>();
        for (TreeLayout.Segment s : layout.segments) {
            if (s.y1() != s.y2()) {
                continue;
            }
            TreeLayout.Card left = null;
            TreeLayout.Card right = null;
            for (TreeLayout.Card c : layout.cards) {
                if (s.y1() > c.y && s.y1() < c.bottom()) {
                    if (c.right() == Math.min(s.x1(), s.x2())) {
                        left = c;
                    }
                    if (c.left() == Math.max(s.x1(), s.x2())) {
                        right = c;
                    }
                }
            }
            if (left != null && right != null && left.y == right.y && partners(left.person, right.person, byId)) {
                linked.computeIfAbsent(left, k -> new ArrayList<>()).add(right);
                linked.computeIfAbsent(right, k -> new ArrayList<>()).add(left);
            }
        }
        return linked;
    }

    static UUID present(TreeLayout layout, UUID id) {
        return layout.byId.containsKey(id) ? id : NIL;
    }

    static void geneanet(Report r, TreeLayout layout) {
        TreeLayout.Card root = layout.root;
        Deque<TreeLayout.Card> todo = new ArrayDeque<>(List.of(root));
        Set<TreeLayout.Card> seen = new HashSet<>();
        while (!todo.isEmpty()) {
            TreeLayout.Card x = todo.poll();
            if (!seen.add(x)) {
                continue;
            }
            List<TreeLayout.Card> above = parentCards.getOrDefault(x, List.of());
            TreeLayout.Card f = null;
            TreeLayout.Card m = null;
            for (TreeLayout.Card c : above) {
                if (c.id().equals(x.person.father())) {
                    f = c;
                } else if (c.id().equals(x.person.mother())) {
                    m = c;
                }
            }
            if (f != null && m != null) {
                int ax = (f.right() + m.left()) / 2;
                if (f.y != m.y || f.centerX() >= m.centerX() || Math.abs(ax - x.centerX()) > 1) {
                    r.ancestors++;
                    r.note("parents of " + x.person.name() + " not father-left/mother-right centred");
                }
            } else if (f != null || m != null) {
                TreeLayout.Card p = f != null ? f : m;
                if (Math.abs(p.centerX() - x.centerX()) > 1) {
                    r.ancestors++;
                    r.note("parent of " + x.person.name() + " not centred");
                }
            }
            if (f != null) {
                todo.add(f);
            }
            if (m != null) {
                todo.add(m);
            }
        }

        List<TreeLayout.Card> row = rowOf(layout, root);
        UUID fa = present(layout, root.person.father());
        UUID mo = present(layout, root.person.mother());
        List<TreeLayout.Card> full = new ArrayList<>();
        List<TreeLayout.Card> fatherSide = new ArrayList<>();
        List<TreeLayout.Card> motherSide = new ArrayList<>();
        for (TreeLayout.Card c : row) {
            if (c.copy) {
                continue;
            }
            UUID cf = present(layout, c.person.father());
            UUID cm = present(layout, c.person.mother());
            boolean f = !fa.equals(NIL) && cf.equals(fa);
            boolean m = !mo.equals(NIL) && cm.equals(mo);
            if (c == root || (f || m) && cf.equals(fa) && cm.equals(mo)) {
                full.add(c);
            } else if (f) {
                fatherSide.add(c);
            } else if (m) {
                motherSide.add(c);
            }
        }
        for (int i = 0; i + 1 < full.size(); i++) {
            if (birth(full.get(i).person) > birth(full.get(i + 1).person)) {
                r.siblings++;
                r.note("siblings out of birth order: " + full.get(i).person.name() + " / " + full.get(i + 1).person.name());
            }
        }
        int lo = full.stream().mapToInt(TreeLayout.Card::centerX).min().orElse(0);
        int hi = full.stream().mapToInt(TreeLayout.Card::centerX).max().orElse(0);
        for (TreeLayout.Card c : fatherSide) {
            if (c.centerX() > lo) {
                r.siblings++;
                r.note("father-side half-sibling " + c.person.name() + " among full siblings");
            }
        }
        for (TreeLayout.Card c : motherSide) {
            if (c.centerX() < hi) {
                r.siblings++;
                r.note("mother-side half-sibling " + c.person.name() + " among full siblings");
            }
        }

        Map<UUID, FamilyPerson> people = new HashMap<>();
        layout.cards.forEach(c -> people.put(c.id(), c.person));
        List<TreeLayout.Card> spouses = new ArrayList<>(linked(layout, people).getOrDefault(root, List.of()));
        if (!spouses.isEmpty()) {
            int want = TreeTexts.isFemale(root.person.gender()) ? -1 : 1;
            int first = Integer.signum(spouses.get(0).centerX() - root.centerX());
            int far = 0;
            for (TreeLayout.Card s : spouses) {
                boolean opposite = TreeTexts.isFemale(s.person.gender()) != TreeTexts.isFemale(root.person.gender());
                int side = Integer.signum(s.centerX() - root.centerX());
                if (opposite && side != want || side != first) {
                    r.rootSpouses++;
                    r.note("root spouse " + s.person.name() + " on wrong side");
                }
                far = Math.max(far, Math.abs(s.centerX() - root.centerX()));
            }
            for (TreeLayout.Card c : row) {
                boolean sibling = full.contains(c) || fatherSide.contains(c) || motherSide.contains(c);
                if (c != root && sibling && Integer.signum(c.centerX() - root.centerX()) == first && Math.abs(c.centerX() - root.centerX()) < far) {
                    r.rootSpouses++;
                    r.note("sibling " + c.person.name() + " between root and its spouses");
                }
            }
        }
    }

    static Map<UUID, FamilyPerson> index(List<FamilyPerson> persons) {
        Map<UUID, FamilyPerson> map = new LinkedHashMap<>();
        persons.forEach(p -> map.put(p.id(), p));
        return map;
    }

    static List<FamilyPerson> select(Map<UUID, FamilyPerson> all, UUID root, int up, int down) {
        TreeSelection.Graph graph = new TreeSelection.Graph() {
            @Override
            public List<UUID> parents(UUID id) {
                FamilyPerson p = all.get(id);
                List<UUID> list = new ArrayList<>();
                if (!p.father().equals(NIL)) {
                    list.add(p.father());
                }
                if (!p.mother().equals(NIL)) {
                    list.add(p.mother());
                }
                return list;
            }

            @Override
            public List<UUID> children(UUID id) {
                return all.get(id).children();
            }

            @Override
            public List<UUID> spouses(UUID id) {
                return all.get(id).spouses();
            }
        };
        List<FamilyPerson> list = new ArrayList<>();
        for (UUID id : TreeSelection.select(graph, root, up, down, 400)) {
            list.add(all.get(id));
        }
        return list;
    }

    static final int[] DEPTHS = {1, 2, 3, -1};

    static Report selectionCases(String name, Fam fam, List<P> roots) {
        Report total = new Report(name);
        Map<UUID, FamilyPerson> all = index(fam.persons(false));
        int shown = 0;
        int cases = 0;
        for (P root : roots) {
            for (int up : DEPTHS) {
                for (int down : DEPTHS) {
                    String id = name + "@" + root.name + "_u" + (up < 0 ? "X" : up) + "d" + (down < 0 ? "X" : down);
                    Report r = check(id, select(all, root.id, up, down), root.id, true);
                    cases++;
                    total.add(r);
                    if (r.errors() > 0 && shown++ < SHOWN) {
                        System.out.println("  " + r + (r.fallback > 0 ? " FALLBACK" : " TREE"));
                        r.details.stream().limit(8).forEach(d -> System.out.println("      " + d));
                        if (System.getenv("TREE_DUMP") != null) {
                            r.dump.forEach(System.out::println);
                        }
                    }
                }
            }
        }
        total.cases = cases;
        return total;
    }

    static final int SHOWN = System.getenv("TREE_SHOWN") != null ? Integer.parseInt(System.getenv("TREE_SHOWN")) : 3;

    static boolean marriedIntoFamily(TreeLayout.Card kid, Map<UUID, FamilyPerson> byId) {
        for (UUID s : kid.person.spouses()) {
            FamilyPerson spouse = byId.get(s);
            if (spouse != null && (byId.containsKey(spouse.father()) || byId.containsKey(spouse.mother()))) {
                return true;
            }
        }
        return false;
    }

    static boolean isCoupleLink(TreeLayout layout, TreeLayout.Segment s) {
        for (TreeLayout.Card c : layout.cards) {
            if (s.y1() > c.y && s.y1() < c.bottom() && (Math.min(s.x1(), s.x2()) == c.right() || Math.max(s.x1(), s.x2()) == c.left())) {
                return true;
            }
        }
        return false;
    }

    static boolean visible(TreeLayout layout, int lo, int hi, int y, boolean horizontal) {
        for (int x = lo + 1; x < hi; x++) {
            if (!underCard(layout, x, y)) {
                return true;
            }
        }
        return false;
    }

    static Path svgDir;

    static void svg(String name, TreeLayout layout) {
        if (svgDir == null || svgBudget-- <= 0) {
            return;
        }
        int ox = -layout.minX + 20;
        int oy = -layout.minY + 20;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("<svg xmlns='http://www.w3.org/2000/svg' width='%d' height='%d' font-family='monospace' font-size='9'>%n",
                layout.maxX - layout.minX + 40, layout.maxY - layout.minY + 40));
        sb.append("<rect width='100%' height='100%' fill='#222'/>\n");
        for (TreeLayout.Segment s : layout.segments) {
            sb.append(String.format("<line x1='%d' y1='%d' x2='%d' y2='%d' stroke='#%06X' %s/>%n", s.x1() + ox, s.y1() + oy, s.x2() + ox, s.y2() + oy,
                    s.color() & 0xFFFFFF, s.dashed() ? "stroke-dasharray='3,2'" : ""));
        }
        for (TreeLayout.Mark m : layout.marks) {
            sb.append(String.format("<circle cx='%d' cy='%d' r='4' fill='none' stroke='%s'/>%n", m.x() + ox, m.y() + oy, "married".equals(m.icon()) ? "gold" : "#aaa"));
        }
        for (TreeLayout.Card c : layout.cards) {
            String color = TreeTexts.isFemale(c.person.gender()) ? "#f5a" : "#5af";
            sb.append(String.format("<rect x='%d' y='%d' width='%d' height='%d' fill='#333' stroke='%s'%s/>%n", c.left() + ox, c.y + oy, c.width, c.height, color, c.copy ? " stroke-dasharray='4,2'" : ""));
            sb.append(String.format("<text x='%d' y='%d' fill='%s'>%s%s</text>%n", c.left() + ox + 4, c.y + oy + 11, color, c.person.name(), c.person.deceased() ? " +" : ""));
            sb.append(String.format("<text x='%d' y='%d' fill='#ccc'>%s</text>%n", c.left() + ox + 4, c.y + oy + 21, c.person.birthDay()));
        }
        sb.append("</svg>\n");
        try {
            Files.createDirectories(svgDir);
            Files.writeString(svgDir.resolve(name.replaceAll("[^A-Za-z0-9_@.-]", "_") + ".svg"), sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static Fam simple() {
        Fam f = new Fam();
        P a = f.m("Adam", 100);
        P b = f.f("Berthe", 110);
        f.marry(a, b);
        f.child(a, b, "Cid", MALE, 300);
        f.child(a, b, "Dora", FEMALE, 200);
        f.child(a, b, "Eli", MALE, 400);
        return f;
    }

    static Fam fourGen() {
        Fam f = new Fam();
        P a = f.m("Arno", 0);
        P b = f.f("Bea", 5);
        f.marry(a, b);
        long t = 100;
        List<P> gen = List.of(a);
        List<P> wives = List.of(b);
        int n = 0;
        for (int g = 1; g <= 3; g++) {
            List<P> next = new ArrayList<>();
            List<P> nextSpouses = new ArrayList<>();
            for (int i = 0; i < gen.size(); i++) {
                P father = gen.get(i).gender == MALE ? gen.get(i) : wives.get(i);
                P mother = father == gen.get(i) ? wives.get(i) : gen.get(i);
                int kids = g == 3 ? 2 : 3;
                for (int k = 0; k < kids; k++) {
                    int gender = (n + k) % 2 == 0 ? MALE : FEMALE;
                    P c = f.child(father, mother, "G" + g + "_" + (n++), gender, t * g + k * 10 + i);
                    if (g < 3 && k != 1) {
                        P s = gender == MALE ? f.f("Sf" + n, c.birth + 1) : f.m("Sm" + n, c.birth + 1);
                        f.marry(c, s);
                        next.add(c);
                        nextSpouses.add(s);
                    }
                }
            }
            gen = next;
            wives = nextSpouses;
        }
        return f;
    }

    static Fam niklas(boolean niklasYoung, boolean grand) {
        Fam f = new Fam();
        P thomas = f.m("?Thomas", 10);
        P marlene = f.f("Marlene", 12);
        f.marry(thomas, marlene);
        P kwan = f.m("Kwan", 11);
        P viola = f.f("Viola", 13);
        f.marry(kwan, viola);
        P domna;
        P niklas;
        if (niklasYoung) {
            domna = f.child(kwan, viola, "Domna", FEMALE, 100);
            niklas = f.child(kwan, viola, "Niklas", MALE, 110);
        } else {
            niklas = f.child(kwan, viola, "Niklas", MALE, 100);
            domna = f.child(kwan, viola, "Domna", FEMALE, 110);
        }
        P orly;
        P wald;
        if (niklasYoung) {
            wald = f.child(thomas, marlene, "Waldfriede", FEMALE, 101);
            orly = f.child(thomas, marlene, "Orly", MALE, 111);
        } else {
            orly = f.child(thomas, marlene, "Orly", MALE, 101);
            wald = f.child(thomas, marlene, "Waldfriede", FEMALE, 111);
        }
        f.marry(niklas, wald);
        f.child(niklas, wald, "Nk1", MALE, 200);
        f.child(niklas, wald, "Nk2", FEMALE, 210);
        P dh = f.m("DomnaHus", 101);
        f.marry(dh, domna);
        f.child(dh, domna, "Dk1", FEMALE, 205);
        P ow = f.f("OrlyWife", 105);
        f.marry(orly, ow);
        f.child(orly, ow, "Ok1", MALE, 206);
        if (grand) {
            P g1 = f.m("GpKwan", -100);
            P g2 = f.f("GmKwan", -95);
            f.marry(g1, g2);
            kwan.father = g1;
            kwan.mother = g2;
            g1.kids.add(kwan);
            g2.kids.add(kwan);
            f.child(g1, g2, "KwanSis", FEMALE, 20);
            P g3 = f.m("GpThomas", -101);
            P g4 = f.f("GmThomas", -96);
            f.marry(g3, g4);
            f.child(g3, g4, "ThomasBro", MALE, 5);
            thomas.father = g3;
            thomas.mother = g4;
            g3.kids.add(thomas);
            g4.kids.add(thomas);
            P g5 = f.m("GpViola", -99);
            P g6 = f.f("GmViola", -97);
            f.marry(g5, g6);
            viola.father = g5;
            viola.mother = g6;
            g5.kids.add(viola);
            g6.kids.add(viola);
            f.child(g5, g6, "ViolaBro", MALE, 30);
        }
        f.root = niklas;
        return f;
    }

    static Fam corneliea() {
        Fam f = new Fam();
        P cp = f.m("CornPa", -100);
        P cm = f.f("CornMa", -90);
        f.marry(cp, cm);
        P c = f.child(cp, cm, "Corneliea", FEMALE, 10);
        f.child(cp, cm, "CornBro", MALE, 20);
        P k = f.m("Konstantin", 5);
        P fr = f.m("Fredrick", 8);
        f.marry(c, k);
        f.child(k, c, "KonstKid1", MALE, 100);
        f.child(k, c, "KonstKid2", FEMALE, 110);
        k.deceased = true;
        k.current = null;
        f.marry(c, fr);
        f.child(fr, c, "FredKid1", FEMALE, 150);
        f.child(fr, c, "FredKid2", MALE, 160);
        f.root = c;
        return f;
    }

    static Fam fabien() {
        Fam f = new Fam();
        P fp = f.m("FabPa", -100);
        P fm = f.f("FabMa", -90);
        f.marry(fp, fm);
        P fab = f.child(fp, fm, "Fabien", MALE, 10);
        f.child(fp, fm, "FabSis", FEMALE, 15);
        P w1 = f.f("Wife1", 12);
        P w2 = f.f("Wife2", 30);
        P w2p = f.m("W2Pa", -80);
        P w2m = f.f("W2Ma", -70);
        f.marry(w2p, w2m);
        w2.father = w2p;
        w2.mother = w2m;
        w2p.kids.add(w2);
        w2m.kids.add(w2);
        f.child(w2p, w2m, "W2Bro", MALE, 40);
        f.marry(fab, w1);
        f.child(fab, w1, "W1Kid1", MALE, 100);
        f.child(fab, w1, "W1Kid2", FEMALE, 105);
        f.child(fab, w1, "W1Kid3", FEMALE, 108);
        w1.deceased = true;
        f.marry(fab, w2);
        f.child(fab, w2, "W2Kid1", MALE, 150);
        f.child(fab, w2, "W2Kid2", MALE, 155);
        f.root = fab;
        return f;
    }

    static Fam remarriedAncestors() {
        Fam f = new Fam();
        P thor = f.m("Thor", 100);
        P fata = f.f("Fata", 110);
        f.marry(thor, fata);
        P timo = f.m("Timo", 120);
        P berna = f.f("Berna", 125);
        f.marry(timo, berna);
        P emano = f.m("Emano", 130);
        P snez = f.f("Snez", 135);
        f.marry(emano, snez);
        P dimo = f.child(thor, fata, "Dimo", MALE, 300);
        f.child(thor, fata, "DimoSis", FEMALE, 310);
        P flo = f.child(timo, berna, "Flo", MALE, 320);
        P etto = f.child(emano, snez, "Etto", FEMALE, 330);
        P zoya = f.f("Zoya", 310);
        P eleni = f.f("Eleni", 305);
        f.marry(flo, eleni);
        eleni.deceased = true;
        f.marry(flo, zoya);
        f.marry(dimo, etto);
        P nadja = f.child(dimo, etto, "Nadja", FEMALE, 500);
        f.child(dimo, etto, "Mauri", MALE, 490);
        P arif = f.child(flo, zoya, "Arif", MALE, 480);
        f.child(flo, zoya, "Miqael", MALE, 470);
        zoya.deceased = true;
        dimo.deceased = true;
        f.marry(flo, etto);
        P kadri = f.m("Kadri", 490);
        f.marry(kadri, nadja);
        P hilkka = f.f("Hilkka", 485);
        f.marry(arif, hilkka);
        P alf = f.child(kadri, nadja, "Alf", MALE, 700);
        P stana = f.child(arif, hilkka, "Stana", FEMALE, 690);
        f.child(arif, hilkka, "Conny", FEMALE, 695);
        f.marry(alf, stana);
        P root = f.child(alf, stana, "Fatmir", MALE, 900);
        f.child(alf, stana, "Hans", MALE, 920);
        f.root = root;
        return f;
    }

    static Fam spouseOfTwo() {
        Fam f = new Fam();
        P gus = f.m("Gus", 100);
        P gwen = f.f("Gwen", 105);
        f.marry(gus, gwen);
        P abel = f.child(gus, gwen, "Abel", MALE, 300);
        P bea = f.child(gus, gwen, "Bea", FEMALE, 310);
        P ada = f.f("Ada", 305);
        f.marry(abel, ada);
        P bob = f.m("Bob", 300);
        f.marry(bob, bea);
        P sam = f.child(abel, ada, "Sam", MALE, 500);
        P sabi = f.child(abel, ada, "Sabi", FEMALE, 520);
        P aug = f.child(bob, bea, "Aug", MALE, 510);
        P de = f.f("De", 505);
        P hasko = f.m("Hasko", 490);
        f.marry(hasko, de);
        hasko.deceased = true;
        f.marry(sam, de);
        f.child(sam, de, "Pius", MALE, 700);
        sam.deceased = true;
        f.marry(aug, sabi);
        sabi.deceased = true;
        f.marry(aug, de);
        f.root = gus;
        return f;
    }

    static Fam selfParent() {
        Fam f = new Fam();
        P adrian = f.m("Adrian", 100);
        P denise = f.f("Denise", 105);
        f.marry(adrian, denise);
        P seve = f.child(adrian, denise, "Seve", FEMALE, 290);
        P jacky = f.add("Jacky", MALE, 300);
        jacky.father = adrian;
        jacky.mother = adrian;
        adrian.kids.add(jacky);
        P antonie = f.f("Antonie", 310);
        f.marry(jacky, antonie);
        f.child(jacky, antonie, "Kris", FEMALE, 500);
        P palm = f.child(jacky, antonie, "Palm", FEMALE, 510);
        P ayden = f.child(jacky, antonie, "Ayden", MALE, 520);
        P silv = f.m("Silv", 295);
        f.marry(silv, seve);
        f.child(silv, seve, "Tom", MALE, 480);
        P jim = f.m("Jim", 505);
        f.marry(jim, palm);
        P chan = f.child(jim, palm, "Chan", FEMALE, 700);
        P ilana = f.f("Ilana", 515);
        f.marry(ayden, ilana);
        P juriy = f.child(ayden, ilana, "Juriy", MALE, 710);
        f.marry(juriy, chan);
        f.root = f.child(juriy, chan, "Botyo", MALE, 900);
        return f;
    }

    static Fam bothRemarried() {
        Fam f = new Fam();
        P a = f.m("Alain", 0);
        P b = f.f("Brigitte", 2);
        P c = f.f("Chloe", 4);
        P d = f.m("Denis", 6);
        f.marry(a, b);
        f.child(a, b, "AB1", MALE, 100);
        b.deceased = true;
        f.marry(a, c);
        f.child(a, c, "AC1", FEMALE, 150);
        f.marry(c, d);
        f.child(d, c, "DC1", MALE, 200);
        a.deceased = true;
        return f;
    }

    static Fam halfSiblings() {
        Fam f = new Fam();
        P a = f.m("Andre", 0);
        P m = f.f("Mireille", 2);
        P b = f.m("Bruno", 4);
        f.marry(a, m);
        P d = f.child(a, m, "Diane", FEMALE, 100);
        f.child(a, m, "Dorian", MALE, 105);
        a.deceased = true;
        f.marry(b, m);
        P s = f.child(b, m, "Simon", MALE, 120);
        f.marry(s, d);
        f.child(s, d, "SD1", FEMALE, 200);
        f.child(s, d, "SD2", MALE, 210);
        return f;
    }

    static Fam halfSiblingsConflict() {
        Fam f = new Fam();
        P a = f.m("Andre", 0);
        P m = f.f("Mireille", 2);
        P b = f.m("Bruno", 4);
        f.marry(a, m);
        P s = f.child(a, m, "Simon", MALE, 100);
        a.deceased = true;
        f.marry(b, m);
        P d = f.child(b, m, "Diane", FEMALE, 120);
        f.child(b, m, "Dorian", MALE, 130);
        f.marry(s, d);
        f.child(s, d, "SD1", FEMALE, 200);
        f.child(s, d, "SD2", MALE, 210);
        return f;
    }

    static Fam halfSiblingsOut() {
        Fam f = new Fam();
        P a = f.m("Andre", 0);
        P g = f.f("Agathe", 1);
        P m = f.f("Mireille", 2);
        P b = f.m("Bruno", 4);
        f.marry(a, g);
        P alix = f.child(a, g, "Alix", MALE, 80);
        g.deceased = true;
        f.marry(a, m);
        P d = f.child(a, m, "Diane", FEMALE, 100);
        f.child(a, m, "Dorian", MALE, 105);
        f.child(a, m, "Delphine", FEMALE, 108);
        a.deceased = true;
        f.marry(b, m);
        P s = f.child(b, m, "Simon", MALE, 120);
        f.child(b, m, "Sophie", FEMALE, 125);
        P sw = f.f("Sarah", 121);
        f.marry(s, sw);
        f.child(s, sw, "SS1", FEMALE, 200);
        f.child(s, sw, "SS2", MALE, 210);
        P dh = f.m("Didier", 99);
        f.marry(dh, d);
        f.child(dh, d, "DD1", MALE, 190);
        f.child(dh, d, "DD2", FEMALE, 195);
        f.child(dh, d, "DD3", MALE, 198);
        P aw = f.f("Aline", 82);
        f.marry(alix, aw);
        f.child(alix, aw, "AA1", FEMALE, 170);
        P gp = f.m("GpAndre", -100);
        P gm = f.f("GmAndre", -98);
        f.marry(gp, gm);
        a.father = gp;
        a.mother = gm;
        gp.kids.add(a);
        gm.kids.add(a);
        f.root = d;
        return f;
    }

    static Fam singleParents() {
        Fam f = new Fam();
        P m = f.f("Mere", 0);
        f.child(null, m, "Fils1", MALE, 100);
        f.child(null, m, "Fille1", FEMALE, 110);
        P p = f.m("Pere", 3);
        f.child(p, null, "Fils2", MALE, 105);
        P x = f.m("Xav", 1);
        P y = f.f("Yolande", 2);
        f.marry(x, y);
        f.child(x, y, "XY", MALE, 90);
        f.child(null, y, "Ysolo", FEMALE, 95);
        return f;
    }

    static Fam sameSex() {
        Fam f = new Fam();
        P a = f.f("Alice", 0);
        P b = f.f("Beatrice", 1);
        f.marry(a, b);
        P k1 = f.child(null, a, "Kid1", MALE, 100);
        P k2 = f.child(null, b, "Kid2", FEMALE, 110);
        b.kids.add(k1);
        a.kids.add(k2);
        P c = f.m("Carl", 2);
        P d = f.m("Dan", 3);
        f.marry(c, d);
        return f;
    }

    static Fam village(long seed, int founders, int maxKids) {
        return village(seed, founders, maxKids, false);
    }

    static Set<P> lineage(P p) {
        Set<P> line = new HashSet<>();
        line.add(p);
        for (P parent : new P[]{p.father, p.mother}) {
            if (parent != null) {
                line.add(parent);
                if (parent.father != null) {
                    line.add(parent.father);
                }
                if (parent.mother != null) {
                    line.add(parent.mother);
                }
            }
        }
        return line;
    }

    static boolean related(P a, P b) {
        Set<P> line = lineage(a);
        line.retainAll(lineage(b));
        return !line.isEmpty();
    }

    static Fam village(long seed, int founders, int maxKids, boolean gameRules) {
        return village(seed, founders, maxKids, gameRules, 3);
    }

    static Fam village(long seed, int founders, int maxKids, boolean gameRules, int generations) {
        Random rnd = new Random(seed);
        Fam f = new Fam();
        List<P[]> couples = new ArrayList<>();
        for (int i = 0; i < founders; i++) {
            P m = f.m("M" + i, rnd.nextInt(50));
            P w = f.f("F" + i, rnd.nextInt(50));
            f.marry(m, w);
            couples.add(new P[]{m, w});
        }
        long base = 100;
        for (int g = 1; g <= generations; g++) {
            List<P> kids = new ArrayList<>();
            for (P[] c : couples) {
                int n = 1 + rnd.nextInt(maxKids);
                for (int k = 0; k < n; k++) {
                    kids.add(f.child(c[0], c[1], "g" + g + "_" + f.all.size(), rnd.nextBoolean() ? MALE : FEMALE, base * g + rnd.nextInt(60)));
                }
            }
            if (g == generations) {
                break;
            }
            List<P> men = new ArrayList<>(kids.stream().filter(k -> k.gender == MALE).toList());
            List<P> women = new ArrayList<>(kids.stream().filter(k -> k.gender == FEMALE).toList());
            Collections.shuffle(men, rnd);
            Collections.shuffle(women, rnd);
            couples = new ArrayList<>();
            int pairs = Math.min(men.size(), women.size());
            for (int i = 0; i < pairs; i++) {
                P m = men.get(i);
                P w = women.get(i);
                if (m.father == w.father || gameRules && related(m, w) || rnd.nextInt(5) == 0) {
                    P out = f.f("out" + f.all.size(), m.birth + 3);
                    f.marry(m, out);
                    couples.add(new P[]{m, out});
                    continue;
                }
                f.marry(m, w);
                couples.add(new P[]{m, w});
                if (rnd.nextInt(6) == 0) {
                    P second = f.m("re" + f.all.size(), w.birth + 5);
                    m.deceased = true;
                    m.current = null;
                    f.marry(w, second);
                    couples.add(new P[]{second, w});
                } else if (rnd.nextInt(6) == 0) {
                    P second = f.f("re" + f.all.size(), m.birth + 7);
                    w.deceased = true;
                    f.marry(m, second);
                    couples.add(new P[]{m, second});
                }
            }
            for (int i = pairs; i < men.size(); i++) {
                if (rnd.nextBoolean()) {
                    P out = f.f("out" + f.all.size(), men.get(i).birth + 2);
                    f.marry(men.get(i), out);
                    couples.add(new P[]{men.get(i), out});
                }
            }
            for (int i = pairs; i < women.size(); i++) {
                if (rnd.nextBoolean()) {
                    P out = f.m("out" + f.all.size(), women.get(i).birth + 2);
                    f.marry(out, women.get(i));
                    couples.add(new P[]{out, women.get(i)});
                }
            }
        }
        f.root = f.all.get(f.all.size() - 1);
        return f;
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            svgDir = Path.of(args[0]);
        }
        Map<String, Supplier<Fam>> cases = new LinkedHashMap<>();
        cases.put("simple", TreeLayoutCheck::simple);
        cases.put("fourGen", TreeLayoutCheck::fourGen);
        cases.put("niklasYoung", () -> niklas(true, false));
        cases.put("niklasYoungGrand", () -> niklas(true, true));
        cases.put("niklasOld", () -> niklas(false, false));
        cases.put("niklasOldGrand", () -> niklas(false, true));
        cases.put("corneliea", TreeLayoutCheck::corneliea);
        cases.put("fabien", TreeLayoutCheck::fabien);
        cases.put("bothRemarried", TreeLayoutCheck::bothRemarried);
        cases.put("halfSiblings", TreeLayoutCheck::halfSiblings);
        cases.put("halfSiblingsConflict", TreeLayoutCheck::halfSiblingsConflict);
        cases.put("singleParents", TreeLayoutCheck::singleParents);
        cases.put("sameSex", TreeLayoutCheck::sameSex);
        cases.put("remarriedAncestors", TreeLayoutCheck::remarriedAncestors);
        cases.put("spouseOfTwo", TreeLayoutCheck::spouseOfTwo);
        cases.put("selfParent", TreeLayoutCheck::selfParent);

        Map<String, Supplier<Fam>> game = new LinkedHashMap<>(cases);
        game.remove("halfSiblings");
        game.remove("halfSiblingsConflict");
        game.put("halfSiblingsOut", TreeLayoutCheck::halfSiblingsOut);

        System.out.println("== SELECTION (TreeSelection.select, every root, up/down in 1,2,3,unlimited)");
        Report selection = new Report("selection");
        for (Map.Entry<String, Supplier<Fam>> e : game.entrySet()) {
            Fam fam = e.getValue().get();
            Report r = selectionCases(e.getKey(), fam, fam.all);
            System.out.println(r);
            selection.add(r);
            selection.cases += r.cases;
        }
        for (int seed = 1; seed <= 8; seed++) {
            Fam fam = village(seed, 5, 3, true);
            Report r = selectionCases("village" + seed + "_" + fam.all.size(), fam, randomRoots(fam, seed, 10));
            System.out.println(r);
            selection.add(r);
            selection.cases += r.cases;
        }
        Fam big = village(99, 14, 4, true);
        Report rb = selectionCases("big_" + big.all.size(), big, randomRoots(big, 99, 10));
        System.out.println(rb);
        selection.add(rb);
        selection.cases += rb.cases;
        Fam huge = village(7, 24, 4, true);
        Report rh = selectionCases("huge_" + huge.all.size(), huge, randomRoots(huge, 7, 10));
        System.out.println(rh);
        selection.add(rh);
        selection.cases += rh.cases;

        System.out.println("== DEEP VILLAGES (game marriage rule, 5 generations: second cousins, widows remarried in the family, implex)");
        Report deep = new Report("deep");
        for (int seed = 1; seed <= 10; seed++) {
            Fam fam = village(seed, 6, 3, true, 5);
            Report r = selectionCases("deep" + seed + "_" + fam.all.size(), fam, randomRoots(fam, seed, 12));
            System.out.println(r);
            deep.add(r);
            deep.cases += r.cases;
        }
        System.out.println("== LEGACY (whole family as input, reported only)");
        Report legacy = new Report("legacy");
        for (Map.Entry<String, Supplier<Fam>> e : cases.entrySet()) {
            for (boolean reverse : new boolean[]{false, true}) {
                Fam fam = e.getValue().get();
                Report r = check(e.getKey() + (reverse ? "_rev" : ""), fam.persons(reverse), fam.root.id, false);
                legacy.add(r);
                legacy.cases++;
                if (r.errors() > 0) {
                    System.out.println(r);
                    r.details.stream().limit(6).forEach(d -> System.out.println("    " + d));
                }
            }
        }
        for (int seed = 1; seed <= 8; seed++) {
            Fam fam = village(seed, 5, 3);
            Report r = check("village" + seed + "_" + fam.all.size(), fam.persons(false), fam.root.id, false);
            legacy.add(r);
            legacy.cases++;
            System.out.println(r);
        }
        System.out.println("SELECTION TOTAL: " + selection);
        System.out.println("DEEP TOTAL:      " + deep);
        System.out.println("LEGACY TOTAL:    " + legacy);
        if (selection.errors() + deep.errors() + selection.fallback + deep.fallback > 0) {
            System.exit(1);
        }
    }

    static List<P> randomRoots(Fam fam, long seed, int count) {
        Random rnd = new Random(seed * 31 + 7);
        List<P> roots = new ArrayList<>();
        while (roots.size() < Math.min(count, fam.all.size())) {
            P p = fam.all.get(rnd.nextInt(fam.all.size()));
            if (!roots.contains(p)) {
                roots.add(p);
            }
        }
        return roots;
    }
}
