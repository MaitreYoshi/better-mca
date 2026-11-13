package com.maitreyoshi.mcavie.mixin.compat.client;

import com.maitreyoshi.mcavie.compat.client.ClientEntityEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.multiplayer.ClientLevel$EntityCallbacks")
public abstract class ClientEntityHandlerMixin {
    @Inject(method = "onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void mcavie$stopTracking(Entity entity, CallbackInfo ci) {
        if (entity.level() instanceof ClientLevel world) {
            ClientEntityEvents.ENTITY_UNLOAD.forEach(listener -> listener.accept(entity, world));
        }
    }
}
