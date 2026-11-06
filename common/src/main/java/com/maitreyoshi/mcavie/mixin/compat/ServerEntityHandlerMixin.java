package com.maitreyoshi.mcavie.mixin.compat;

import com.maitreyoshi.mcavie.compat.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.world.ServerWorld$ServerEntityHandler")
public abstract class ServerEntityHandlerMixin {
    @Inject(method = "stopTracking(Lnet/minecraft/entity/Entity;)V", at = @At("HEAD"))
    private void mcavie$stopTracking(Entity entity, CallbackInfo ci) {
        if (entity.getWorld() instanceof ServerWorld world) {
            ServerEntityEvents.ENTITY_UNLOAD.forEach(listener -> listener.accept(entity, world));
        }
    }
}
