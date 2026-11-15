package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.phone.client.CameraMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// En mode photo, le joueur ne tient rien à l'image (selfie compris).
@Mixin(HeldItemFeatureRenderer.class)
public abstract class CameraHeldItemMixin {
    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void mcavie$hideInCamera(MatrixStack matrices, VertexConsumerProvider consumers, int light, LivingEntity entity,
                                     float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch,
                                     CallbackInfo ci) {
        if (CameraMode.active() && entity == MinecraftClient.getInstance().player) {
            ci.cancel();
        }
    }
}
