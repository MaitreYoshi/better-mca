package com.maitreyoshi.mcavie.life.client;

import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import com.maitreyoshi.mcavie.compat.client.ClientPlayNetworking;
import com.maitreyoshi.mcavie.life.MemorialBlock;
import com.maitreyoshi.mcavie.life.MemorialBlockEntity;
import com.maitreyoshi.mcavie.life.MemorialRegistry;
import com.maitreyoshi.mcavie.life.TombstoneBlocks;
import com.maitreyoshi.mcavie.life.TombstoneDetails;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import dev.architectury.registry.client.rendering.RenderTypeRegistry;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;

public final class MemorialClient {
    private static final int NAME_COLOR = 0xF2CC5A;
    private static final int OUTLINE = 0x3E2A0A;
    private static final int ENGRAVED = 0x3A3328;
    private static final int PER_PLAQUE = 10;

    private MemorialClient() {
    }

    public static void register() {
        RenderTypeRegistry.register(RenderType.cutout(), TombstoneBlocks.MEMORIAL);
        BlockEntityRendererRegistry.register(TombstoneBlocks.MEMORIAL_ENTITY, context -> new Renderer());
        ClientPlayNetworking.registerGlobalReceiver(MemorialBlock.EDIT_OPEN, (client, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            String title = buf.readUtf();
            String dates = buf.readUtf();
            client.execute(() -> client.setScreen(new EditScreen(pos, title, dates)));
        });
        ClientPlayNetworking.registerGlobalReceiver(MemorialBlock.LIST_OPEN, (client, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            client.execute(() -> {
                if (client.level != null && client.level.getBlockEntity(pos) instanceof MemorialBlockEntity memorial) {
                    client.setScreen(new ListScreen(memorial));
                }
            });
        });
    }

    static String name(TombstoneDetails details) {
        return details.firstName() + (details.lastName().isEmpty() ? "" : " " + details.lastName().toUpperCase(Locale.ROOT));
    }

    static Component inscription(MemorialBlockEntity memorial) {
        if (memorial.title() != null) {
            return Component.literal(memorial.title());
        }
        return memorial.villageName().isEmpty() ? Component.translatable("block.mca_vie.war_memorial.inscription_short")
                : Component.translatable("block.mca_vie.war_memorial.inscription", memorial.villageName());
    }

    private static final class Renderer implements BlockEntityRenderer<MemorialBlockEntity> {
        @Override
        public boolean shouldRenderOffScreen(MemorialBlockEntity entity) {
            return true;
        }

        @Override
        public int getViewDistance() {
            return 96;
        }

