package com.maitreyoshi.mcavie.phone.client;

import com.maitreyoshi.mcavie.phone.PhoneNetwork;
import com.maitreyoshi.mcavie.tree.client.TreeScreen;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class PortraitPickerScreen extends Screen {
    private static final int CELL_W = 128;
    private static final int CELL_H = CELL_W * PhoneNetwork.THUMB_HEIGHT / PhoneNetwork.THUMB_WIDTH;
    private static final int GAP = 6;
    private static final int TOP = 30;
    private static final int BOTTOM = 32;

    private final TreeScreen parent;
    private final int number;
    private final List<PhoneClient.Photo> gallery;
    private final UUID person;
    private double scroll;

    public PortraitPickerScreen(TreeScreen parent, PhoneClient.State state, UUID person, String name) {
        super(Component.translatable("gui.mca_vie.portrait.title", name));
        this.parent = parent;
        this.number = state.number();
        this.gallery = state.gallery();
        this.person = person;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        assert minecraft != null;
        minecraft.setScreen(parent);
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(width / 2 - 50, height - BOTTOM + 7, 100, 20).build());
    }

    private int columns() {
        return Math.max(1, (width - 20 + GAP) / (CELL_W + GAP));
    }

    private int left() {
        int columns = columns();
        return (width - columns * CELL_W - (columns - 1) * GAP) / 2;
    }

    private int maxScroll() {
        int rows = (gallery.size() + columns() - 1) / columns();
        return Math.max(0, rows * (CELL_H + GAP) - GAP - (height - TOP - BOTTOM));
    }

    private int indexAt(double mouseX, double mouseY) {
        if (mouseY < TOP || mouseY >= height - BOTTOM) {
            return -1;
        }
        int column = (int) Math.floor((mouseX - left()) / (CELL_W + GAP));
        int row = (int) Math.floor((mouseY - TOP + scroll) / (CELL_H + GAP));
        if (column < 0 || column >= columns() || row < 0) {
            return -1;
        }
        double inX = mouseX - left() - column * (CELL_W + GAP);
        double inY = mouseY - TOP + scroll - row * (CELL_H + GAP);
        int index = row * columns() + column;
        return inX < CELL_W && inY < CELL_H && index < gallery.size() ? index : -1;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        if (gallery.isEmpty()) {
            context.drawCenteredString(font, Component.translatable("gui.mca_vie.phone.no_photos"), width / 2, height / 2 - 4, 0xA0A0A0);
        } else {
            context.drawCenteredString(font, Component.translatable("gui.mca_vie.portrait.hint"), width / 2, 20, 0xA0A0A0);
        }
        int columns = columns();
        int left = left();
        int hovered = indexAt(mouseX, mouseY);
        int side = (CELL_W - CELL_H) / 2;
        context.enableScissor(0, TOP, width, height - BOTTOM);
        for (int i = 0; i < gallery.size(); i++) {
            int x = left + (i % columns) * (CELL_W + GAP);
            int y = TOP + (i / columns) * (CELL_H + GAP) - (int) scroll;
            if (y + CELL_H < TOP || y > height - BOTTOM) {
                continue;
            }
            PhonePhotos.Picture picture = PhonePhotos.get(number, gallery.get(i).id(), false);
            if (picture == null) {
                context.fill(x, y, x + CELL_W, y + CELL_H, 0xFF202030);
            } else {
                context.blit(picture.texture(), x, y, CELL_W, CELL_H, 0, 0, picture.width(), picture.height(), picture.width(), picture.height());
            }
            context.fill(x, y, x + side, y + CELL_H, 0x90000000);
            context.fill(x + CELL_W - side, y, x + CELL_W, y + CELL_H, 0x90000000);
            context.renderOutline(x + side, y, CELL_H, CELL_H, i == hovered ? 0xFFFFFF55 : 0x80FFFFFF);
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
        UUID photo = gallery.get(index).id();
        PhoneClient.send(number, PhoneNetwork.OP_SET_PORTRAIT, buf -> {
            buf.writeUUID(person);
            buf.writeUUID(photo);
        });
        onClose();
        parent.refreshPortraits();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - amount * 20));
        return true;
    }
}
