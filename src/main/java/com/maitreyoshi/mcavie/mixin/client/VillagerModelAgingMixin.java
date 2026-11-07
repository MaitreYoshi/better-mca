package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.aging.client.AgingClient;
import net.mca.client.model.VillagerEntityBaseModelMCA;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerEntityBaseModelMCA.class)
public abstract class VillagerModelAgingMixin {
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void mcavie$elderPose(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        if (entity instanceof VillagerEntityMCA villager) {
            AgingClient.pose((VillagerEntityBaseModelMCA<?>) (Object) this, villager);
        }
    }
}
