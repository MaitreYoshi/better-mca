package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.phone.client.CameraMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// En mode photo, les bras du joueur restent le long du corps (pas de bras tendu vers l'objectif).
@Mixin(PlayerRenderer.class)
public abstract class CameraArmPoseMixin {
    @Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true)
    private static void mcavie$emptyArms(AbstractClientPlayer player, InteractionHand hand, CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
        if (CameraMode.active() && player == Minecraft.getInstance().player) {
            cir.setReturnValue(HumanoidModel.ArmPose.EMPTY);
        }
    }
}
