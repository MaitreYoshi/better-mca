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
    private static int nextFakeId = -2_000_000;

    private GuiEntity() {
    }

    public static int fakeId() {
        return nextFakeId--;
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
        org.joml.Matrix3x2f pose = context.pose();
        org.joml.Vector2f a = pose.transformPosition(x0, y0, new org.joml.Vector2f());
        org.joml.Vector2f b = pose.transformPosition(x1, y1, new org.joml.Vector2f());
        float factor = (float) Math.sqrt(pose.m00() * pose.m00() + pose.m01() * pose.m01());
        context.entity(state, scale * factor, new Vector3f(0, entity.getBbHeight() * 0.875f, 0),
                new Quaternionf().rotateZ((float) Math.PI), new Quaternionf(),
                Math.round(Math.min(a.x, b.x)), Math.round(Math.min(a.y, b.y)), Math.round(Math.max(a.x, b.x)), Math.round(Math.max(a.y, b.y)));
    }
}
