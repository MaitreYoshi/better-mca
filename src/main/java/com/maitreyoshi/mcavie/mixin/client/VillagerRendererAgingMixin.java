package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.aging.client.AgingClient;
import net.mca.client.render.VillagerLikeEntityMCARenderer;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerLikeEntityMCARenderer.class)
public abstract class VillagerRendererAgingMixin {
    @Inject(method = "scale(Lnet/minecraft/entity/mob/MobEntity;Lnet/minecraft/client/util/math/MatrixStack;F)V", at = @At("TAIL"))
    private void mcavie$shrink(MobEntity entity, MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (entity instanceof VillagerEntityMCA villager) {
            float scale = AgingClient.shrink(villager);
            if (scale < 1) {
                matrices.scale(scale, scale, scale);
            }
        }
    }
}
