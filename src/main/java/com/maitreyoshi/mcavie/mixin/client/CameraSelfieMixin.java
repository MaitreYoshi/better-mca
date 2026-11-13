package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.phone.client.CameraMode;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Camera.class)
public abstract class CameraSelfieMixin {
    @ModifyArg(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"), require = 0)
    private float mcavie$selfieDistance(float distance) {
        return CameraMode.selfie() ? Math.min(distance, 1.6f) : distance;
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void mcavie$zoom(float partialTicks, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Float> cir) {
        double zoom = CameraMode.zoom();
        if (zoom > 1) {
            cir.setReturnValue((float) (cir.getReturnValue() / zoom));
        }
    }
}
