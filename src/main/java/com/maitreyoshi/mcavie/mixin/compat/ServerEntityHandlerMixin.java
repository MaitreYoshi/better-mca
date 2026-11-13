package com.maitreyoshi.mcavie.mixin.compat;

import com.maitreyoshi.mcavie.compat.ServerEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
public abstract class ServerEntityHandlerMixin {
    @Inject(method = "onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void mcavie$stopTracking(Entity entity, CallbackInfo ci) {
        if (entity.level() instanceof ServerLevel world) {
            ServerEntityEvents.ENTITY_UNLOAD.forEach(listener -> listener.accept(entity, world));
        }
    }
}
