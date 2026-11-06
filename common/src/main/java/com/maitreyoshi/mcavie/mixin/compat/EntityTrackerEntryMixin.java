package com.maitreyoshi.mcavie.mixin.compat;

import com.maitreyoshi.mcavie.compat.EntityTrackingEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityTrackerEntry.class)
public abstract class EntityTrackerEntryMixin {
    @Shadow
    @Final
    private Entity entity;

    @Inject(method = "startTracking", at = @At("TAIL"))
    private void mcavie$startTracking(ServerPlayerEntity player, CallbackInfo ci) {
        EntityTrackingEvents.START_TRACKING.forEach(listener -> listener.accept(entity, player));
    }
}
