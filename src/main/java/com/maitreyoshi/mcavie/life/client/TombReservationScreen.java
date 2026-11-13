package com.maitreyoshi.mcavie.life.client;

import net.minecraft.client.input.MouseButtonEvent;
import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import com.maitreyoshi.mcavie.compat.client.ClientPlayNetworking;
import com.maitreyoshi.mcavie.life.TombReservations;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class TombReservationScreen extends Screen {
    private static final int ROW_H = 14;
    private static final int ROW_W = 200;
    private static final int TOP = 34;
    private static final int BOTTOM = 34;

    private record Entry(UUID id, String name, boolean hasTomb) {
    }

    private final BlockPos pos;
    @Nullable
    private final UUID current;
    private final List<Entry> entries;
    private double scroll;

    private TombReservationScreen(BlockPos pos, String village, @Nullable UUID current, List<Entry> entries) {
        super(Component.translatable("gui.mca_vie.tomb.title", village));
        this.pos = pos;
        this.current = current;
        this.entries = entries;
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(TombReservations.OPEN, (client, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            String village = buf.readUtf();
            UUID current = buf.readBoolean() ? buf.readUUID() : null;
            int count = buf.readVarInt();
            List<Entry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                entries.add(new Entry(buf.readUUID(), buf.readUtf(), buf.readBoolean()));
            }
            client.execute(() -> client.gui.setScreen(new TombReservationScreen(pos, village, current, entries)));
        });
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int y = height - BOTTOM + 7;
        if (current != null) {
            addRenderableWidget(Button.builder(Component.translatable("gui.mca_vie.tomb.clear"), b -> send(null))
                    .bounds(width / 2 - 104, y, 100, 20).build());
            addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                    .bounds(width / 2 + 4, y, 100, 20).build());
        } else {
            addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                    .bounds(width / 2 - 50, y, 100, 20).build());
        }
    }

    private void send(@Nullable UUID villager) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeBoolean(villager != null);
        if (villager != null) {
            buf.writeUUID(villager);
        }
        ClientPlayNetworking.send(TombReservations.CHOOSE, buf);
        onClose();
    }

    private int left() {
        return (width - ROW_W) / 2;
    }

    private int maxScroll() {
        return Math.max(0, entries.size() * ROW_H - (height - TOP - BOTTOM));
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
        extractBackground(context, mouseX, mouseY, delta);
        context.centeredText(font, title, width / 2, 10, 0xFFFFFF);
        Component hint = entries.isEmpty() ? Component.translatable("gui.mca_vie.tomb.empty") : Component.translatable("gui.mca_vie.tomb.hint");
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
            if (i == hovered || selected) {
                context.fill(left(), y, left() + ROW_W, y + ROW_H, selected ? 0x6050A050 : 0x40FFFFFF);
            }
            int color = selected ? 0x80FF80 : entry.hasTomb() ? 0x909090 : 0xFFFFFF;
            context.text(font, entry.name(), left() + 4, y + 3, color);
            if (entry.hasTomb() && !selected) {
                Component tag = Component.translatable("gui.mca_vie.tomb.has_one");
                context.text(font, tag, left() + ROW_W - 4 - font.width(tag), y + 3, 0x909090);
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
        if (index < 0) {
            return false;
        }
        send(entries.get(index).id());
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double amount) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - amount * ROW_H * 2));
        return true;
    }
}
