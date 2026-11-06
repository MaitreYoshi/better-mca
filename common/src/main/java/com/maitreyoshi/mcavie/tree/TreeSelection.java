package com.maitreyoshi.mcavie.tree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class TreeSelection {
    private TreeSelection() {
    }

    public interface Graph {
        List<UUID> parents(UUID id);

        List<UUID> children(UUID id);

        List<UUID> spouses(UUID id);
    }

    public static LinkedHashSet<UUID> select(Graph graph, UUID root, int up, int down, int max) {
        int maxUp = up < 0 ? Integer.MAX_VALUE : up;
        int maxDown = down < 0 ? Integer.MAX_VALUE : down;
        LinkedHashSet<UUID> result = new LinkedHashSet<>();
        if (max <= 0) {
            return result;
        }
        result.add(root);

        List<UUID> generation = List.of(root);
        for (int level = 0; level < maxUp && !generation.isEmpty() && result.size() < max; level++) {
            List<UUID> next = new ArrayList<>();
            for (UUID person : generation) {
                for (UUID parent : graph.parents(person)) {
                    if (result.size() >= max) {
                        break;
                    }
                    if (result.add(parent)) {
                        next.add(parent);
                    }
                }
            }
            generation = next;
        }

        record Walk(UUID id, int depth) {
        }
        Deque<Walk> queue = new ArrayDeque<>();
        Set<UUID> seen = new HashSet<>();
        queue.add(new Walk(root, 0));
        seen.add(root);
        if (maxUp > 0) {
            for (UUID parent : graph.parents(root)) {
                for (UUID sibling : graph.children(parent)) {
                    if (seen.add(sibling)) {
                        queue.add(new Walk(sibling, 0));
                    }
                }
            }
        }

        while (!queue.isEmpty() && result.size() < max) {
            Walk walk = queue.poll();
            result.add(walk.id);
            for (UUID spouse : graph.spouses(walk.id)) {
                if (result.size() >= max) {
                    break;
                }
                result.add(spouse);
            }
            if (walk.depth < maxDown) {
                for (UUID child : graph.children(walk.id)) {
                    if (seen.add(child)) {
                        queue.add(new Walk(child, walk.depth + 1));
                    }
                }
            }
        }
        return result;
    }
}
