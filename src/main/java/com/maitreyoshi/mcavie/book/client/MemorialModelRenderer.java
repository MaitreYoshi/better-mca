package com.maitreyoshi.mcavie.book.client;

import com.maitreyoshi.mcavie.life.MemorialBlock;
import com.maitreyoshi.mcavie.life.TombstoneBlocks;
import net.mca.client.gui.ExtendedBookScreen;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;

/** Rendu 3D du monument aux morts pour MemorialModelPage, isolé pour ne pas charger de classes client sur un serveur dédié. */
public final class MemorialModelRenderer {
    private MemorialModelRenderer() {
    }

    private static final int[] SLICES = {13, 40, 67};
    private static final int[] SLICE_Y = {1, 4, 7};

    public static void render(ExtendedBookScreen screen, DrawContext context) {
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(screen.width / 2f - 2, 92, 150);
        matrices.scale(13, -13, 13);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(25));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(225));
        matrices.translate(-0.5, -3.7, -0.5);
        DiffuseLighting.enableGuiDepthLighting();
        for (int i = 0; i < SLICES.length; i++) {
            BlockState state = TombstoneBlocks.MEMORIAL.getDefaultState()
                    .with(MemorialBlock.FACING, Direction.WEST)
                    .with(MemorialBlock.PART, SLICES[i]);
            matrices.push();
            matrices.translate(0, SLICE_Y[i], 0);
            MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(state, matrices, context.getVertexConsumers(),
                    LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
            matrices.pop();
        }
        context.draw();
        DiffuseLighting.disableGuiDepthLighting();
        matrices.pop();
    }
}
