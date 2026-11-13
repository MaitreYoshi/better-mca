package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.aging.client.AgingClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.render.VillagerLikeEntityMCARenderer;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerLikeEntityMCARenderer.class)
public abstract class VillagerRendererAgingMixin {
    @Inject(method = "scale(Lnet/minecraft/world/entity/Mob;Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("TAIL"))
    private void mcavie$shrink(Mob entity, PoseStack matrices, float tickDelta, CallbackInfo ci) {
        if (entity instanceof VillagerEntityMCA villager) {
            float scale = AgingClient.shrink(villager);
            if (scale < 1) {
                matrices.scale(scale, scale, scale);
            }
        }
    }
}
