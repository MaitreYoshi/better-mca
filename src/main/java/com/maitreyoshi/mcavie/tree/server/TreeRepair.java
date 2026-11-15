package com.maitreyoshi.mcavie.tree.server;

import com.maitreyoshi.mcavie.MCAVie;
import net.mca.server.world.data.FamilyTreeNode;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class TreeRepair {
    private TreeRepair() {
    }

    public static boolean repair(Map<UUID, FamilyTreeNode> entries) {
        if (entries == null || entries.isEmpty()) {
            return false;
        }

        int removed = 0;
        for (FamilyTreeNode node : entries.values()) {
            for (UUID childId : new ArrayList<>(node.children())) {
                FamilyTreeNode child = entries.get(childId);
                if (child == null || (!node.id().equals(child.father()) && !node.id().equals(child.mother()))) {
                    node.children().remove(childId);
                    removed++;
                }
            }
        }

        int added = 0;
        for (FamilyTreeNode node : entries.values()) {
            added += attach(entries, node, node.father());
            if (!Objects.equals(node.mother(), node.father())) {
                added += attach(entries, node, node.mother());
            }
        }

        if (removed > 0 || added > 0) {
            MCAVie.LOGGER.info("Arbre généalogique réparé : {} lien(s) enfant retiré(s), {} ajouté(s)", removed, added);
            return true;
        }
        return false;
    }

    private static int attach(Map<UUID, FamilyTreeNode> entries, FamilyTreeNode child, UUID parentId) {
        if (!FamilyTreeNode.isValid(parentId) || parentId.equals(child.id())) {
            return 0;
        }
        FamilyTreeNode parent = entries.get(parentId);
        return parent != null && parent.children().add(child.id()) ? 1 : 0;
    }
}
