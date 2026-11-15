package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.phone.client.CameraMode;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class CameraHudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void mcavie$cameraOnly(DrawContext context, float tickDelta, CallbackInfo ci) {
        if (CameraMode.active()) {
            CameraMode.render(context, tickDelta);
            ci.cancel();
        }
    }
}
