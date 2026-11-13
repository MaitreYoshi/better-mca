package com.maitreyoshi.mcavie.mixin.compat;

import com.maitreyoshi.mcavie.compat.EntityTrackingEvents;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerEntity.class)
public abstract class EntityTrackerEntryMixin {
    @Shadow
    @Final
    private Entity entity;

    @Inject(method = "addPairing", at = @At("TAIL"))
    private void mcavie$startTracking(ServerPlayer player, CallbackInfo ci) {
        EntityTrackingEvents.START_TRACKING.forEach(listener -> listener.accept(entity, player));
    }
}
