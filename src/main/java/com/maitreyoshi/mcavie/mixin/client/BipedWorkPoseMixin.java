package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.work.client.WorkPoses;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// pose de travail des PNJ MCA : appliquée après l'animation biped, avant que MCA recopie les bras sur les vêtements
@Mixin(BipedEntityModel.class)
public abstract class BipedWorkPoseMixin {
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void mcavie$workPose(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        WorkPoses.apply((BipedEntityModel<?>) (Object) this, entity, animationProgress);
    }
}
