package com.maitreyoshi.mcavie.mixin.compat.client;

import com.maitreyoshi.mcavie.aging.client.AgingClient;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.entity.EntityType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Shadow
    private Map<EntityType<?>, EntityRenderer<?>> renderers;

    @Shadow
    @Final
    private HeldItemRenderer heldItemRenderer;

    @Inject(method = "reload", at = @At("TAIL"))
    private void mcavie$addLayers(CallbackInfo ci) {
        for (EntityRenderer<?> renderer : renderers.values()) {
            AgingClient.addLayers(renderer, heldItemRenderer);
        }
    }
}
