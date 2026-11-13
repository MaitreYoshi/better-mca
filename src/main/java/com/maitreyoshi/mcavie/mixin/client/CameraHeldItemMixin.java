package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.phone.client.CameraMode;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// En mode photo, le joueur ne tient rien à l'image (selfie compris).
@Mixin(ItemInHandLayer.class)
public abstract class CameraHeldItemMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void mcavie$hideInCamera(PoseStack matrices, MultiBufferSource consumers, int light, LivingEntity entity,
                                     float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch,
                                     CallbackInfo ci) {
        if (CameraMode.active() && entity == Minecraft.getInstance().player) {
            ci.cancel();
        }
    }
}
