package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.phone.client.CameraMode;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class CameraHudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void mcavie$cameraOnly(GuiGraphicsExtractor context, float tickDelta, CallbackInfo ci) {
        if (CameraMode.active()) {
            CameraMode.render(context, tickDelta);
            ci.cancel();
        }
    }
}
