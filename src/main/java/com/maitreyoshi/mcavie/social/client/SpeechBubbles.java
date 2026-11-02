package com.maitreyoshi.mcavie.social.client;

import com.maitreyoshi.mcavie.social.SocialConversation;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bulles de dialogue au-dessus des villageois, reçues du serveur (seuls les joueurs proches les reçoivent). */
public final class SpeechBubbles {
    private static final int MAX_WIDTH = 150;
    private static final double MAX_DISTANCE_SQ = 24 * 24;

    private record Bubble(Text text, boolean angry, long expires) {
    }

    private static final Map<Integer, Bubble> BUBBLES = new HashMap<>();

    private SpeechBubbles() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SocialConversation.BUBBLE, (client, handler, buf, responseSender) -> {
            int entityId = buf.readVarInt();
            Text text = buf.readText();
            boolean angry = buf.readBoolean();
            int ticks = buf.readVarInt();
            client.execute(() -> BUBBLES.put(entityId, new Bubble(text, angry, System.currentTimeMillis() + ticks * 50L)));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BUBBLES.clear());
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> BUBBLES.remove(entity.getId()));
    }

    public static void render(Entity entity, EntityRenderDispatcher dispatcher, TextRenderer textRenderer, MatrixStack matrices,
                              VertexConsumerProvider consumers, int light) {
        Bubble bubble = BUBBLES.get(entity.getId());
        if (bubble == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now > bubble.expires) {
            BUBBLES.remove(entity.getId());
            return;
        }
        if (dispatcher.getSquaredDistanceToCamera(entity) > MAX_DISTANCE_SQ || entity.isInvisibleTo(MinecraftClient.getInstance().player)) {
            return;
        }
        List<OrderedText> lines = textRenderer.wrapLines(bubble.text, MAX_WIDTH);
        int color = bubble.angry ? 0xffff7a7a : 0xffffffff;
        int background = bubble.angry ? 0xa0400000 : 0xa0000000;

        matrices.push();
        // au-dessus du nom et de la barre de bonheur
        matrices.translate(0.0F, entity.getHeight() + 0.85F, 0.0F);
        matrices.multiply(dispatcher.getRotation());
        matrices.scale(-0.025F, -0.025F, 0.025F);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        int lineHeight = textRenderer.fontHeight + 1;
        float y = -lines.size() * lineHeight;
        for (OrderedText line : lines) {
            float x = -textRenderer.getWidth(line) / 2f;
            textRenderer.draw(line, x, y, color, false, matrix, consumers, TextRenderer.TextLayerType.NORMAL, background, light);
            y += lineHeight;
        }
        matrices.pop();
    }
}
