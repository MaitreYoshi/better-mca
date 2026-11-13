package com.maitreyoshi.mcavie.life.client;

import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import com.maitreyoshi.mcavie.compat.client.ClientPlayNetworking;
import com.maitreyoshi.mcavie.life.MemorialBlock;
import com.maitreyoshi.mcavie.life.MemorialBlockEntity;
import com.maitreyoshi.mcavie.life.MemorialRegistry;
import com.maitreyoshi.mcavie.life.TombstoneBlocks;
import com.maitreyoshi.mcavie.life.TombstoneDetails;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import dev.architectury.registry.client.rendering.RenderTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class MemorialClient {
    private static final int NAME_COLOR = 0xF2CC5A;
    private static final int OUTLINE = 0x3E2A0A;
    private static final int ENGRAVED = 0x3A3328;
    private static final int PER_PLAQUE = 10;

    private MemorialClient() {
    }

    public static void register() {
        RenderTypeRegistry.register(RenderLayer.getCutout(), TombstoneBlocks.MEMORIAL);
        BlockEntityRendererRegistry.register(TombstoneBlocks.MEMORIAL_ENTITY, context -> new Renderer());
        ClientPlayNetworking.registerGlobalReceiver(MemorialBlock.EDIT_OPEN, (client, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            String title = buf.readString();
            String dates = buf.readString();
            client.execute(() -> client.setScreen(new EditScreen(pos, title, dates)));
        });
        ClientPlayNetworking.registerGlobalReceiver(MemorialBlock.LIST_OPEN, (client, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            client.execute(() -> {
                if (client.world != null && client.world.getBlockEntity(pos) instanceof MemorialBlockEntity memorial) {
                    client.setScreen(new ListScreen(memorial));
                }
            });
        });
    }

    static String name(TombstoneDetails details) {
        return details.firstName() + (details.lastName().isEmpty() ? "" : " " + details.lastName().toUpperCase(Locale.ROOT));
    }

    static Text inscription(MemorialBlockEntity memorial) {
        if (memorial.title() != null) {
            return Text.literal(memorial.title());
        }
        return memorial.villageName().isEmpty() ? Text.translatable("block.mca_vie.war_memorial.inscription_short")
                : Text.translatable("block.mca_vie.war_memorial.inscription", memorial.villageName());
    }

    private static final class Renderer implements BlockEntityRenderer<MemorialBlockEntity> {
        @Override
        public boolean rendersOutsideBoundingBox(MemorialBlockEntity entity) {
            return true;
        }

        @Override
        public int getRenderDistance() {
            return 96;
        }

        @Override
        public void render(MemorialBlockEntity memorial, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
            World world = memorial.getWorld();
            if (world == null) {
                return;
            }
            Direction facing = memorial.getCachedState().get(MemorialBlock.FACING);
            Direction back = facing.getOpposite();
            Direction left = facing.rotateYCounterclockwise();
            BlockPos pos = memorial.getPos();
            TextRenderer text = MinecraftClient.getInstance().textRenderer;
            matrices.push();
            matrices.multiplyPositionMatrix(new Matrix4f(
                    back.getOffsetX() / 16f, 0, back.getOffsetZ() / 16f, 0,
                    0, 1 / 16f, 0, 0,
                    left.getOffsetX() / 16f, 0, left.getOffsetZ() / 16f, 0,
                    0.5f - 0.5f * back.getOffsetX() - 0.5f * left.getOffsetX(), 0, 0.5f - 0.5f * back.getOffsetZ() - 0.5f * left.getOffsetZ(), 1));

            int frontLight = WorldRenderer.getLightmapCoordinates(world, pos.offset(facing, 2).up(3));
            face(matrices, new Vector3f(1.13f, 84, 2.5f), new Vector3f(0, 0, 1), new Vector3f(1, 0, 0));
            block(inscription(memorial), 110, 104, 1.6f, text, matrices, consumers, frontLight);
            matrices.pop();
            if (memorial.dates() != null) {
                face(matrices, new Vector3f(-3.02f, 48, -3), new Vector3f(0, 0, 1), new Vector3f(1, 0, 0));
                block(Text.literal(memorial.dates()), 220, 56, 4.5f, text, matrices, consumers, frontLight);
                matrices.pop();
            }

            List<MemorialRegistry.Fallen> all = memorial.fallen();
            int start = Math.max(0, Math.min(all.size(), memorial.first()));
            List<MemorialRegistry.Fallen> shown = all.subList(start, Math.min(all.size(), start + 2 * PER_PLAQUE));
            face(matrices, new Vector3f(-3, 36, 22.52f), new Vector3f(1, 0, 0), new Vector3f(0, 0, -1));
            names(shown.subList(0, Math.min(PER_PLAQUE, shown.size())), text, matrices, consumers,
                    WorldRenderer.getLightmapCoordinates(world, pos.offset(left, 2).up()));
            matrices.pop();
            if (shown.size() > PER_PLAQUE) {
                face(matrices, new Vector3f(19, 36, -6.52f), new Vector3f(-1, 0, 0), new Vector3f(0, 0, 1));
                names(shown.subList(PER_PLAQUE, shown.size()), text, matrices, consumers,
                        WorldRenderer.getLightmapCoordinates(world, pos.offset(left.getOpposite(), 2).up()));
                matrices.pop();
            }
            matrices.pop();
        }
    }

    private static void face(MatrixStack matrices, Vector3f origin, Vector3f right, Vector3f inward) {
        matrices.push();
        matrices.multiplyPositionMatrix(new Matrix4f(
                right.x, right.y, right.z, 0,
                0, -1, 0, 0,
                inward.x, inward.y, inward.z, 0,
                origin.x, origin.y, origin.z, 1));
        matrices.scale(0.1f, 0.1f, 0.1f);
    }

    private static void block(Text content, float width, float height, float maxScale, TextRenderer text, MatrixStack matrices,
                              VertexConsumerProvider consumers, int light) {
        float scale = maxScale;
        List<OrderedText> lines = text.wrapLines(content, (int) (width / scale));
        while (scale > 0.4f && lines.size() * 10 * scale > height) {
            scale -= 0.1f;
            lines = text.wrapLines(content, (int) (width / scale));
        }
        float y = (height - lines.size() * 10 * scale) / 2 + 3;
        for (OrderedText line : lines) {
            matrices.push();
            matrices.translate(width / 2, y, 0);
            matrices.scale(scale, scale, scale);
            text.draw(line, -text.getWidth(line) / 2f, 0, ENGRAVED, false, matrices.peek().getPositionMatrix(), consumers,
                    TextRenderer.TextLayerType.NORMAL, 0, light);
            matrices.pop();
            y += 10 * scale;
        }
    }

    private static void names(List<MemorialRegistry.Fallen> list, TextRenderer text, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        float margin = 16;
        float row = (255 - 2 * margin) / PER_PLAQUE;
        for (int i = 0; i < list.size(); i++) {
            TombstoneDetails details = list.get(i).details();
            float top = margin + i * row;
            UUID image = TombstoneClient.portrait(details.person());
            if (image != null) {
                matrices.push();
                matrices.translate(margin + 8, 0, 0);
                TombstoneClient.drawOval(image, matrices, consumers, top + 1.5f, row - 3, light);
                matrices.pop();
            }
            Text line = Text.literal(name(details));
            float scale = Math.min(1.4f, (220 - 2 * margin - 22) / Math.max(1, text.getWidth(line)));
            matrices.push();
            matrices.translate(margin + 20, top + (row - 8 * scale) / 2, 0);
            matrices.scale(scale, scale, scale);
            text.drawWithOutline(line.asOrderedText(), 0, 0, NAME_COLOR, OUTLINE, matrices.peek().getPositionMatrix(), consumers, light);
            matrices.pop();
        }
    }

    private static final class EditScreen extends Screen {
        private final BlockPos pos;
        private final String initialTitle;
        private final String initialDates;
        private TextFieldWidget titleField;
        private TextFieldWidget datesField;

        private EditScreen(BlockPos pos, String title, String dates) {
            super(Text.translatable("gui.mca_vie.war_memorial.edit"));
            this.pos = pos;
            this.initialTitle = title;
            this.initialDates = dates;
        }

        @Override
        public boolean shouldPause() {
            return false;
        }

        @Override
        protected void init() {
            titleField = new TextFieldWidget(textRenderer, width / 2 - 120, height / 2 - 36, 240, 20, Text.translatable("gui.mca_vie.war_memorial.inscription"));
            titleField.setMaxLength(MemorialBlock.TITLE_MAX);
            titleField.setText(initialTitle);
            addDrawableChild(titleField);
            datesField = new TextFieldWidget(textRenderer, width / 2 - 120, height / 2 + 4, 240, 20, Text.translatable("gui.mca_vie.war_memorial.dates"));
            datesField.setMaxLength(MemorialBlock.DATES_MAX);
            datesField.setText(initialDates);
            addDrawableChild(datesField);
            setInitialFocus(titleField);
            addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> send()).dimensions(width / 2 - 104, height / 2 + 34, 100, 20).build());
            addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, b -> close()).dimensions(width / 2 + 4, height / 2 + 34, 100, 20).build());
        }

        private void send() {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBlockPos(pos);
            buf.writeString(titleField.getText());
            buf.writeString(datesField.getText());
            ClientPlayNetworking.send(MemorialBlock.EDIT_SET, buf);
            close();
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            renderBackground(context);
            context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 76, 0xFFFFFF);
            context.drawTextWithShadow(textRenderer, Text.translatable("gui.mca_vie.war_memorial.inscription_hint"), width / 2 - 120, height / 2 - 48, 0xA0A0A0);
            context.drawTextWithShadow(textRenderer, Text.translatable("gui.mca_vie.war_memorial.dates_hint"), width / 2 - 120, height / 2 - 8, 0xA0A0A0);
            super.render(context, mouseX, mouseY, delta);
        }
    }

    private static final class ListScreen extends Screen {
        private static final int ROW = 24;
        private final MemorialBlockEntity memorial;
        private double scroll;

        private ListScreen(MemorialBlockEntity memorial) {
            super(Text.literal(inscription(memorial).getString().replace('\n', ' ')));
            this.memorial = memorial;
        }

        @Override
        public boolean shouldPause() {
            return false;
        }

        @Override
        protected void init() {
            addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close()).dimensions(width / 2 - 50, height - 28, 100, 20).build());
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
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            renderBackground(context);
            context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xF2CC5A);
            List<MemorialRegistry.Fallen> all = memorial.fallen();
            if (all.isEmpty()) {
                context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.mca_vie.war_memorial.empty"), width / 2, height / 2, 0xA0A0A0);
            }
            int x = width / 2 - 150;
            int y = 40;
            for (int i = (int) scroll; i < all.size() && i < scroll + visibleRows(); i++) {
                MemorialRegistry.Fallen entry = all.get(all.size() - 1 - i);
                TombstoneDetails details = entry.details();
                context.drawTextWithShadow(textRenderer, Text.literal(name(details)).formatted(Formatting.WHITE), x, y, 0xFFFFFF);
                Text killer = Text.Serializer.fromJson(entry.killer());
                Text info = Text.literal(TombstoneClient.lives(details) + "  ")
                        .append(Text.translatable("gui.mca_vie.war_memorial.killed_by", killer == null ? Text.literal("?") : killer));
                context.drawTextWithShadow(textRenderer, info, x + 8, y + 10, 0xA0A0A0);
                y += ROW;
            }
            super.render(context, mouseX, mouseY, delta);
        }
    }
}
