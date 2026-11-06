package com.maitreyoshi.mcavie.mixin.compat.client;

import com.maitreyoshi.mcavie.compat.client.WorldRenderEvents;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void mcavie$renderEnd(CallbackInfo ci) {
        WorldRenderEvents.END.forEach(Runnable::run);
    }
}
