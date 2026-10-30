package com.maitreyoshi.mcavie.tree.client.mixin;

import net.mca.client.gui.FamilyTreeScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

@Mixin(value = FamilyTreeScreen.class, remap = false)
public interface FamilyTreeScreenAccessor {
    @Accessor("focusedEntityId")
    UUID mcavie$getFocusedEntityId();
}
