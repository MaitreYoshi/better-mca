package com.maitreyoshi.mcavie.compat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

import java.util.function.BiConsumer;

/**
 * Remplace l'ancien MultiBufferSource dans nos rendus 26.x : le dessin est soumis puis exécuté plus tard,
 * avec une copie de la matrice courante (ne jamais réutiliser une matrice capturée avant).
 */
public final class Buffers {
    private final SubmitNodeCollector collector;
    private final PoseStack matrices;

    public Buffers(SubmitNodeCollector collector, PoseStack matrices) {
        this.collector = collector;
        this.matrices = matrices;
    }

    public SubmitNodeCollector collector() {
        return collector;
    }

    public PoseStack matrices() {
        return matrices;
    }

    /** Ancien {@code consumers.getBuffer(type)} suivi du dessin : reçoit le tampon et la matrice de position. */
    public void draw(RenderType type, BiConsumer<VertexConsumer, Matrix4f> drawer) {
        collector.submitCustomGeometry(matrices, type, (pose, buffer) -> drawer.accept(buffer, pose.pose()));
    }

    /** Ancien {@code font.drawInBatch} (couleur RVB ou ARVB ; sans alpha elle devient opaque). */
    public void text(FormattedCharSequence line, float x, float y, int color, boolean shadow, Font.DisplayMode mode, int background, int light) {
        collector.submitText(matrices, x, y, line, shadow, mode, light, opaque(color), background, 0);
    }

    /** Ancien {@code font.drawInBatch8xOutline}. */
    public void outlined(FormattedCharSequence line, float x, float y, int color, int outline, int light) {
        collector.submitText(matrices, x, y, line, false, Font.DisplayMode.NORMAL, light, opaque(color), 0, opaque(outline));
    }

    public static int opaque(int color) {
        return (color & 0xFF000000) == 0 ? color | 0xFF000000 : color;
    }

    /** Ancien LevelRenderer.getLightColor(monde, position). */
    public static int light(net.minecraft.world.level.BlockAndLightGetter world, net.minecraft.core.BlockPos pos) {
        return net.minecraft.util.LightCoordsUtil.pack(world.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos),
                world.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos));
    }
}
