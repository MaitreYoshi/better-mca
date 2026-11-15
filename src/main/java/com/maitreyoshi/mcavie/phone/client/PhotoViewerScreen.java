package com.maitreyoshi.mcavie.phone.client;

import com.maitreyoshi.mcavie.phone.PhoneNetwork;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PhotoViewerScreen extends Screen {
    private static final int BAR = 32;

    private record Action(String key, Runnable run) {
    }

    @Nullable
    private final PhoneScreen parent;
    private final int number;
    private final UUID id;
    private final boolean canSend;
    private final boolean canDelete;
    private final boolean canSave;

    public PhotoViewerScreen(@Nullable PhoneScreen parent, int number, UUID id, boolean canSend, boolean canDelete, boolean canSave) {
        super(Text.translatable("gui.mca_vie.phone.photo"));
        this.parent = parent;
        this.number = number;
        this.id = id;
        this.canSend = canSend;
        this.canDelete = canDelete;
        this.canSave = canSave;
    }

    public static PhotoViewerScreen print(UUID id) {
        return new PhotoViewerScreen(null, -1, id, false, false, false);
    }

    @Override
    protected void init() {
        List<Action> actions = new ArrayList<>();
        actions.add(new Action("gui.mca_vie.phone.back", this::close));
        if (parent != null) {
            actions.add(new Action("gui.mca_vie.phone.print", () -> PhoneClient.send(number, PhoneNetwork.OP_PRINT_PHOTO, buf -> buf.writeUuid(id))));
            if (canSave) {
                actions.add(new Action("gui.mca_vie.phone.save_photo", () -> {
                    PhoneClient.send(number, PhoneNetwork.OP_SAVE_PHOTO, buf -> buf.writeUuid(id));
                    close();
                }));
            }
            if (canSend) {
                actions.add(new Action("gui.mca_vie.phone.send_photo", () -> {
                    parent.pickContact(id);
                    close();
                }));
            }
            if (canDelete && parent.state().owner()) {
                actions.add(new Action("gui.mca_vie.phone.set_wallpaper", () -> {
                    parent.startCrop(id);
                    close();
                }));
            }
            if (canDelete) {
                actions.add(new Action("gui.mca_vie.phone.delete_photo", () -> {
                    parent.deletePhoto(id);
                    close();
                }));
            }
        }
        int w = Math.min(100, (width - 16) / actions.size() - 6);
        int gap = 6;
        int x = (width - actions.size() * w - (actions.size() - 1) * gap) / 2;
        int y = height - BAR + 6;
        for (Action action : actions) {
            addDrawableChild(ButtonWidget.builder(Text.translatable(action.key()), b -> action.run().run()).dimensions(x, y, w, 20).build());
            x += w + gap;
        }
    }

    @Override
    public void close() {
        if (client == null) {
            return;
        }
        client.setScreen(parent);
        if (parent != null) {
            parent.refresh();
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xF0000000);
        PhonePhotos.Picture picture = parent == null ? PhonePhotos.print(id, true) : PhonePhotos.get(number, id, true);
        int margin = 8;
        if (picture == null) {
            context.drawCenteredTextWithShadow(textRenderer, "…", width / 2, (height - BAR) / 2, 0xAAAAAA);
        } else {
            PhoneScreen.drawFitted(context, picture, margin, margin, width - 2 * margin, height - BAR - 2 * margin);
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
