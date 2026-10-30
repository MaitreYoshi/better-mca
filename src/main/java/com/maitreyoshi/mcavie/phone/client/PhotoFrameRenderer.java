package com.maitreyoshi.mcavie.phone.client;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.phone.PhotoFrameEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.UUID;

public class PhotoFrameRenderer extends EntityRenderer<PhotoFrameEntity> {
    private static final Identifier PAPER = MCAVie.id("textures/entity/photo_paper.png");

    public PhotoFrameRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(PhotoFrameEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - entity.getYaw()));
        float w = entity.getWidthPixels() / 16f;
        float h = entity.getHeightPixels() / 16f;
        MatrixStack.Entry entry = matrices.peek();
        UUID photo = entity.photo();
        PhonePhotos.Picture picture = photo == null ? null : PhonePhotos.print(photo, true);
        if (picture == null) {
            quad(consumers.getBuffer(RenderLayer.getEntitySolid(PAPER)), entry.getPositionMatrix(), entry.getNormalMatrix(),
                    w / 2, h / 2, 0, 0, 0, 1, 1, light);
        } else {
            float[] uv = PhotoItemRenderer.cover(picture, w / h);
            quad(consumers.getBuffer(RenderLayer.getEntitySolid(picture.texture())), entry.getPositionMatrix(), entry.getNormalMatrix(),
                    w / 2, h / 2, 0, uv[0], uv[1], uv[2], uv[3], light);
        }
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, consumers, light);
    }

    private static void quad(VertexConsumer buffer, Matrix4f position, Matrix3f normal, float hw, float hh, float z,
                             float u0, float v0, float u1, float v1, int light) {
        vertex(buffer, position, normal, hw, -hh, z, u0, v1, light);
        vertex(buffer, position, normal, -hw, -hh, z, u1, v1, light);
        vertex(buffer, position, normal, -hw, hh, z, u1, v0, light);
        vertex(buffer, position, normal, hw, hh, z, u0, v0, light);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f position, Matrix3f normal, float x, float y, float z, float u, float v, int light) {
        buffer.vertex(position, x, y, z).color(255, 255, 255, 255).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light)
                .normal(normal, 0, 0, -1).next();
    }

    @Override
    public Identifier getTexture(PhotoFrameEntity entity) {
        return PAPER;
    }
}
