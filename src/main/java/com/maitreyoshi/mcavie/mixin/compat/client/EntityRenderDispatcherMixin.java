package com.maitreyoshi.mcavie.mixin.compat.client;

import com.maitreyoshi.mcavie.aging.client.AgingClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.EntityType;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Shadow
    private Map<EntityType<?>, EntityRenderer<?>> renderers;

    @Shadow
    @Final
    private ItemInHandRenderer itemInHandRenderer;

    @Inject(method = "reload", at = @At("TAIL"))
    private void mcavie$addLayers(CallbackInfo ci) {
        for (EntityRenderer<?> renderer : renderers.values()) {
            AgingClient.addLayers(renderer, itemInHandRenderer);
        }
    }
}
