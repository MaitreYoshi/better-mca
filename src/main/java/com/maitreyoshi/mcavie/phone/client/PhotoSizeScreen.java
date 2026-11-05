package com.maitreyoshi.mcavie.phone.client;

import com.maitreyoshi.mcavie.phone.PhoneNetwork;
import com.maitreyoshi.mcavie.phone.PhotoFrameEntity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class PhotoSizeScreen extends Screen {
    private static final int BIG_STEP = 4;
    private static int lastWidth = 4;
    private static int lastHeight = 2;

    private final UUID photo;
    private final BlockPos attach;
    private final Direction facing;
    @Nullable
    private final BlockPos clicked;
    private final Hand hand;
    @Nullable
    private final PhotoFrameEntity frame;
    private int widthHalves;
    private int heightHalves;
    private boolean fits;
    private ButtonWidget confirm;

    private PhotoSizeScreen(UUID photo, BlockPos attach, Direction facing, @Nullable BlockPos clicked, Hand hand,
                            @Nullable PhotoFrameEntity frame, int widthHalves, int heightHalves) {
        super(Text.translatable("gui.mca_vie.photo_size.title"));
        this.photo = photo;
        this.attach = attach;
        this.facing = facing;
        this.clicked = clicked;
        this.hand = hand;
        this.frame = frame;
        this.widthHalves = PhotoFrameEntity.clamp(widthHalves);
        this.heightHalves = PhotoFrameEntity.clamp(heightHalves);
    }

    public static PhotoSizeScreen place(BlockPos clicked, Direction side, Hand hand, UUID photo, Direction top) {
        return new PhotoSizeScreen(photo, clicked.offset(side), side, clicked, hand, null, lastWidth, lastHeight);
    }

    public static PhotoSizeScreen resize(PhotoFrameEntity frame) {
        UUID photo = frame.photo();
        return new PhotoSizeScreen(photo == null ? new UUID(0, 0) : photo, frame.getDecorationBlockPos(), frame.facing(),
                null, Hand.MAIN_HAND, frame, frame.widthHalves(), frame.heightHalves());
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int y = height - 102;
        addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> setWidth(widthHalves - step())).dimensions(cx - 90, y, 20, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> setWidth(widthHalves + step())).dimensions(cx + 70, y, 20, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> setHeight(heightHalves - step())).dimensions(cx - 90, y + 24, 20, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> setHeight(heightHalves + step())).dimensions(cx + 70, y + 24, 20, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mca_vie.photo_size.ratio"), b -> matchPhoto()).dimensions(cx - 75, y + 50, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mca_vie.phone.back"), b -> close()).dimensions(cx - 104, y + 76, 100, 20).build());
        confirm = addDrawableChild(ButtonWidget.builder(Text.translatable(frame == null ? "gui.mca_vie.photo_size.place" : "gui.mca_vie.photo_size.apply"),
                b -> confirm()).dimensions(cx + 4, y + 76, 100, 20).build());
        fits = client != null && client.world != null && PhotoFrameEntity.fits(client.world, attach, facing);
        confirm.active = fits;
    }

    private static int step() {
        return hasShiftDown() ? BIG_STEP : 1;
    }

    private void setWidth(int next) {
        widthHalves = PhotoFrameEntity.clamp(next);
    }

    private void setHeight(int next) {
        heightHalves = PhotoFrameEntity.clamp(next);
    }

    private void matchPhoto() {
        PhonePhotos.Picture picture = PhonePhotos.print(photo, true);
        float ratio = picture == null || picture.width() <= 0 ? 9f / 16f : picture.height() / (float) picture.width();
        setHeight(Math.round(widthHalves * ratio));
    }

    private void confirm() {
        if (!fits) {
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        if (frame == null && clicked != null) {
            lastWidth = widthHalves;
            lastHeight = heightHalves;
            buf.writeBlockPos(clicked);
            buf.writeEnumConstant(facing);
            buf.writeEnumConstant(hand);
            buf.writeVarInt(widthHalves);
            buf.writeVarInt(heightHalves);
            ClientPlayNetworking.send(PhoneNetwork.PHOTO_PLACE, buf);
        } else if (frame != null) {
            buf.writeVarInt(frame.getId());
            buf.writeVarInt(widthHalves);
            buf.writeVarInt(heightHalves);
            ClientPlayNetworking.send(PhoneNetwork.PHOTO_RESIZE, buf);
        }
        close();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int delta = amount > 0 ? 1 : -1;
        if (hasShiftDown()) {
            setHeight(heightHalves + delta);
        } else {
            setWidth(widthHalves + delta);
        }
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
        context.drawCenteredTextWithShadow(textRenderer, title, cx, 10, 0xFFFFFF);
        int areaTop = 26;
        int areaBottom = height - 110;
        int areaW = width - 32;
        float scale = Math.min(10f, Math.min(areaW / (float) widthHalves, (areaBottom - areaTop) / (float) heightHalves));
        int pw = Math.max(2, Math.round(widthHalves * scale));
        int ph = Math.max(2, Math.round(heightHalves * scale));
        int px = cx - pw / 2;
        int py = (areaTop + areaBottom) / 2 - ph / 2;
        context.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFFEEEEEE);
        PhonePhotos.Picture picture = PhonePhotos.print(photo, true);
        if (picture == null) {
            context.fill(px, py, px + pw, py + ph, 0xFF555555);
        } else {
            float[] uv = PhotoItemRenderer.cover(picture, widthHalves / (float) heightHalves);
            int tw = picture.width();
            int th = picture.height();
            context.drawTexture(picture.texture(), px, py, pw, ph, uv[0] * tw, uv[1] * th,
                    Math.max(1, Math.round((uv[2] - uv[0]) * tw)), Math.max(1, Math.round((uv[3] - uv[1]) * th)), tw, th);
        }
        int y = height - 102;
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.mca_vie.photo_size.width", blocks(widthHalves)), cx, y + 6, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.mca_vie.photo_size.height", blocks(heightHalves)), cx, y + 30, 0xFFFFFF);
        if (!fits) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("msg.mca_vie.photo.no_support"), cx, areaBottom + 2, 0xFF5555);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private static String blocks(int halves) {
        String half = MinecraftClient.getInstance().getLanguageManager().getLanguage().startsWith("fr") ? ",5" : ".5";
        return halves % 2 == 0 ? String.valueOf(halves / 2) : (halves / 2) + half;
    }
}
