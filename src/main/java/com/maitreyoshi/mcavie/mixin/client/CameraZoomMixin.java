package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.phone.client.CameraMode;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class CameraZoomMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void mcavie$zoom(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        double zoom = CameraMode.zoom();
        if (zoom > 1) {
            cir.setReturnValue(cir.getReturnValue() / zoom);
        }
    }

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void mcavie$hideHand(MatrixStack matrices, Camera camera, float tickDelta, CallbackInfo ci) {
        if (CameraMode.active()) {
            ci.cancel();
        }
    }
}
