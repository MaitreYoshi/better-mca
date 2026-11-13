package com.maitreyoshi.mcavie.social.client;

import com.maitreyoshi.mcavie.social.SocialConversation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class ChatHeads {
    private static final int SIZE = 8;
    private static final int MIN_ALPHA = 80;
    public static final int CHAT_RAISE = 12;

    private ChatHeads() {
    }

    public static void draw(GuiGraphics context, FormattedCharSequence text, int x, int y, int color) {
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
        Matrix4f matrix = context.pose().last().pose();
        Vector3f from = matrix.transformPosition(x, y, 0, new Vector3f());
        Vector3f to = matrix.transformPosition(x + SIZE, y + SIZE, 0, new Vector3f());
        float height = villager.getBbHeight();
        int scale = Math.max(1, Math.round(SIZE / (height * 0.25f)));
        float bodyYaw = villager.yBodyRot;
        float yaw = villager.getYRot();
        float pitch = villager.getXRot();
        float headYaw = villager.yHeadRot;
        float prevHeadYaw = villager.yHeadRotO;
        villager.yBodyRot = 180;
        villager.setYRot(180);
        villager.setXRot(0);
        villager.yHeadRot = 180;
        villager.yHeadRotO = 180;
        context.enableScissor(Mth.floor(from.x), Mth.floor(from.y), Mth.ceil(to.x), Mth.ceil(to.y));
        try {
            InventoryScreen.renderEntityInInventory(context, x + SIZE / 2, y + Math.round(height * scale), scale,
                    new Quaternionf().rotateZ((float) Math.PI), new Quaternionf(), villager);
        } finally {
            context.disableScissor();
            villager.yBodyRot = bodyYaw;
            villager.setYRot(yaw);
            villager.setXRot(pitch);
            villager.yHeadRot = headYaw;
            villager.yHeadRotO = prevHeadYaw;
        }
    }

    public static Component withHead(Component text, UUID uuid) {
        ClientLevel world = Minecraft.getInstance().level;
        if (world == null || uuid == null) {
            return text;
        }
        for (Entity entity : world.entitiesForRendering()) {
            if (entity instanceof LivingEntity && uuid.equals(entity.getUUID())) {
                return Component.empty().append(Component.literal("   ").withStyle(s -> s.withInsertion(SocialConversation.HEAD_MARKER + entity.getId() + ":" + uuid))).append(text);
            }
        }
        return text;
    }

    private static LivingEntity find(String insertion) {
        if (insertion == null || !insertion.startsWith(SocialConversation.HEAD_MARKER) || Minecraft.getInstance().level == null) {
            return null;
        }
        String[] parts = insertion.substring(SocialConversation.HEAD_MARKER.length()).split(":", 2);
        if (parts.length != 2) {
            return null;
        }
        try {
            Entity entity = Minecraft.getInstance().level.getEntity(Integer.parseInt(parts[0]));
            return entity instanceof LivingEntity living && entity.getUUID().equals(UUID.fromString(parts[1])) ? living : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
