package com.maitreyoshi.mcavie.phone.client;

import com.maitreyoshi.mcavie.phone.PhoneNetwork;
import com.maitreyoshi.mcavie.phone.PhotoFrameEntity;
import com.maitreyoshi.mcavie.compat.client.ClientPlayNetworking;
import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

public class PhotoSizeScreen extends Screen {
    private static final int BIG_STEP = 4;
    private static int lastWidth = 4;
    private static int lastHeight = 2;

    private final UUID photo;
    private final BlockPos attach;
    private final Direction facing;
    @Nullable
    private final BlockPos clicked;
    private final InteractionHand hand;
    @Nullable
    private final PhotoFrameEntity frame;
    private int widthHalves;
    private int heightHalves;
    private boolean fits;
    private Button confirm;

    private PhotoSizeScreen(UUID photo, BlockPos attach, Direction facing, @Nullable BlockPos clicked, InteractionHand hand,
                            @Nullable PhotoFrameEntity frame, int widthHalves, int heightHalves) {
        super(Component.translatable("gui.mca_vie.photo_size.title"));
        this.photo = photo;
        this.attach = attach;
        this.facing = facing;
        this.clicked = clicked;
        this.hand = hand;
        this.frame = frame;
        this.widthHalves = PhotoFrameEntity.clamp(widthHalves);
        this.heightHalves = PhotoFrameEntity.clamp(heightHalves);
    }

    public static PhotoSizeScreen place(BlockPos clicked, Direction side, InteractionHand hand, UUID photo, Direction top) {
        return new PhotoSizeScreen(photo, clicked.relative(side), side, clicked, hand, null, lastWidth, lastHeight);
    }

    public static PhotoSizeScreen resize(PhotoFrameEntity frame) {
        UUID photo = frame.photo();
        return new PhotoSizeScreen(photo == null ? new UUID(0, 0) : photo, frame.getPos(), frame.facing(),
                null, InteractionHand.MAIN_HAND, frame, frame.widthHalves(), frame.heightHalves());
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int y = height - 102;
        addRenderableWidget(Button.builder(Component.literal("-"), b -> setWidth(widthHalves - step())).bounds(cx - 90, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> setWidth(widthHalves + step())).bounds(cx + 70, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-"), b -> setHeight(heightHalves - step())).bounds(cx - 90, y + 24, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> setHeight(heightHalves + step())).bounds(cx + 70, y + 24, 20, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.mca_vie.photo_size.ratio"), b -> matchPhoto()).bounds(cx - 75, y + 50, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.mca_vie.phone.back"), b -> onClose()).bounds(cx - 104, y + 76, 100, 20).build());
        confirm = addRenderableWidget(Button.builder(Component.translatable(frame == null ? "gui.mca_vie.photo_size.place" : "gui.mca_vie.photo_size.apply"),
                b -> confirm()).bounds(cx + 4, y + 76, 100, 20).build());
        fits = minecraft != null && minecraft.level != null && PhotoFrameEntity.fits(minecraft.level, attach, facing);
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
        FriendlyByteBuf buf = PacketByteBufs.create();
        if (frame == null && clicked != null) {
            lastWidth = widthHalves;
            lastHeight = heightHalves;
            buf.writeBlockPos(clicked);
            buf.writeEnum(facing);
            buf.writeEnum(hand);
            buf.writeVarInt(widthHalves);
            buf.writeVarInt(heightHalves);
            ClientPlayNetworking.send(PhoneNetwork.PHOTO_PLACE, buf);
        } else if (frame != null) {
            buf.writeVarInt(frame.getId());
            buf.writeVarInt(widthHalves);
            buf.writeVarInt(heightHalves);
            ClientPlayNetworking.send(PhoneNetwork.PHOTO_RESIZE, buf);
        }
        onClose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double amount) {
        int delta = amount > 0 ? 1 : -1;
        if (hasShiftDown()) {
            setHeight(heightHalves + delta);
        } else {
            setWidth(widthHalves + delta);
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        extractBackground(context, mouseX, mouseY, delta);
        int cx = width / 2;
        context.centeredText(font, title, cx, 10, 0xFFFFFF);
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
            context.blit(picture.texture(), px, py, pw, ph, uv[0] * tw, uv[1] * th,
                    Math.max(1, Math.round((uv[2] - uv[0]) * tw)), Math.max(1, Math.round((uv[3] - uv[1]) * th)), tw, th);
        }
        int y = height - 102;
        context.centeredText(font, Component.translatable("gui.mca_vie.photo_size.width", blocks(widthHalves)), cx, y + 6, 0xFFFFFF);
        context.centeredText(font, Component.translatable("gui.mca_vie.photo_size.height", blocks(heightHalves)), cx, y + 30, 0xFFFFFF);
        if (!fits) {
            context.centeredText(font, Component.translatable("msg.mca_vie.photo.no_support"), cx, areaBottom + 2, 0xFF5555);
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private static String blocks(int halves) {
        String half = Minecraft.getInstance().getLanguageManager().getSelected().startsWith("fr") ? ",5" : ".5";
        return halves % 2 == 0 ? String.valueOf(halves / 2) : (halves / 2) + half;
    }
}
