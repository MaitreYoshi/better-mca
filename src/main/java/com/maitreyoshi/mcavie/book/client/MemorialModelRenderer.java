package com.maitreyoshi.mcavie.book.client;

import com.maitreyoshi.mcavie.life.MemorialBlock;
import com.maitreyoshi.mcavie.life.TombstoneBlocks;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.mca.client.gui.ExtendedBookScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Rendu 3D du monument aux morts pour MemorialModelPage, isolé pour ne pas charger de classes client sur un serveur dédié. */
public final class MemorialModelRenderer {
    private MemorialModelRenderer() {
    }

    private static final int[] SLICES = {13, 40, 67};
    private static final int[] SLICE_Y = {1, 4, 7};

    public static void render(ExtendedBookScreen screen, GuiGraphics context) {
        PoseStack matrices = context.pose();
        matrices.pushPose();
        matrices.translate(screen.width / 2f - 2, 92, 150);
        matrices.scale(13, -13, 13);
        matrices.mulPose(Axis.XP.rotationDegrees(25));
        matrices.mulPose(Axis.YP.rotationDegrees(225));
        matrices.translate(-0.5, -3.7, -0.5);
        Lighting.setupFor3DItems();
        for (int i = 0; i < SLICES.length; i++) {
            BlockState state = TombstoneBlocks.MEMORIAL.defaultBlockState()
                    .setValue(MemorialBlock.FACING, Direction.WEST)
                    .setValue(MemorialBlock.PART, SLICES[i]);
            matrices.pushPose();
            matrices.translate(0, SLICE_Y[i], 0);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, matrices, context.bufferSource(),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            matrices.popPose();
        }
        context.flush();
        Lighting.setupForFlatItems();
        matrices.popPose();
    }
}
