package com.maitreyoshi.mcavie.phone.client;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.phone.PhotoItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.maitreyoshi.mcavie.compat.client.BuiltinItemRendererRegistry;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.UUID;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class PhotoItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    private static final Identifier PAPER = MCAVie.id("textures/entity/photo_paper.png");
    private static final float CARD_HEIGHT = 0.5625f;

    @Override
    public void render(ItemStack stack, ItemDisplayContext mode, PoseStack matrices, MultiBufferSource consumers, int light, int overlay) {
        UUID photo = PhotoItem.photoOf(stack);
        PhonePhotos.Picture picture = photo == null ? null : PhonePhotos.print(photo, false);
        float bottom = (1 - CARD_HEIGHT) / 2;
        PoseStack.Pose entry = matrices.last();
        Matrix4f position = entry.pose();
        Matrix3f normal = entry.normal();
        if (picture == null) {
            quad(consumers.getBuffer(RenderType.entityCutoutNoCull(PAPER)), position, normal, 0, bottom, 1, bottom + CARD_HEIGHT, 0.5f,
                    0, 0, 1, 1, light, overlay);
            return;
        }
        float[] uv = cover(picture, 1 / CARD_HEIGHT);
        quad(consumers.getBuffer(RenderType.entityCutoutNoCull(picture.texture())), position, normal, 0, bottom, 1, bottom + CARD_HEIGHT, 0.5f,
                uv[0], uv[1], uv[2], uv[3], light, overlay);
    }

    public static float[] cover(PhonePhotos.Picture picture, float target) {
        float ratio = picture.width() / (float) picture.height();
        if (ratio > target) {
            float span = target / ratio;
            return new float[]{(1 - span) / 2, 0, (1 + span) / 2, 1};
        }
        float span = ratio / target;
        return new float[]{0, (1 - span) / 2, 1, (1 + span) / 2};
    }

    public static void renderMap(PoseStack matrices, MultiBufferSource consumers, int light, ItemStack stack) {
        matrices.mulPose(Axis.YP.rotationDegrees(180.0f));
        matrices.mulPose(Axis.ZP.rotationDegrees(180.0f));
        matrices.scale(0.38f, 0.38f, 0.38f);
        matrices.translate(-0.5f, -0.5f, 0.0f);
        matrices.scale(0.0078125f, 0.0078125f, 0.0078125f);
        Matrix4f position = matrices.last().pose();
        float x0 = -7;
        float x1 = 135;
        float height = (x1 - x0) * CARD_HEIGHT;
        float y0 = 64 - height / 2;
        float y1 = 64 + height / 2;
        UUID photo = PhotoItem.photoOf(stack);
        PhonePhotos.Picture picture = photo == null ? null : PhonePhotos.print(photo, true);
        if (picture == null) {
            mapQuad(consumers.getBuffer(RenderType.text(PAPER)), position, x0, y0, x1, y1, 0, 0, 0, 1, 1, light);
            return;
        }
        float[] uv = cover(picture, 1 / CARD_HEIGHT);
        mapQuad(consumers.getBuffer(RenderType.text(picture.texture())), position, x0, y0, x1, y1, 0, uv[0], uv[1], uv[2], uv[3], light);
    }

    private static void mapQuad(VertexConsumer buffer, Matrix4f position, float x0, float y0, float x1, float y1, float z,
                                float u0, float v0, float u1, float v1, int light) {
        buffer.vertex(position, x0, y1, z).color(255, 255, 255, 255).uv(u0, v1).uv2(light).endVertex();
        buffer.vertex(position, x1, y1, z).color(255, 255, 255, 255).uv(u1, v1).uv2(light).endVertex();
        buffer.vertex(position, x1, y0, z).color(255, 255, 255, 255).uv(u1, v0).uv2(light).endVertex();
        buffer.vertex(position, x0, y0, z).color(255, 255, 255, 255).uv(u0, v0).uv2(light).endVertex();
    }

    private static void quad(VertexConsumer buffer, Matrix4f position, Matrix3f normal, float x0, float y0, float x1, float y1, float z,
                             float u0, float v0, float u1, float v1, int light, int overlay) {
        vertex(buffer, position, normal, x0, y0, z, u0, v1, light, overlay);
        vertex(buffer, position, normal, x1, y0, z, u1, v1, light, overlay);
        vertex(buffer, position, normal, x1, y1, z, u1, v0, light, overlay);
        vertex(buffer, position, normal, x0, y1, z, u0, v0, light, overlay);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f position, Matrix3f normal, float x, float y, float z, float u, float v, int light, int overlay) {
        buffer.vertex(position, x, y, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(overlay).uv2(light).normal(normal, 0, 0, 1).endVertex();
    }
}
