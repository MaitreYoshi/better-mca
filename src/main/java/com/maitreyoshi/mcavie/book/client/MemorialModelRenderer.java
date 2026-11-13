package com.maitreyoshi.mcavie.book.client;

import com.maitreyoshi.mcavie.life.MemorialBlock;
import com.maitreyoshi.mcavie.life.TombstoneBlocks;
import com.maitreyoshi.mcavie.mixin.compat.client.GuiGraphicsAccessor;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.conczin.mca.client.gui.ExtendedBookScreen;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class MemorialModelRenderer {
    private static final int[] SLICES = {13, 40, 67};
    private static final int[] SLICE_Y = {1, 4, 7};
    private static final int HALF_WIDTH = 60;
    private static final int TOP = 30;
    private static final int BOTTOM = 154;

    private MemorialModelRenderer() {
    }

    public static void register() {
        PictureInPictureRendererRegistry.register(context -> new Renderer());
    }

    public static void render(ExtendedBookScreen screen, GuiGraphicsExtractor context) {
        int cx = screen.width / 2 - 2;
        ((GuiGraphicsAccessor) context).mcavie$guiRenderState().addPicturesInPictureState(
                new State(cx - HALF_WIDTH, TOP, cx + HALF_WIDTH, BOTTOM, 13, null));
    }

    public record State(int x0, int y0, int x1, int y1, float scale, @Nullable ScreenRectangle scissorArea) implements PictureInPictureRenderState {
        @Override
        public ScreenRectangle bounds() {
            return PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea);
        }
    }

    private static final class Renderer extends PictureInPictureRenderer<State> {
        private final BlockModelRenderState block = new BlockModelRenderState();

        @Override
        public Class<State> getRenderStateClass() {
            return State.class;
        }

        @Override
        protected float getTranslateY(int height, int guiScale) {
            return height / 2f;
        }

        @Override
        protected void renderToTexture(State state, PoseStack matrices, SubmitNodeCollector collector) {
            Minecraft client = Minecraft.getInstance();
            client.gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
            BlockModelResolver resolver = new BlockModelResolver(client.getModelManager());
            matrices.pushPose();
            matrices.scale(1, -1, 1);
            matrices.mulPose(Axis.XP.rotationDegrees(25));
            matrices.mulPose(Axis.YP.rotationDegrees(225));
            matrices.translate(-0.5, -3.7, -0.5);
            for (int i = 0; i < SLICES.length; i++) {
                BlockState part = TombstoneBlocks.MEMORIAL.defaultBlockState()
                        .setValue(MemorialBlock.FACING, Direction.WEST)
                        .setValue(MemorialBlock.PART, SLICES[i]);
                block.clear();
                resolver.update(block, part, BlockDisplayContext.create());
                matrices.pushPose();
                matrices.translate(0, SLICE_Y[i], 0);
                block.submit(matrices, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
                matrices.popPose();
            }
            matrices.popPose();
        }

        @Override
        protected String getTextureLabel() {
            return "mca_vie memorial";
        }
    }
}
