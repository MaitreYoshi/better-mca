package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.client.HappinessDisplay;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void mcavie$happinessBar(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
        if (!(entity instanceof VillagerEntityMCA) || state.nameTag == null || state.distanceToCameraSq > 4096) {
            return;
        }
        HappinessDisplay.Entry entry = HappinessDisplay.get(entity.getId());
        if (entry != null) {
            state.scoreText = HappinessDisplay.bar(entry.happiness());
        }
    }
}
