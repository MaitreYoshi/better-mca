package com.maitreyoshi.mcavie.mixin.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.TagValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Accès au NBT brut écrit, pour garder nos anciennes méthodes de sauvegarde (et le format des sauvegardes 1.20). */
@Mixin(TagValueOutput.class)
public interface TagValueOutputAccessor {
    @Accessor("output")
    CompoundTag mcavie$output();
}
