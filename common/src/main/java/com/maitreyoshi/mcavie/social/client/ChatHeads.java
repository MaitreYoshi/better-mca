package com.maitreyoshi.mcavie.social.client;

import com.maitreyoshi.mcavie.social.SocialConversation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.OrderedText;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

public final class ChatHeads {
    private static final int SIZE = 8;
    private static final int MIN_ALPHA = 80;

    private ChatHeads() {
    }

    public static void draw(DrawContext context, OrderedText text, int x, int y, int color) {
        if (((color >>> 24) & 0xFF) < MIN_ALPHA) {
            return;
        }
        String[] insertion = new String[1];
        text.accept((index, style, codePoint) -> {
            insertion[0] = style.getInsertion();
            return false;
        });
        LivingEntity villager = find(insertion[0]);
        if (villager == null) {
            return;
        }
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        Vector3f from = matrix.transformPosition(x, y, 0, new Vector3f());
        Vector3f to = matrix.transformPosition(x + SIZE, y + SIZE, 0, new Vector3f());
        float height = villager.getHeight();
        int scale = Math.max(1, Math.round(SIZE / (height * 0.25f)));
        float bodyYaw = villager.bodyYaw;
        float yaw = villager.getYaw();
        float pitch = villager.getPitch();
        float headYaw = villager.headYaw;
        float prevHeadYaw = villager.prevHeadYaw;
        villager.bodyYaw = 180;
        villager.setYaw(180);
        villager.setPitch(0);
        villager.headYaw = 180;
        villager.prevHeadYaw = 180;
        context.enableScissor(MathHelper.floor(from.x), MathHelper.floor(from.y), MathHelper.ceil(to.x), MathHelper.ceil(to.y));
        try {
            InventoryScreen.drawEntity(context, x + SIZE / 2, y + Math.round(height * scale), scale,
                    new Quaternionf().rotateZ((float) Math.PI), new Quaternionf(), villager);
        } finally {
            context.disableScissor();
            villager.bodyYaw = bodyYaw;
            villager.setYaw(yaw);
            villager.setPitch(pitch);
            villager.headYaw = headYaw;
            villager.prevHeadYaw = prevHeadYaw;
        }
    }

    private static LivingEntity find(String insertion) {
        if (insertion == null || !insertion.startsWith(SocialConversation.HEAD_MARKER) || MinecraftClient.getInstance().world == null) {
            return null;
        }
        String[] parts = insertion.substring(SocialConversation.HEAD_MARKER.length()).split(":", 2);
        if (parts.length != 2) {
            return null;
        }
        try {
            Entity entity = MinecraftClient.getInstance().world.getEntityById(Integer.parseInt(parts[0]));
            return entity instanceof LivingEntity living && entity.getUuid().equals(UUID.fromString(parts[1])) ? living : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
