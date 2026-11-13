package com.maitreyoshi.mcavie.life.client;

import com.maitreyoshi.mcavie.compat.client.SimpleBlockEntityRenderer;
import com.maitreyoshi.mcavie.compat.client.Buffers;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.input.MouseButtonEvent;
import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import com.maitreyoshi.mcavie.compat.client.ClientPlayNetworking;
import com.maitreyoshi.mcavie.life.GhostAltarBlockEntity;
import com.maitreyoshi.mcavie.life.Ghosts;
import com.maitreyoshi.mcavie.life.TombstoneBlocks;
import com.maitreyoshi.mcavie.phone.client.PhonePhotos;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class GhostAltarClient {
    // cadre de la photo dans le modèle (pixels), face avant au nord
    private static final float X0 = 6, X1 = 10, Y0 = 7, Y1 = 12.5f, Z = 14.4f;

    private GhostAltarClient() {
    }

    public static void register() {
        BlockEntityRendererRegistry.register(TombstoneBlocks.GHOST_ALTAR_ENTITY, context -> new Renderer());
        EntityRendererRegistry.register(() -> TombstoneBlocks.GHOST, GhostRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(Ghosts.OPEN, (client, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            boolean house = buf.readBoolean();
            UUID current = buf.readBoolean() ? buf.readUUID() : null;
            int count = buf.readVarInt();
            List<Entry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                entries.add(new Entry(buf.readUUID(), buf.readUtf(), buf.readBoolean()));
            }
            client.execute(() -> client.gui.setScreen(new ChooseScreen(pos, house, current, entries)));
        });
    }

    private static final class Renderer extends SimpleBlockEntityRenderer<GhostAltarBlockEntity> {
        @Override
        protected void render(GhostAltarBlockEntity altar, float tickDelta, PoseStack matrices, Buffers consumers, int light, int overlay) {
            UUID person = altar.person();
            UUID image = person == null ? null : TombstoneClient.portrait(person);
            PhonePhotos.Picture picture = image == null ? null : PhonePhotos.portrait(image);
            if (picture == null) {
                return;
            }
            Direction facing = altar.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
            matrices.pushPose();
            matrices.translate(0.5, 0, 0.5);
            matrices.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
            matrices.translate(-0.5, 0, -0.5);
            float target = (X1 - X0) / (Y1 - Y0);
            float ratio = picture.width() / (float) Math.max(1, picture.height());
            float du = target < ratio ? target / ratio : 1;
            float dv = target < ratio ? 1 : ratio / target;
            float u0 = 0.5f - du / 2, u1 = 0.5f + du / 2, v0 = 0.5f - dv / 2, v1 = 0.5f + dv / 2;
            float z = Z / 16;
            // vu depuis le nord, la gauche de l'image est du côté x+
            consumers.draw(RenderTypes.text(picture.texture()), (buffer, m) -> {
                vertex(buffer, m, X1 / 16, Y1 / 16, z, u0, v0, light);
                vertex(buffer, m, X1 / 16, Y0 / 16, z, u0, v1, light);
                vertex(buffer, m, X0 / 16, Y0 / 16, z, u1, v1, light);
                vertex(buffer, m, X0 / 16, Y1 / 16, z, u1, v0, light);
            });
            matrices.popPose();
        }

        private static void vertex(VertexConsumer buffer, Matrix4f m, float x, float y, float z, float u, float v, int light) {
            buffer.addVertex(m, x, y, z).setColor(235, 225, 205, 255).setUv(u, v).setLight(light);
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
            super(Component.translatable("gui.mca_vie.ghost_altar.title"));
            this.pos = pos;
            this.house = house;
            this.current = current;
            this.entries = entries;
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        protected void init() {
            int y = height - BOTTOM + 7;
            if (current != null) {
                addRenderableWidget(Button.builder(Component.translatable("gui.mca_vie.ghost_altar.clear"), b -> send(null))
                        .bounds(width / 2 - 104, y, 100, 20).build());
                addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(width / 2 + 4, y, 100, 20).build());
            } else {
                addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(width / 2 - 50, y, 100, 20).build());
            }
        }

        private void send(@Nullable UUID person) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeBlockPos(pos);
            buf.writeBoolean(person != null);
            if (person != null) {
                buf.writeUUID(person);
            }
            ClientPlayNetworking.send(Ghosts.CHOOSE, buf);
            onClose();
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
        public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            context.centeredText(font, title, width / 2, 10, 0xFFFFFF);
            Component hint = Component.translatable(!house ? "gui.mca_vie.ghost_altar.no_house" : entries.isEmpty() ? "gui.mca_vie.ghost_altar.empty" : "gui.mca_vie.ghost_altar.hint");
            context.centeredText(font, hint, width / 2, 21, 0xA0A0A0);
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
                context.text(font, entry.name(), left() + 4, y + 3, color);
                if (!entry.look()) {
                    Component tag = Component.translatable("gui.mca_vie.ghost_altar.no_look");
                    context.text(font, tag, left() + ROW_W - 4 - font.width(tag), y + 3, 0x808080);
                }
            }
            context.disableScissor();
            super.extractRenderState(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
            if (super.mouseClicked(event, doubleClick)) {
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
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double amount) {
            int max = Math.max(0, entries.size() * ROW_H - (height - TOP - BOTTOM));
            scroll = Math.max(0, Math.min(max, scroll - amount * ROW_H * 2));
            return true;
        }
    }
}
