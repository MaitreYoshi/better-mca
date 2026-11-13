package com.maitreyoshi.mcavie.life.client;

import com.maitreyoshi.mcavie.compat.client.Buffers;
import net.minecraft.client.input.KeyEvent;
import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import com.maitreyoshi.mcavie.compat.client.ClientPlayNetworking;
import com.maitreyoshi.mcavie.life.TombstoneBlocks;
import com.maitreyoshi.mcavie.life.TombstoneDetails;
import com.maitreyoshi.mcavie.life.VaultBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.block.TombstoneBlock;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
        ClientPlayNetworking.registerGlobalReceiver(VaultBlock.TITLE_OPEN, (client, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            String title = buf.readUtf();
            client.execute(() -> client.gui.setScreen(new TitleScreen(pos, title)));
        });
    }

    public static void render(TombstoneBlock.Data entity, @Nullable TombstoneDetails details, BlockState state, Font text,
                              PoseStack matrices, Buffers consumers, int light) {
        int part = state.getValue(VaultBlock.PART);
        int slot = VaultBlock.slotOf(part);
        if (slot < 0) {
            return;
        }
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        Direction back = facing.getOpposite();
        Direction left = facing.getCounterClockWise();
        BlockPos offset = VaultBlock.offset(facing, part);
        float tx = -offset.getX() + 0.5f - 0.5f * back.getStepX() - 0.5f * left.getStepX();
        float ty = -offset.getY();
        float tz = -offset.getZ() + 0.5f - 0.5f * back.getStepZ() - 0.5f * left.getStepZ();
        matrices.pushPose();
        matrices.mulPose(new Matrix4f(
                back.getStepX() / 16f, 0, back.getStepZ() / 16f, 0,
                0, 1 / 16f, 0, 0,
                left.getStepX() / 16f, 0, left.getStepZ() / 16f, 0,
                tx, ty, tz, 1));
        if (details != null) {
            person(details, slot, text, matrices, consumers, light);
        }
        if (slot == 0) {
            Component title = title(entity, state);
            if (title != null) {
                plaque(matrices, 30.6f, 11);
                line(title, 50, 2, 1.5f, 94, NAME_COLOR, text, matrices, consumers, light);
                matrices.popPose();
            }
        }
        matrices.popPose();
    }

    private static void plaque(PoseStack matrices, float top, float left) {
        matrices.pushPose();
        matrices.mulPose(new Matrix4f(0, 0, 1, 0, 0, -1, 0, 0, 1, 0, 0, 0, FACE, top, left, 1));
        matrices.scale(0.1f, 0.1f, 0.1f);
    }

    private static void person(TombstoneDetails details, int slot, Font text, PoseStack matrices, Buffers consumers, int light) {
        float z0 = slot % 2 == 0 ? 4.5f : 16.5f;
        float y0 = slot < 2 ? 22.6f : 16.6f;

        UUID image = TombstoneClient.portrait(details.person());
        if (image != null) {
            plaque(matrices, y0 + 5, z0 + 0.25f);
            matrices.translate(18.75f, 0, 0);
            TombstoneClient.drawOval(image, matrices, consumers, 0, 50, light);
            matrices.popPose();
        }

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(details.firstName()));
        if (!details.lastName().isEmpty()) {
            lines.add(Component.literal(details.lastName().toUpperCase(Locale.ROOT)));
        }
        lines.add(Component.literal(TombstoneClient.lives(details)));
        float scale = Math.min(1.15f, 34f / (lines.size() * 10));
        float y = (40 - lines.size() * 10 * scale) / 2;
        plaque(matrices, y0 + 4.5f, z0 + 4.5f);
        for (int i = 0; i < lines.size(); i++) {
            line(lines.get(i), 30, y, scale, 52, i == lines.size() - 1 ? INFO_COLOR : NAME_COLOR, text, matrices, consumers, light);
            y += 10 * scale;
        }
        matrices.popPose();
    }

    private static void line(Component line, float center, float y, float scale, float width, int color, Font text,
                             PoseStack matrices, Buffers consumers, int light) {
        float lineScale = Math.min(scale, width / Math.max(1, text.width(line)));
        matrices.pushPose();
        matrices.translate(center, y + (10 * scale - 10 * lineScale) / 2, 0);
        matrices.scale(lineScale, lineScale, lineScale);
        consumers.outlined(line.getVisualOrderText(), -text.width(line) / 2f, 0, color, OUTLINE, light);
        matrices.popPose();
    }

    @Nullable
    private static Component title(TombstoneBlock.Data holder, BlockState state) {
        String custom = ((TombstoneDetails.Holder) holder).mcavie$vaultTitle();
        if (custom != null) {
            return Component.literal(custom);
        }
        Level world = holder.getLevel();
        if (world == null) {
            return null;
        }
        BlockPos master = VaultBlock.master(holder.getBlockPos(), state);
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
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
        return first == null ? null : Component.translatable("block.mca_vie.family_vault.family", first.lastName().toUpperCase(Locale.ROOT));
    }

    private static final class TitleScreen extends Screen {
        private final BlockPos pos;
        private final String initial;
        private EditBox field;

        private TitleScreen(BlockPos pos, String initial) {
            super(Component.translatable("gui.mca_vie.vault.title"));
            this.pos = pos;
            this.initial = initial;
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        protected void init() {
            field = new EditBox(font, width / 2 - 100, height / 2 - 20, 200, 20, title);
            field.setMaxLength(VaultBlock.TITLE_MAX);
            field.setValue(initial);
            addRenderableWidget(field);
            setInitialFocus(field);
            addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> send()).bounds(width / 2 - 104, height / 2 + 10, 100, 20).build());
            addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(width / 2 + 4, height / 2 + 10, 100, 20).build());
        }

        private void send() {
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeBlockPos(pos);
            buf.writeUtf(field.getValue());
            ClientPlayNetworking.send(VaultBlock.TITLE_SET, buf);
            onClose();
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        int scanCode = event.scancode();
        int modifiers = event.modifiers();
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                send();
                return true;
            }
            return super.keyPressed(event);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            context.centeredText(font, title, width / 2, height / 2 - 48, 0xFFFFFF);
            context.centeredText(font, Component.translatable("gui.mca_vie.vault.hint"), width / 2, height / 2 - 34, 0xA0A0A0);
            super.extractRenderState(context, mouseX, mouseY, delta);
        }
    }
}
