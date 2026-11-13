package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.client.HappinessDisplay;
import com.mojang.blaze3d.vertex.PoseStack;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Unique
    private static final float BAR_SCALE = 0.6f;

    @Unique
    private static float mcavie$barX = Float.NaN;

    @Shadow
    @Final
    protected EntityRenderDispatcher entityRenderDispatcher;

    @Shadow
    public abstract Font getFont();

    @Inject(method = "renderNameTag", at = @At("TAIL"))
    private void mcavie$drawHappinessBar(Entity entity, Component text, PoseStack matrices, MultiBufferSource vertexConsumers, int light, CallbackInfo ci) {
        if (!(entity instanceof VillagerEntityMCA) || entityRenderDispatcher.distanceToSqr(entity) > 4096) {
            return;
        }
        HappinessDisplay.Entry entry = HappinessDisplay.get(entity.getId());
        if (entry == null) {
            return;
        }

        matrices.pushPose();
        matrices.translate(0.0F, entity.getNameTagOffsetY(), 0.0F);
        matrices.mulPose(entityRenderDispatcher.cameraOrientation());
        matrices.scale(-0.025F, -0.025F, 0.025F);
        matrices.translate(0.0F, 10.0F, 0.0F);
        matrices.scale(BAR_SCALE, BAR_SCALE, 1.0F);

        Component bar = HappinessDisplay.bar(entry.happiness());
        Font textRenderer = getFont();
        if (Float.isNaN(mcavie$barX)) {
            mcavie$barX = -textRenderer.width(bar) / 2f;
        }
        float x = mcavie$barX;
        Matrix4f matrix = matrices.last().pose();
        textRenderer.drawInBatch(bar, x, 0, 0xFFFFFFFF, false, matrix, vertexConsumers, Font.DisplayMode.NORMAL, 0x40000000, light);
        matrices.popPose();
    }
}
