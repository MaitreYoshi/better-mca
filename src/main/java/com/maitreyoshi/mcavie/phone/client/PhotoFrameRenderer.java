package com.maitreyoshi.mcavie.phone.client;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.phone.PhotoFrameEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.UUID;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

public class PhotoFrameRenderer extends EntityRenderer<PhotoFrameEntity> {
    private static final Identifier PAPER = MCAVie.id("textures/entity/photo_paper.png");

    public PhotoFrameRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(PhotoFrameEntity entity, float yaw, float tickDelta, PoseStack matrices, MultiBufferSource consumers, int light) {
        matrices.pushPose();
        Direction facing = entity.facing();
        if (facing == Direction.UP) {
            matrices.mulPose(Axis.YP.rotationDegrees(-entity.top().toYRot()));
            matrices.mulPose(Axis.XP.rotationDegrees(90.0f));
        } else if (facing == Direction.DOWN) {
            matrices.mulPose(Axis.YP.rotationDegrees(180.0f - entity.top().toYRot()));
            matrices.mulPose(Axis.XP.rotationDegrees(-90.0f));
        } else {
            matrices.mulPose(Axis.YP.rotationDegrees(180.0f - entity.getYRot()));
        }
        float w = entity.getWidth() / 16f;
        float h = entity.getHeight() / 16f;
        PoseStack.Pose entry = matrices.last();
        UUID photo = entity.photo();
        PhonePhotos.Picture picture = photo == null ? null : PhonePhotos.print(photo, true);
        if (picture == null) {
            quad(consumers.getBuffer(RenderType.entitySolid(PAPER)), entry.pose(), entry.normal(),
                    w / 2, h / 2, 0, 0, 0, 1, 1, light);
        } else {
            float[] uv = PhotoItemRenderer.cover(picture, w / h);
            quad(consumers.getBuffer(RenderType.entitySolid(picture.texture())), entry.pose(), entry.normal(),
                    w / 2, h / 2, 0, uv[0], uv[1], uv[2], uv[3], light);
        }
        matrices.popPose();
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
        buffer.vertex(position, x, y, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normal, 0, 0, -1).endVertex();
    }

    @Override
    public Identifier getTextureLocation(PhotoFrameEntity entity) {
        return PAPER;
    }
}
