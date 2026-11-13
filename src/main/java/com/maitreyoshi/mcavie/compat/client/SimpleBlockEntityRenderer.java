package com.maitreyoshi.mcavie.compat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Rendu de bloc-entité à l'ancienne (render(entité, delta, matrices, tampons, lumière, overlay))
 * branché sur le système 26.x (état extrait puis soumission).
 */
public abstract class SimpleBlockEntityRenderer<T extends BlockEntity> implements BlockEntityRenderer<T, SimpleBlockEntityRenderer.State<T>> {
    public static class State<T> extends BlockEntityRenderState {
        public T entity;
        public float tickDelta;
    }

    @Override
    public State<T> createRenderState() {
        return new State<>();
    }

    @Override
    public void extractRenderState(T entity, State<T> state, float partialTicks, Vec3 camera, @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, camera, breakProgress);
        state.entity = entity;
        state.tickDelta = partialTicks;
    }

    @Override
    public void submit(State<T> state, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.entity != null) {
            render(state.entity, state.tickDelta, matrices, new Buffers(collector, matrices), state.lightCoords, OverlayTexture.NO_OVERLAY);
        }
    }

    protected abstract void render(T entity, float tickDelta, PoseStack matrices, Buffers consumers, int light, int overlay);
}
