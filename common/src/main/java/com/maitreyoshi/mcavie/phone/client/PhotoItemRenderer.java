package com.maitreyoshi.mcavie.phone.client;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.phone.PhotoItem;
import com.maitreyoshi.mcavie.compat.client.BuiltinItemRendererRegistry;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.UUID;

public final class PhotoItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    private static final Identifier PAPER = MCAVie.id("textures/entity/photo_paper.png");
    private static final float CARD_HEIGHT = 0.5625f;

    @Override
    public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
        UUID photo = PhotoItem.photoOf(stack);
        PhonePhotos.Picture picture = photo == null ? null : PhonePhotos.print(photo, false);
        float bottom = (1 - CARD_HEIGHT) / 2;
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f position = entry.getPositionMatrix();
        Matrix3f normal = entry.getNormalMatrix();
        if (picture == null) {
            quad(consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(PAPER)), position, normal, 0, bottom, 1, bottom + CARD_HEIGHT, 0.5f,
                    0, 0, 1, 1, light, overlay);
            return;
        }
        float[] uv = cover(picture, 1 / CARD_HEIGHT);
        quad(consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(picture.texture())), position, normal, 0, bottom, 1, bottom + CARD_HEIGHT, 0.5f,
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

    public static void renderMap(MatrixStack matrices, VertexConsumerProvider consumers, int light, ItemStack stack) {
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0f));
        matrices.scale(0.38f, 0.38f, 0.38f);
        matrices.translate(-0.5f, -0.5f, 0.0f);
        matrices.scale(0.0078125f, 0.0078125f, 0.0078125f);
        Matrix4f position = matrices.peek().getPositionMatrix();
        float x0 = -7;
        float x1 = 135;
        float height = (x1 - x0) * CARD_HEIGHT;
        float y0 = 64 - height / 2;
        float y1 = 64 + height / 2;
        UUID photo = PhotoItem.photoOf(stack);
        PhonePhotos.Picture picture = photo == null ? null : PhonePhotos.print(photo, true);
        if (picture == null) {
            mapQuad(consumers.getBuffer(RenderLayer.getText(PAPER)), position, x0, y0, x1, y1, 0, 0, 0, 1, 1, light);
            return;
        }
        float[] uv = cover(picture, 1 / CARD_HEIGHT);
        mapQuad(consumers.getBuffer(RenderLayer.getText(picture.texture())), position, x0, y0, x1, y1, 0, uv[0], uv[1], uv[2], uv[3], light);
    }

    private static void mapQuad(VertexConsumer buffer, Matrix4f position, float x0, float y0, float x1, float y1, float z,
                                float u0, float v0, float u1, float v1, int light) {
        buffer.vertex(position, x0, y1, z).color(255, 255, 255, 255).texture(u0, v1).light(light).next();
        buffer.vertex(position, x1, y1, z).color(255, 255, 255, 255).texture(u1, v1).light(light).next();
        buffer.vertex(position, x1, y0, z).color(255, 255, 255, 255).texture(u1, v0).light(light).next();
        buffer.vertex(position, x0, y0, z).color(255, 255, 255, 255).texture(u0, v0).light(light).next();
    }

    private static void quad(VertexConsumer buffer, Matrix4f position, Matrix3f normal, float x0, float y0, float x1, float y1, float z,
                             float u0, float v0, float u1, float v1, int light, int overlay) {
        vertex(buffer, position, normal, x0, y0, z, u0, v1, light, overlay);
        vertex(buffer, position, normal, x1, y0, z, u1, v1, light, overlay);
        vertex(buffer, position, normal, x1, y1, z, u1, v0, light, overlay);
        vertex(buffer, position, normal, x0, y1, z, u0, v0, light, overlay);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f position, Matrix3f normal, float x, float y, float z, float u, float v, int light, int overlay) {
        buffer.vertex(position, x, y, z).color(255, 255, 255, 255).texture(u, v).overlay(overlay).light(light).normal(normal, 0, 0, 1).next();
    }
}
