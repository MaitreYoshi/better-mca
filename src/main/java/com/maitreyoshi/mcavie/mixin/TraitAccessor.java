package com.maitreyoshi.mcavie.mixin;

import net.mca.entity.ai.Traits;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Traits.Trait.class, remap = false)
public interface TraitAccessor {
    @Accessor("chance")
    float mcavie$getChance();
}