        @Override
        public void render(MemorialBlockEntity memorial, float tickDelta, PoseStack matrices, MultiBufferSource consumers, int light, int overlay) {
            Level world = memorial.getLevel();
            if (world == null) {
                return;
            }
            Direction facing = memorial.getBlockState().getValue(MemorialBlock.FACING);
            Direction back = facing.getOpposite();
            Direction left = facing.getCounterClockWise();
            BlockPos pos = memorial.getBlockPos();
            Font text = Minecraft.getInstance().font;
            matrices.pushPose();
            matrices.mulPoseMatrix(new Matrix4f(
                    back.getStepX() / 16f, 0, back.getStepZ() / 16f, 0,
                    0, 1 / 16f, 0, 0,
                    left.getStepX() / 16f, 0, left.getStepZ() / 16f, 0,
                    0.5f - 0.5f * back.getStepX() - 0.5f * left.getStepX(), 0, 0.5f - 0.5f * back.getStepZ() - 0.5f * left.getStepZ(), 1));

            int frontLight = LevelRenderer.getLightColor(world, pos.relative(facing, 2).above(3));
            face(matrices, new Vector3f(1.13f, 84, 2.5f), new Vector3f(0, 0, 1), new Vector3f(1, 0, 0));
            block(inscription(memorial), 110, 104, 1.6f, text, matrices, consumers, frontLight);
            matrices.popPose();
            if (memorial.dates() != null) {
                face(matrices, new Vector3f(-3.02f, 48, -3), new Vector3f(0, 0, 1), new Vector3f(1, 0, 0));
                block(Component.literal(memorial.dates()), 220, 56, 4.5f, text, matrices, consumers, frontLight);
                matrices.popPose();
            }

            List<MemorialRegistry.Fallen> all = memorial.fallen();
            int start = Math.max(0, Math.min(all.size(), memorial.first()));
            List<MemorialRegistry.Fallen> shown = all.subList(start, Math.min(all.size(), start + 2 * PER_PLAQUE));
            face(matrices, new Vector3f(-3, 36, 22.52f), new Vector3f(1, 0, 0), new Vector3f(0, 0, -1));
            names(shown.subList(0, Math.min(PER_PLAQUE, shown.size())), text, matrices, consumers,
                    LevelRenderer.getLightColor(world, pos.relative(left, 2).above()));
            matrices.popPose();
            if (shown.size() > PER_PLAQUE) {
                face(matrices, new Vector3f(19, 36, -6.52f), new Vector3f(-1, 0, 0), new Vector3f(0, 0, 1));
                names(shown.subList(PER_PLAQUE, shown.size()), text, matrices, consumers,
                        LevelRenderer.getLightColor(world, pos.relative(left.getOpposite(), 2).above()));
                matrices.popPose();
            }
            matrices.popPose();
        }
    }

    private static void face(PoseStack matrices, Vector3f origin, Vector3f right, Vector3f inward) {
        matrices.pushPose();
        matrices.mulPoseMatrix(new Matrix4f(
                right.x, right.y, right.z, 0,
                0, -1, 0, 0,
                inward.x, inward.y, inward.z, 0,
                origin.x, origin.y, origin.z, 1));
        matrices.scale(0.1f, 0.1f, 0.1f);
    }

    private static void block(Component content, float width, float height, float maxScale, Font text, PoseStack matrices,
                              MultiBufferSource consumers, int light) {
        float scale = maxScale;
        List<FormattedCharSequence> lines = text.split(content, (int) (width / scale));
        while (scale > 0.4f && lines.size() * 10 * scale > height) {
            scale -= 0.1f;
            lines = text.split(content, (int) (width / scale));
        }
        float y = (height - lines.size() * 10 * scale) / 2 + 3;
        for (FormattedCharSequence line : lines) {
            matrices.pushPose();
            matrices.translate(width / 2, y, 0);
            matrices.scale(scale, scale, scale);
            text.drawInBatch(line, -text.width(line) / 2f, 0, ENGRAVED, false, matrices.last().pose(), consumers,
                    Font.DisplayMode.NORMAL, 0, light);
            matrices.popPose();
            y += 10 * scale;
        }
    }

    private static void names(List<MemorialRegistry.Fallen> list, Font text, PoseStack matrices, MultiBufferSource consumers, int light) {
        float margin = 16;
        float row = (255 - 2 * margin) / PER_PLAQUE;
        for (int i = 0; i < list.size(); i++) {
            TombstoneDetails details = list.get(i).details();
            float top = margin + i * row;
            UUID image = TombstoneClient.portrait(details.person());
            if (image != null) {
                matrices.pushPose();
                matrices.translate(margin + 8, 0, 0);
                TombstoneClient.drawOval(image, matrices, consumers, top + 1.5f, row - 3, light);
                matrices.popPose();
            }
            Component line = Component.literal(name(details));
            float scale = Math.min(1.4f, (220 - 2 * margin - 22) / Math.max(1, text.width(line)));
            matrices.pushPose();
            matrices.translate(margin + 20, top + (row - 8 * scale) / 2, 0);
            matrices.scale(scale, scale, scale);
            text.drawInBatch8xOutline(line.getVisualOrderText(), 0, 0, NAME_COLOR, OUTLINE, matrices.last().pose(), consumers, light);
            matrices.popPose();
        }
    }

