package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.phone.client.CameraMode;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class CameraZoomMixin {
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void mcavie$hideHand(net.minecraft.client.renderer.state.level.CameraRenderState camera, float tickDelta, org.joml.Matrix4fc matrix, CallbackInfo ci) {
        if (CameraMode.active()) {
            ci.cancel();
        }
    }
}
