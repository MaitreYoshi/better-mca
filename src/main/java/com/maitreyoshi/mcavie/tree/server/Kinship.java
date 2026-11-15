package com.maitreyoshi.mcavie.tree.server;

import com.maitreyoshi.mcavie.tree.RelationInfo;
import net.mca.server.world.data.FamilyTreeNode;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Function;

public final class Kinship {
    public enum Step {
        UP,
        DOWN,
        SPOUSE
    }

    public static final RelationInfo SELF = new RelationInfo("self", 0);
    private static final RelationInfo BY_MARRIAGE = rel("relative_by_marriage");

    private Kinship() {
    }

    public static RelationInfo classify(List<UUID> nodes, List<Step> steps, Function<UUID, Optional<FamilyTreeNode>> lookup,
                                        BiPredicate<UUID, UUID> currentSpouse) {
        if (steps.isEmpty()) {
            return SELF;
        }
        UUID a = nodes.get(0);
        UUID b = nodes.get(nodes.size() - 1);

        int spouseSteps = (int) steps.stream().filter(s -> s == Step.SPOUSE).count();
        if (spouseSteps == 0) {
            int[] ij = upDown(steps);
            return ij == null ? rel("distant_relative") : blood(ij[0], ij[1], a, b, lookup);
        }
        if (spouseSteps > 1) {
            return BY_MARRIAGE;
        }

        if (steps.get(0) == Step.SPOUSE) {
            if (steps.size() == 1) {
                boolean current = currentSpouse.test(a, b) || currentSpouse.test(b, a);
                return rel(current ? "spouse" : "ex_spouse");
            }
            int[] ij = upDown(steps.subList(1, steps.size()));
            if (ij != null) {
                if (ij[0] == 1 && ij[1] == 0) {
                    return rel("parent_in_law");
                }
                if (ij[0] == 1 && ij[1] == 1) {
                    return rel("sibling_in_law");
                }
                if (ij[0] == 0 && ij[1] == 1 && !isParentOf(a, b, lookup)) {
                    return rel("step_child");
                }
            }
        } else if (steps.get(steps.size() - 1) == Step.SPOUSE) {
            int[] ij = upDown(steps.subList(0, steps.size() - 1));
            if (ij != null) {
                if (ij[0] == 1 && ij[1] == 0 && !isParentOf(b, a, lookup)) {
                    return rel("step_parent");
                }
                if (ij[0] == 0 && ij[1] == 1) {
                    return rel("child_in_law");
                }
                if (ij[0] == 1 && ij[1] == 1) {
                    return rel("sibling_in_law");
                }
            }
        }
        return BY_MARRIAGE;
    }

    static RelationInfo blood(int i, int j, UUID a, UUID b, Function<UUID, Optional<FamilyTreeNode>> lookup) {
        if (i == 0) {
            if (j == 1) {
                return rel("child");
            }
            if (j == 2) {
                return rel("grandchild");
            }
            return new RelationInfo("great_grandchild", j - 2);
        }
        if (j == 0) {
            if (i == 1) {
                return rel("parent");
            }
            if (i == 2) {
                return rel("grandparent");
            }
            return new RelationInfo("great_grandparent", i - 2);
        }
        if (i == 1 && j == 1) {
            return rel(isHalfSibling(a, b, lookup) ? "half_sibling" : "sibling");
        }
        if (j == 1) {
            return i == 2 ? rel("uncle") : new RelationInfo("grand_uncle", i - 3);
        }
        if (i == 1) {
            return j == 2 ? rel("nephew") : new RelationInfo("grand_nephew", j - 3);
        }
        if (i >= 2 && j >= 2) {
            return new RelationInfo("cousin", Math.min(i, j) - 1);
        }
        return rel("distant_relative");
    }

    private static boolean isHalfSibling(UUID a, UUID b, Function<UUID, Optional<FamilyTreeNode>> lookup) {
        Optional<FamilyTreeNode> na = lookup.apply(a);
        Optional<FamilyTreeNode> nb = lookup.apply(b);
        if (na.isEmpty() || nb.isEmpty()) {
            return false;
        }
        List<UUID> pa = na.get().streamParents().toList();
        List<UUID> pb = nb.get().streamParents().toList();
        long shared = pa.stream().filter(pb::contains).count();
        return shared < 2 && pa.size() == 2 && pb.size() == 2;
    }

    private static boolean isParentOf(UUID parent, UUID child, Function<UUID, Optional<FamilyTreeNode>> lookup) {
        return lookup.apply(child).map(n -> n.isParent(parent)).orElse(false);
    }

    private static int[] upDown(List<Step> steps) {
        int up = 0;
        int down = 0;
        for (Step step : steps) {
            if (step == Step.UP && down == 0) {
                up++;
            } else if (step == Step.DOWN) {
                down++;
            } else {
                return null;
            }
        }
        return new int[]{up, down};
    }

    private static RelationInfo rel(String code) {
        return new RelationInfo(code, 0);
    }
}