    private static final class EditScreen extends Screen {
        private final BlockPos pos;
        private final String initialTitle;
        private final String initialDates;
        private EditBox titleField;
        private EditBox datesField;

        private EditScreen(BlockPos pos, String title, String dates) {
            super(Component.translatable("gui.mca_vie.war_memorial.edit"));
            this.pos = pos;
            this.initialTitle = title;
            this.initialDates = dates;
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        protected void init() {
            titleField = new EditBox(font, width / 2 - 120, height / 2 - 36, 240, 20, Component.translatable("gui.mca_vie.war_memorial.inscription"));
            titleField.setMaxLength(MemorialBlock.TITLE_MAX);
            titleField.setValue(initialTitle);
            addRenderableWidget(titleField);
            datesField = new EditBox(font, width / 2 - 120, height / 2 + 4, 240, 20, Component.translatable("gui.mca_vie.war_memorial.dates"));
            datesField.setMaxLength(MemorialBlock.DATES_MAX);
            datesField.setValue(initialDates);
            addRenderableWidget(datesField);
            setInitialFocus(titleField);
            addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> send()).bounds(width / 2 - 104, height / 2 + 34, 100, 20).build());
            addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(width / 2 + 4, height / 2 + 34, 100, 20).build());
        }

        private void send() {
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeBlockPos(pos);
            buf.writeUtf(titleField.getValue());
            buf.writeUtf(datesField.getValue());
            ClientPlayNetworking.send(MemorialBlock.EDIT_SET, buf);
            onClose();
        }

        @Override
        public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
            renderBackground(context);
            context.drawCenteredString(font, title, width / 2, height / 2 - 76, 0xFFFFFF);
            context.drawString(font, Component.translatable("gui.mca_vie.war_memorial.inscription_hint"), width / 2 - 120, height / 2 - 48, 0xA0A0A0);
            context.drawString(font, Component.translatable("gui.mca_vie.war_memorial.dates_hint"), width / 2 - 120, height / 2 - 8, 0xA0A0A0);
            super.render(context, mouseX, mouseY, delta);
        }
    }

    private static final class ListScreen extends Screen {
        private static final int ROW = 24;
        private final MemorialBlockEntity memorial;
        private double scroll;

        private ListScreen(MemorialBlockEntity memorial) {
            super(Component.literal(inscription(memorial).getString().replace('\n', ' ')));
            this.memorial = memorial;
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        protected void init() {
            addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 - 50, height - 28, 100, 20).build());
        }

        private int visibleRows() {
            return Math.max(1, (height - 80) / ROW);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
            int max = Math.max(0, memorial.fallen().size() - visibleRows());
            scroll = Math.max(0, Math.min(max, scroll - amount));
            return true;
        }

        @Override
        public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
            renderBackground(context);
            context.drawCenteredString(font, title, width / 2, 14, 0xF2CC5A);
            List<MemorialRegistry.Fallen> all = memorial.fallen();
            if (all.isEmpty()) {
                context.drawCenteredString(font, Component.translatable("gui.mca_vie.war_memorial.empty"), width / 2, height / 2, 0xA0A0A0);
            }
            int x = width / 2 - 150;
            int y = 40;
            for (int i = (int) scroll; i < all.size() && i < scroll + visibleRows(); i++) {
                MemorialRegistry.Fallen entry = all.get(all.size() - 1 - i);
                TombstoneDetails details = entry.details();
                context.drawString(font, Component.literal(name(details)).withStyle(ChatFormatting.WHITE), x, y, 0xFFFFFF);
                Component killer = Component.Serializer.fromJson(entry.killer());
                Component info = Component.literal(TombstoneClient.lives(details) + "  ")
                        .append(Component.translatable("gui.mca_vie.war_memorial.killed_by", killer == null ? Component.literal("?") : killer));
                context.drawString(font, info, x + 8, y + 10, 0xA0A0A0);
                y += ROW;
            }
            super.render(context, mouseX, mouseY, delta);
        }
    }
}
