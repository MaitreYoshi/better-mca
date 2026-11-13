package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.phone.client.CameraMode;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Camera.class)
public abstract class CameraSelfieMixin {
    @ModifyArg(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(D)D"), require = 0)
    private double mcavie$selfieDistance(double distance) {
        return CameraMode.selfie() ? Math.min(distance, 1.6) : distance;
    }
}
