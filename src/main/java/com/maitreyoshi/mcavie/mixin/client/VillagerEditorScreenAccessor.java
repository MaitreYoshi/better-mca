package com.maitreyoshi.mcavie.mixin.client;

import net.mca.client.gui.VillagerEditorScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = VillagerEditorScreen.class, remap = false)
public interface VillagerEditorScreenAccessor {
    @Accessor("villagerNameField")
    TextFieldWidget mcavie$getNameField();
}
