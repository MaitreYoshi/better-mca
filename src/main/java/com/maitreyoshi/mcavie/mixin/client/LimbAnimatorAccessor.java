package com.maitreyoshi.mcavie.mixin.client;

import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WalkAnimationState.class)
public interface LimbAnimatorAccessor {
    @Accessor("speedOld")
    float mcavie$getPrevSpeed();

    @Accessor("speedOld")
    void mcavie$setPrevSpeed(float value);

    @Accessor("speed")
    float mcavie$getSpeed();

    @Accessor("speed")
    void mcavie$setSpeed(float value);

    @Accessor("position")
    float mcavie$getPos();

    @Accessor("position")
    void mcavie$setPos(float value);
}
