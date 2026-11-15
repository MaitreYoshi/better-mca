package com.maitreyoshi.mcavie.life.client;

import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import com.maitreyoshi.mcavie.compat.client.ClientPlayNetworking;
import com.maitreyoshi.mcavie.life.TombReservations;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
        super(Text.translatable("gui.mca_vie.tomb.title", village));
        this.pos = pos;
        this.current = current;
        this.entries = entries;
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(TombReservations.OPEN, (client, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            String village = buf.readString();
            UUID current = buf.readBoolean() ? buf.readUuid() : null;
            int count = buf.readVarInt();
            List<Entry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                entries.add(new Entry(buf.readUuid(), buf.readString(), buf.readBoolean()));
            }
            client.execute(() -> client.setScreen(new TombReservationScreen(pos, village, current, entries)));
        });
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    protected void init() {
        int y = height - BOTTOM + 7;
        if (current != null) {
            addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mca_vie.tomb.clear"), b -> send(null))
                    .dimensions(width / 2 - 104, y, 100, 20).build());
            addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, b -> close())
                    .dimensions(width / 2 + 4, y, 100, 20).build());
        } else {
            addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, b -> close())
                    .dimensions(width / 2 - 50, y, 100, 20).build());
        }
    }

    private void send(@Nullable UUID villager) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeBoolean(villager != null);
        if (villager != null) {
            buf.writeUuid(villager);
        }
        ClientPlayNetworking.send(TombReservations.CHOOSE, buf);
        close();
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFF);
        Text hint = entries.isEmpty() ? Text.translatable("gui.mca_vie.tomb.empty") : Text.translatable("gui.mca_vie.tomb.hint");
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
            if (i == hovered || selected) {
                context.fill(left(), y, left() + ROW_W, y + ROW_H, selected ? 0x6050A050 : 0x40FFFFFF);
            }
            int color = selected ? 0x80FF80 : entry.hasTomb() ? 0x909090 : 0xFFFFFF;
            context.drawTextWithShadow(textRenderer, entry.name(), left() + 4, y + 3, color);
            if (entry.hasTomb() && !selected) {
                Text tag = Text.translatable("gui.mca_vie.tomb.has_one");
                context.drawTextWithShadow(textRenderer, tag, left() + ROW_W - 4 - textRenderer.getWidth(tag), y + 3, 0x909090);
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
        if (index < 0) {
            return false;
        }
        send(entries.get(index).id());
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - amount * ROW_H * 2));
        return true;
    }
}
