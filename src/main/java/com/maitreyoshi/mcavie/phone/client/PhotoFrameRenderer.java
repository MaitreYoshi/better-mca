package com.maitreyoshi.mcavie.phone.client;

import net.minecraft.client.renderer.rendertype.RenderTypes;
import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.phone.PhotoFrameEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.UUID;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.maitreyoshi.mcavie.compat.client.Buffers;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

public class PhotoFrameRenderer extends EntityRenderer<PhotoFrameEntity, PhotoFrameRenderer.State> {
    private static final Identifier PAPER = MCAVie.id("textures/entity/photo_paper.png");

    public static class State extends EntityRenderState {
        Direction facing = Direction.NORTH;
        Direction top = Direction.UP;
        float yRot;
        float width;
        float height;
        UUID photo;
    }

    public PhotoFrameRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PhotoFrameEntity entity, State state, float tickDelta) {
        super.extractRenderState(entity, state, tickDelta);
        state.facing = entity.facing();
        state.top = entity.top();
        state.yRot = entity.getYRot();
        state.width = entity.getWidth() / 16f;
        state.height = entity.getHeight() / 16f;
        state.photo = entity.photo();
    }

    @Override
    public void submit(State state, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera) {
        matrices.pushPose();
        Direction facing = state.facing;
        if (facing == Direction.UP) {
            matrices.mulPose(Axis.YP.rotationDegrees(-state.top.toYRot()));
            matrices.mulPose(Axis.XP.rotationDegrees(90.0f));
        } else if (facing == Direction.DOWN) {
            matrices.mulPose(Axis.YP.rotationDegrees(180.0f - state.top.toYRot()));
            matrices.mulPose(Axis.XP.rotationDegrees(-90.0f));
        } else {
            matrices.mulPose(Axis.YP.rotationDegrees(180.0f - state.yRot));
        }
        float w = state.width;
        float h = state.height;
        int light = state.lightCoords;
        Buffers consumers = new Buffers(collector, matrices);
        PhonePhotos.Picture picture = state.photo == null ? null : PhonePhotos.print(state.photo, true);
        if (picture == null) {
            consumers.draw(RenderTypes.entitySolid(PAPER), (buffer, position) -> quad(buffer, position, w / 2, h / 2, 0, 0, 0, 1, 1, light));
        } else {
            float[] uv = PhotoItemRenderer.cover(picture, w / h);
            consumers.draw(RenderTypes.entitySolid(picture.texture()), (buffer, position) -> quad(buffer, position, w / 2, h / 2, 0, uv[0], uv[1], uv[2], uv[3], light));
        }
        matrices.popPose();
        super.submit(state, matrices, collector, camera);
    }

    private static void quad(VertexConsumer buffer, Matrix4f position, float hw, float hh, float z,
                             float u0, float v0, float u1, float v1, int light) {
        vertex(buffer, position, hw, -hh, z, u0, v1, light);
        vertex(buffer, position, -hw, -hh, z, u1, v1, light);
        vertex(buffer, position, -hw, hh, z, u1, v0, light);
        vertex(buffer, position, hw, hh, z, u0, v0, light);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f position, float x, float y, float z, float u, float v, int light) {
        buffer.addVertex(position, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(0, 0, -1);
    }
}
