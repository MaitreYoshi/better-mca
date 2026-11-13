package com.maitreyoshi.mcavie.compat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class GuiEntity {
    private GuiEntity() {
    }

    public static void head(GuiGraphicsExtractor context, LivingEntity entity, int x0, int y0, int x1, int y1, float scale) {
        EntityRenderer<? super LivingEntity, ?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        EntityRenderState state = renderer.createRenderState(entity, 1.0F);
        state.shadowPieces.clear();
        state.outlineColor = 0;
        if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = 180;
            living.yRot = 0;
            living.xRot = 0;
        }
        context.entity(state, scale, new Vector3f(0, entity.getBbHeight() * 0.875f, 0),
                new Quaternionf().rotateZ((float) Math.PI), new Quaternionf(), x0, y0, x1, y1);
    }
}
