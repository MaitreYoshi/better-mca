package com.maitreyoshi.mcavie.phone.client;

import com.maitreyoshi.mcavie.phone.PhoneNetwork;
import com.maitreyoshi.mcavie.phone.PhotoFrameEntity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

public class PhotoSizeScreen extends Screen {
    private static final int CELL = 18;
    private static int lastSize = 2;

    private final UUID photo;
    private final BlockPos attach;
    private final Direction facing;
    private final Direction top;
    @Nullable
    private final BlockPos clicked;
    private final Hand hand;
    @Nullable
    private final PhotoFrameEntity frame;
    private int size;
    private boolean fits;
    private ButtonWidget confirm;

    private PhotoSizeScreen(UUID photo, BlockPos attach, Direction facing, Direction top, @Nullable BlockPos clicked, Hand hand,
                            @Nullable PhotoFrameEntity frame, int size) {
        super(Text.translatable("gui.mca_vie.photo_size.title"));
        this.photo = photo;
        this.attach = attach;
        this.facing = facing;
        this.top = top;
        this.clicked = clicked;
        this.hand = hand;
        this.frame = frame;
        this.size = PhotoFrameEntity.clamp(size);
    }

    public static PhotoSizeScreen place(BlockPos clicked, Direction side, Hand hand, UUID photo, Direction top) {
        return new PhotoSizeScreen(photo, clicked.offset(side), side, top, clicked, hand, null, lastSize);
    }

    public static PhotoSizeScreen resize(PhotoFrameEntity frame) {
        UUID photo = frame.photo();
        return new PhotoSizeScreen(photo == null ? new UUID(0, 0) : photo, frame.getDecorationBlockPos(), frame.facing(), frame.top(),
                null, Hand.MAIN_HAND, frame, frame.size());
    }

    @Override
    protected void init() {
        if (frame == null && client != null && client.world != null && !check(size)) {
            for (int s = size - 1; s >= PhotoFrameEntity.MIN_SIZE; s--) {
                if (check(s)) {
                    size = s;
                    break;
                }
            }
        }
        int cx = width / 2;
        int y = height - 54;
        addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> setSize(size - 1)).dimensions(cx - 80, y, 20, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> setSize(size + 1)).dimensions(cx + 60, y, 20, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mca_vie.phone.back"), b -> close()).dimensions(cx - 104, y + 26, 100, 20).build());
        confirm = addDrawableChild(ButtonWidget.builder(Text.translatable(frame == null ? "gui.mca_vie.photo_size.place" : "gui.mca_vie.photo_size.apply"),
                b -> confirm()).dimensions(cx + 4, y + 26, 100, 20).build());
        refresh();
    }

    private boolean check(int s) {
        return client != null && client.world != null && PhotoFrameEntity.fits(client.world, attach, facing, top, s, frame);
    }

    private void setSize(int next) {
        size = PhotoFrameEntity.clamp(next);
        refresh();
    }

    private void refresh() {
        fits = check(size);
        if (confirm != null) {
            confirm.active = fits;
        }
    }

    private void confirm() {
        if (!fits) {
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        if (frame == null && clicked != null) {
            lastSize = size;
            buf.writeBlockPos(clicked);
            buf.writeEnumConstant(facing);
            buf.writeEnumConstant(hand);
            buf.writeVarInt(size);
            ClientPlayNetworking.send(PhoneNetwork.PHOTO_PLACE, buf);
        } else if (frame != null) {
            buf.writeVarInt(frame.getId());
            buf.writeVarInt(size);
            ClientPlayNetworking.send(PhoneNetwork.PHOTO_RESIZE, buf);
        }
        close();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        setSize(size + (amount > 0 ? 1 : -1));
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        int cx = width / 2;
        context.drawCenteredTextWithShadow(textRenderer, title, cx, 12, 0xFFFFFF);
        int areaTop = 28;
        int areaBottom = height - 72;
        int maxW = Math.min(width - 32, PhotoFrameEntity.MAX_SIZE * CELL);
        float scale = Math.min(1f, Math.min(maxW / (float) (PhotoFrameEntity.MAX_SIZE * CELL),
                (areaBottom - areaTop) / (PhotoFrameEntity.MAX_SIZE * CELL * 9f / 16f)));
        int pw = Math.max(8, Math.round(size * CELL * scale));
        int ph = Math.max(5, Math.round(pw * 9f / 16f));
        int px = cx - pw / 2;
        int py = (areaTop + areaBottom) / 2 - ph / 2;
        context.fill(px - 2, py - 2, px + pw + 2, py + ph + 2, fits ? 0xFFEEEEEE : 0xFFCC3333);
        PhonePhotos.Picture picture = PhonePhotos.print(photo, true);
        if (picture == null) {
            context.fill(px, py, px + pw, py + ph, 0xFF555555);
        } else {
            PhoneScreen.drawFitted(context, picture, px, py, pw, ph);
        }
        String blocks = String.format(Locale.ROOT, "%d × %s", size, trim(Math.max(9, Math.round(size * 9f)) / 16f));
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.mca_vie.photo_size.size", blocks), cx, height - 48, 0xFFFFFF);
        if (!fits) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("msg.mca_vie.photo.no_room"), cx, height - 66, 0xFF5555);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private static String trim(float value) {
        String text = String.format(Locale.ROOT, "%.2f", value);
        return text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
