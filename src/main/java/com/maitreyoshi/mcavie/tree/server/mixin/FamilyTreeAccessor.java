package com.maitreyoshi.mcavie.tree.server.mixin;

import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

@Mixin(value = FamilyTree.class, remap = false)
public interface FamilyTreeAccessor {
    @Accessor(value = "entries", remap = false)
    Map<UUID, FamilyTreeNode> mcavie$getEntries();
}
