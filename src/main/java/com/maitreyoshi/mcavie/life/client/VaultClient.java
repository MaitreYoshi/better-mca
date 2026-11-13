package com.maitreyoshi.mcavie.life.client;

import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import com.maitreyoshi.mcavie.compat.client.ClientPlayNetworking;
import com.maitreyoshi.mcavie.life.TombstoneBlocks;
import com.maitreyoshi.mcavie.life.TombstoneDetails;
import com.maitreyoshi.mcavie.life.VaultBlock;
import dev.architectury.registry.client.rendering.RenderTypeRegistry;
import net.mca.block.TombstoneBlock;
import net.minecraft.block.BlockState;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class VaultClient {
    private static final float FACE = 20.73f;
    private static final int NAME_COLOR = 0xF2CC5A;
    private static final int INFO_COLOR = 0xD9B24C;
    private static final int OUTLINE = 0x3E2A0A;

    private VaultClient() {
    }

    public static void register() {
        RenderTypeRegistry.register(RenderLayer.getCutout(), TombstoneBlocks.VAULT);
        ClientPlayNetworking.registerGlobalReceiver(VaultBlock.TITLE_OPEN, (client, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            String title = buf.readString();
            client.execute(() -> client.setScreen(new TitleScreen(pos, title)));
        });
    }

    public static void render(TombstoneBlock.Data entity, @Nullable TombstoneDetails details, BlockState state, TextRenderer text,
                              MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        int part = state.get(VaultBlock.PART);
        int slot = VaultBlock.slotOf(part);
        if (slot < 0) {
            return;
        }
        Direction facing = state.get(Properties.HORIZONTAL_FACING);
        Direction back = facing.getOpposite();
        Direction left = facing.rotateYCounterclockwise();
        BlockPos offset = VaultBlock.offset(facing, part);
        float tx = -offset.getX() + 0.5f - 0.5f * back.getOffsetX() - 0.5f * left.getOffsetX();
        float ty = -offset.getY();
        float tz = -offset.getZ() + 0.5f - 0.5f * back.getOffsetZ() - 0.5f * left.getOffsetZ();
        matrices.push();
        matrices.multiplyPositionMatrix(new Matrix4f(
                back.getOffsetX() / 16f, 0, back.getOffsetZ() / 16f, 0,
                0, 1 / 16f, 0, 0,
                left.getOffsetX() / 16f, 0, left.getOffsetZ() / 16f, 0,
                tx, ty, tz, 1));
        if (details != null) {
            person(details, slot, text, matrices, consumers, light);
        }
        if (slot == 0) {
            Text title = title(entity, state);
            if (title != null) {
                plaque(matrices, 30.6f, 11);
                line(title, 50, 2, 1.5f, 94, NAME_COLOR, text, matrices, consumers, light);
                matrices.pop();
            }
        }
        matrices.pop();
    }

    private static void plaque(MatrixStack matrices, float top, float left) {
        matrices.push();
        matrices.multiplyPositionMatrix(new Matrix4f(0, 0, 1, 0, 0, -1, 0, 0, 1, 0, 0, 0, FACE, top, left, 1));
        matrices.scale(0.1f, 0.1f, 0.1f);
    }

    private static void person(TombstoneDetails details, int slot, TextRenderer text, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        float z0 = slot % 2 == 0 ? 4.5f : 16.5f;
        float y0 = slot < 2 ? 22.6f : 16.6f;

        UUID image = TombstoneClient.portrait(details.person());
        if (image != null) {
            plaque(matrices, y0 + 5, z0 + 0.25f);
            matrices.translate(18.75f, 0, 0);
            TombstoneClient.drawOval(image, matrices, consumers, 0, 50, light);
            matrices.pop();
        }

        List<Text> lines = new ArrayList<>();
        lines.add(Text.literal(details.firstName()));
        if (!details.lastName().isEmpty()) {
            lines.add(Text.literal(details.lastName().toUpperCase(Locale.ROOT)));
        }
        lines.add(Text.literal(TombstoneClient.lives(details)));
        float scale = Math.min(1.15f, 34f / (lines.size() * 10));
        float y = (40 - lines.size() * 10 * scale) / 2;
        plaque(matrices, y0 + 4.5f, z0 + 4.5f);
        for (int i = 0; i < lines.size(); i++) {
            line(lines.get(i), 30, y, scale, 52, i == lines.size() - 1 ? INFO_COLOR : NAME_COLOR, text, matrices, consumers, light);
            y += 10 * scale;
        }
        matrices.pop();
    }

    private static void line(Text line, float center, float y, float scale, float width, int color, TextRenderer text,
                             MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        float lineScale = Math.min(scale, width / Math.max(1, text.getWidth(line)));
        matrices.push();
        matrices.translate(center, y + (10 * scale - 10 * lineScale) / 2, 0);
        matrices.scale(lineScale, lineScale, lineScale);
        text.drawWithOutline(line.asOrderedText(), -text.getWidth(line) / 2f, 0, color, OUTLINE, matrices.peek().getPositionMatrix(), consumers, light);
        matrices.pop();
    }

    @Nullable
    private static Text title(TombstoneBlock.Data holder, BlockState state) {
        String custom = ((TombstoneDetails.Holder) holder).mcavie$vaultTitle();
        if (custom != null) {
            return Text.literal(custom);
        }
        World world = holder.getWorld();
        if (world == null) {
            return null;
        }
        BlockPos master = VaultBlock.master(holder.getPos(), state);
        Direction facing = state.get(Properties.HORIZONTAL_FACING);
        TombstoneDetails first = null;
        for (int part : VaultBlock.SLOTS) {
            TombstoneDetails details = TombstoneBlock.Data.of(world.getBlockEntity(VaultBlock.partPos(master, facing, part)))
                    .filter(TombstoneBlock.Data::hasEntity)
                    .map(data -> ((TombstoneDetails.Holder) data).mcavie$details())
                    .orElse(null);
            if (details != null && !details.lastName().isEmpty() && (first == null || details.death() < first.death())) {
                first = details;
            }
        }
        return first == null ? null : Text.translatable("block.mca_vie.family_vault.family", first.lastName().toUpperCase(Locale.ROOT));
    }

    private static final class TitleScreen extends Screen {
        private final BlockPos pos;
        private final String initial;
        private TextFieldWidget field;

        private TitleScreen(BlockPos pos, String initial) {
            super(Text.translatable("gui.mca_vie.vault.title"));
            this.pos = pos;
            this.initial = initial;
        }

        @Override
        public boolean shouldPause() {
            return false;
        }

        @Override
        protected void init() {
            field = new TextFieldWidget(textRenderer, width / 2 - 100, height / 2 - 20, 200, 20, title);
            field.setMaxLength(VaultBlock.TITLE_MAX);
            field.setText(initial);
            addDrawableChild(field);
            setInitialFocus(field);
            addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> send()).dimensions(width / 2 - 104, height / 2 + 10, 100, 20).build());
            addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, b -> close()).dimensions(width / 2 + 4, height / 2 + 10, 100, 20).build());
        }

        private void send() {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBlockPos(pos);
            buf.writeString(field.getText());
            ClientPlayNetworking.send(VaultBlock.TITLE_SET, buf);
            close();
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                send();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            renderBackground(context);
            context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 48, 0xFFFFFF);
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.mca_vie.vault.hint"), width / 2, height / 2 - 34, 0xA0A0A0);
            super.render(context, mouseX, mouseY, delta);
        }
    }
}
