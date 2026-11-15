package com.maitreyoshi.mcavie.mixin.client;

import net.minecraft.entity.LimbAnimator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LimbAnimator.class)
public interface LimbAnimatorAccessor {
    @Accessor("prevSpeed")
    float mcavie$getPrevSpeed();

    @Accessor("prevSpeed")
    void mcavie$setPrevSpeed(float value);

    @Accessor("speed")
    float mcavie$getSpeed();

    @Accessor("speed")
    void mcavie$setSpeed(float value);

    @Accessor("pos")
    float mcavie$getPos();

    @Accessor("pos")
    void mcavie$setPos(float value);
}
