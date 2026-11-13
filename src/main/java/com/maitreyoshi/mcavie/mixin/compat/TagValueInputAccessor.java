package com.maitreyoshi.mcavie.mixin.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.TagValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Accès au NBT brut lu, pour garder nos anciennes méthodes de chargement (et le format des sauvegardes 1.20). */
@Mixin(TagValueInput.class)
public interface TagValueInputAccessor {
    @Accessor("input")
    CompoundTag mcavie$input();
}
