package com.maitreyoshi.mcavie.life.client;

import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import com.maitreyoshi.mcavie.compat.client.ClientPlayNetworking;
import com.maitreyoshi.mcavie.life.GhostAltarBlockEntity;
import com.maitreyoshi.mcavie.life.Ghosts;
import com.maitreyoshi.mcavie.life.TombstoneBlocks;
import com.maitreyoshi.mcavie.phone.client.PhonePhotos;
import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import dev.architectury.registry.client.rendering.RenderTypeRegistry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class GhostAltarClient {
    // cadre de la photo dans le modèle (pixels), face avant au nord
    private static final float X0 = 6, X1 = 10, Y0 = 7, Y1 = 12.5f, Z = 14.4f;

    private GhostAltarClient() {
    }

    public static void register() {
        RenderTypeRegistry.register(RenderLayer.getCutout(), TombstoneBlocks.GHOST_ALTAR);
        BlockEntityRendererRegistry.register(TombstoneBlocks.GHOST_ALTAR_ENTITY, context -> new Renderer());
        EntityRendererRegistry.register(() -> TombstoneBlocks.GHOST, GhostRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(Ghosts.OPEN, (client, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            boolean house = buf.readBoolean();
            UUID current = buf.readBoolean() ? buf.readUuid() : null;
            int count = buf.readVarInt();
            List<Entry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                entries.add(new Entry(buf.readUuid(), buf.readString(), buf.readBoolean()));
            }
            client.execute(() -> client.setScreen(new ChooseScreen(pos, house, current, entries)));
        });
    }

    private static final class Renderer implements BlockEntityRenderer<GhostAltarBlockEntity> {
        @Override
        public void render(GhostAltarBlockEntity altar, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
            UUID person = altar.person();
            UUID image = person == null ? null : TombstoneClient.portrait(person);
            PhonePhotos.Picture picture = image == null ? null : PhonePhotos.portrait(image);
            if (picture == null) {
                return;
            }
            Direction facing = altar.getCachedState().get(Properties.HORIZONTAL_FACING);
            matrices.push();
            matrices.translate(0.5, 0, 0.5);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 - facing.asRotation()));
            matrices.translate(-0.5, 0, -0.5);
            float target = (X1 - X0) / (Y1 - Y0);
            float ratio = picture.width() / (float) Math.max(1, picture.height());
            float du = target < ratio ? target / ratio : 1;
            float dv = target < ratio ? 1 : ratio / target;
            float u0 = 0.5f - du / 2, u1 = 0.5f + du / 2, v0 = 0.5f - dv / 2, v1 = 0.5f + dv / 2;
            Matrix4f m = matrices.peek().getPositionMatrix();
            VertexConsumer buffer = consumers.getBuffer(RenderLayer.getText(picture.texture()));
            float z = Z / 16;
            // vu depuis le nord, la gauche de l'image est du côté x+
            vertex(buffer, m, X1 / 16, Y1 / 16, z, u0, v0, light);
            vertex(buffer, m, X1 / 16, Y0 / 16, z, u0, v1, light);
            vertex(buffer, m, X0 / 16, Y0 / 16, z, u1, v1, light);
            vertex(buffer, m, X0 / 16, Y1 / 16, z, u1, v0, light);
            matrices.pop();
        }

        private static void vertex(VertexConsumer buffer, Matrix4f m, float x, float y, float z, float u, float v, int light) {
            buffer.vertex(m, x, y, z).color(235, 225, 205, 255).texture(u, v).light(light).next();
        }
    }

    private record Entry(UUID id, String name, boolean look) {
    }

    private static final class ChooseScreen extends Screen {
        private static final int ROW_H = 14;
        private static final int ROW_W = 220;
        private static final int TOP = 34;
        private static final int BOTTOM = 34;

        private final BlockPos pos;
        private final boolean house;
        @Nullable
        private final UUID current;
        private final List<Entry> entries;
        private double scroll;

        private ChooseScreen(BlockPos pos, boolean house, @Nullable UUID current, List<Entry> entries) {
            super(Text.translatable("gui.mca_vie.ghost_altar.title"));
            this.pos = pos;
            this.house = house;
            this.current = current;
            this.entries = entries;
        }

        @Override
        public boolean shouldPause() {
            return false;
        }

        @Override
        protected void init() {
            int y = height - BOTTOM + 7;
            if (current != null) {
                addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mca_vie.ghost_altar.clear"), b -> send(null))
                        .dimensions(width / 2 - 104, y, 100, 20).build());
                addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, b -> close()).dimensions(width / 2 + 4, y, 100, 20).build());
            } else {
                addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, b -> close()).dimensions(width / 2 - 50, y, 100, 20).build());
            }
        }

        private void send(@Nullable UUID person) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBlockPos(pos);
            buf.writeBoolean(person != null);
            if (person != null) {
                buf.writeUuid(person);
            }
            ClientPlayNetworking.send(Ghosts.CHOOSE, buf);
            close();
        }

        private int left() {
            return (width - ROW_W) / 2;
        }

        private int indexAt(double mouseX, double mouseY) {
            if (mouseY < TOP || mouseY >= height - BOTTOM || mouseX < left() || mouseX >= left() + ROW_W) {
                return -1;
            }
            int index = (int) Math.floor((mouseY - TOP + scroll) / ROW_H);
            return index >= 0 && index < entries.size() ? index : -1;
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            renderBackground(context);
            context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFF);
            Text hint = Text.translatable(!house ? "gui.mca_vie.ghost_altar.no_house" : entries.isEmpty() ? "gui.mca_vie.ghost_altar.empty" : "gui.mca_vie.ghost_altar.hint");
            context.drawCenteredTextWithShadow(textRenderer, hint, width / 2, 21, 0xA0A0A0);
            int hovered = indexAt(mouseX, mouseY);
            context.enableScissor(0, TOP, width, height - BOTTOM);
            for (int i = 0; i < entries.size(); i++) {
                int y = TOP + i * ROW_H - (int) scroll;
                if (y + ROW_H < TOP || y > height - BOTTOM) {
                    continue;
                }
                Entry entry = entries.get(i);
                boolean selected = entry.id().equals(current);
                if ((i == hovered && entry.look()) || selected) {
                    context.fill(left(), y, left() + ROW_W, y + ROW_H, selected ? 0x6050A0C0 : 0x40FFFFFF);
                }
                int color = selected ? 0xB0E0FF : entry.look() ? 0xFFFFFF : 0x808080;
                context.drawTextWithShadow(textRenderer, entry.name(), left() + 4, y + 3, color);
                if (!entry.look()) {
                    Text tag = Text.translatable("gui.mca_vie.ghost_altar.no_look");
                    context.drawTextWithShadow(textRenderer, tag, left() + ROW_W - 4 - textRenderer.getWidth(tag), y + 3, 0x808080);
                }
            }
            context.disableScissor();
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (super.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            int index = button == 0 ? indexAt(mouseX, mouseY) : -1;
            if (index < 0 || !entries.get(index).look()) {
                return false;
            }
            send(entries.get(index).id());
            return true;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
            int max = Math.max(0, entries.size() * ROW_H - (height - TOP - BOTTOM));
            scroll = Math.max(0, Math.min(max, scroll - amount * ROW_H * 2));
            return true;
        }
    }
}
