package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.client.HappinessDisplay;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
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
    protected EntityRenderDispatcher dispatcher;

    @Shadow
    public abstract TextRenderer getTextRenderer();

    @Inject(method = "renderLabelIfPresent", at = @At("TAIL"))
    private void mcavie$drawHappinessBar(Entity entity, Text text, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (!(entity instanceof VillagerEntityMCA) || dispatcher.getSquaredDistanceToCamera(entity) > 4096) {
            return;
        }
        HappinessDisplay.Entry entry = HappinessDisplay.get(entity.getId());
        if (entry == null) {
            return;
        }

        matrices.push();
        matrices.translate(0.0F, entity.getNameLabelHeight(), 0.0F);
        matrices.multiply(dispatcher.getRotation());
        matrices.scale(-0.025F, -0.025F, 0.025F);
        matrices.translate(0.0F, 10.0F, 0.0F);
        matrices.scale(BAR_SCALE, BAR_SCALE, 1.0F);

        Text bar = HappinessDisplay.bar(entry.happiness());
        TextRenderer textRenderer = getTextRenderer();
        if (Float.isNaN(mcavie$barX)) {
            mcavie$barX = -textRenderer.getWidth(bar) / 2f;
        }
        float x = mcavie$barX;
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        textRenderer.draw(bar, x, 0, 0xFFFFFFFF, false, matrix, vertexConsumers, TextRenderer.TextLayerType.NORMAL, 0x40000000, light);
        matrices.pop();
    }
}
