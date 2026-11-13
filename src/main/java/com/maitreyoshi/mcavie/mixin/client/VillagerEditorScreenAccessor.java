package com.maitreyoshi.mcavie.mixin.client;

import net.conczin.mca.client.gui.VillagerEditorScreen;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = VillagerEditorScreen.class, remap = false)
public interface VillagerEditorScreenAccessor {
    @Accessor("villagerNameField")
    EditBox mcavie$getNameField();
}
