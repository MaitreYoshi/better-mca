package com.maitreyoshi.mcavie.phone.client;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.phone.PhoneItem;
import com.maitreyoshi.mcavie.phone.PhoneNetwork;
import com.maitreyoshi.mcavie.phone.client.PhoneClient.State;
import com.maitreyoshi.mcavie.compat.client.ClientTickEvents;
import com.maitreyoshi.mcavie.compat.client.ClientPlayNetworking;
import com.maitreyoshi.mcavie.compat.client.HudRenderCallback;
import com.maitreyoshi.mcavie.compat.client.WorldRenderEvents;
import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public final class CameraMode {
    private static final int FLASH_TICKS = 6;
    private static final double MAX_ZOOM = 10;
    private static final double ZOOM_STEP = 1.2;
    private static final float JPEG_QUALITY = 0.88f;

    @Nullable
    private static State state;
    private static boolean capture;
    private static int flash;
    private static int uploadId;
    private static double zoom = 1;
    private static double targetZoom = 1;
    private static boolean hudWasHidden;

    private CameraMode() {
    }

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(CameraMode::tick);
        WorldRenderEvents.END.register(() -> {
            if (capture) {
                capture = false;
                shoot();
            }
        });
        HudRenderCallback.EVENT.register(CameraMode::render);
    }

    public static boolean active() {
        return state != null;
    }

    public static double zoom() {
        return state == null ? 1 : zoom;
    }

    public static void scroll(double amount) {
        targetZoom = Math.max(1, Math.min(MAX_ZOOM, targetZoom * Math.pow(ZOOM_STEP, amount)));
    }

    public static void start(State phone) {
        state = phone;
        capture = false;
        flash = 0;
        zoom = 1;
        targetZoom = 1;
        MinecraftClient client = MinecraftClient.getInstance();
        hudWasHidden = client.options.hudHidden;
        client.options.hudHidden = false;
        client.setScreen(null);
    }

    public static void update(State phone) {
        if (state != null && state.number() == phone.number()) {
            state = phone.access() ? phone : null;
        }
    }

    public static void stop(boolean reopen) {
        State phone = state;
        if (phone != null) {
            MinecraftClient.getInstance().options.hudHidden = hudWasHidden;
        }
        state = null;
        capture = false;
        zoom = 1;
        targetZoom = 1;
        if (reopen && phone != null) {
            MinecraftClient.getInstance().setScreen(new PhoneScreen(phone));
            PhoneClient.send(phone.number(), PhoneNetwork.OP_REFRESH, buf -> {
            });
        }
    }

    private static void tick(MinecraftClient client) {
        if (flash > 0) {
            flash--;
        }
        if (state == null) {
            return;
        }
        if (client.player == null || client.currentScreen != null || !holds(client)) {
            stop(false);
            return;
        }
        boolean shot = false;
        while (client.options.attackKey.wasPressed()) {
            shot = true;
        }
        client.options.attackKey.setPressed(false);
        boolean back = false;
        while (client.options.useKey.wasPressed()) {
            back = true;
        }
        client.options.useKey.setPressed(false);
        if (back) {
            stop(true);
        } else if (shot && flash == 0) {
            capture = true;
        }
    }

    private static boolean holds(MinecraftClient client) {
        for (Hand hand : Hand.values()) {
            if (PhoneItem.numberOf(client.player.getStackInHand(hand)) == state.number()) {
                return true;
            }
        }
        return false;
    }

    private static void shoot() {
        MinecraftClient client = MinecraftClient.getInstance();
        State phone = state;
        if (phone == null) {
            return;
        }
        byte[] thumbnail;
        byte[] full;
        try (NativeImage screen = ScreenshotRecorder.takeScreenshot(client.getFramebuffer());
             NativeImage big = new NativeImage(PhoneNetwork.FULL_WIDTH, PhoneNetwork.FULL_HEIGHT, false);
             NativeImage small = new NativeImage(PhoneNetwork.THUMB_WIDTH, PhoneNetwork.THUMB_HEIGHT, false)) {
            int[] crop = crop(screen.getWidth(), screen.getHeight());
            screen.resizeSubRectTo(crop[0], crop[1], crop[2], crop[3], big);
            screen.resizeSubRectTo(crop[0], crop[1], crop[2], crop[3], small);
            thumbnail = small.getBytes();
            full = jpeg(big);
        } catch (IOException | RuntimeException e) {
            MCAVie.LOGGER.warn("Photo impossible", e);
            return;
        }
        if (thumbnail.length > PhoneNetwork.MAX_THUMB_BYTES || full.length > PhoneNetwork.MAX_FULL_BYTES) {
            client.player.sendMessage(Text.translatable("msg.mca_vie.phone.photo_too_big"), true);
            return;
        }
        flash = FLASH_TICKS;
        client.player.playSound(SoundEvents.BLOCK_DISPENSER_FAIL, 0.4f, 2.0f);
        byte[] data = new byte[thumbnail.length + full.length];
        System.arraycopy(thumbnail, 0, data, 0, thumbnail.length);
        System.arraycopy(full, 0, data, thumbnail.length, full.length);
        int id = ++uploadId;
        int total = (data.length + PhoneNetwork.PHOTO_CHUNK - 1) / PhoneNetwork.PHOTO_CHUNK;
        for (int i = 0; i < total; i++) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeVarInt(phone.number());
            buf.writeVarInt(id);
            buf.writeVarInt(i);
            buf.writeVarInt(total);
            buf.writeVarInt(thumbnail.length);
            buf.writeByteArray(Arrays.copyOfRange(data, i * PhoneNetwork.PHOTO_CHUNK, Math.min(data.length, (i + 1) * PhoneNetwork.PHOTO_CHUNK)));
            ClientPlayNetworking.send(PhoneNetwork.PHOTO_UPLOAD, buf);
        }
    }

    private static byte[] jpeg(NativeImage image) throws IOException {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int abgr = image.getColor(x, y);
                pixels[y * width + x] = (abgr & 0xFF) << 16 | (abgr & 0xFF00) | (abgr >> 16 & 0xFF);
            }
        }
        buffered.setRGB(0, 0, width, height, pixels, 0, width);
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.setOutput(stream);
            writer.write(null, new IIOImage(buffered, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static int[] crop(int width, int height) {
        int w = width;
        int h = width * PhoneNetwork.FULL_HEIGHT / PhoneNetwork.FULL_WIDTH;
        if (h > height) {
            h = height;
            w = height * PhoneNetwork.FULL_WIDTH / PhoneNetwork.FULL_HEIGHT;
        }
        return new int[]{(width - w) / 2, (height - h) / 2, w, h};
    }

    public static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (state == null && flash == 0) {
            return;
        }
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        if (state != null) {
            zoom += (targetZoom - zoom) * 0.25;
            if (Math.abs(targetZoom - zoom) < 0.005) {
                zoom = targetZoom;
            }
            int[] frame = crop(width, height);
            int x = frame[0];
            int y = frame[1];
            int w = frame[2];
            int h = frame[3];
            context.fill(0, 0, width, y, 0xC0000000);
            context.fill(0, y + h, width, height, 0xC0000000);
            context.fill(0, y, x, y + h, 0xC0000000);
            context.fill(x + w, y, width, y + h, 0xC0000000);
            int corner = Math.min(w, h) / 8;
            int m = 8;
            corner(context, x + m, y + m, corner, 1, 1);
            corner(context, x + w - m, y + m, corner, -1, 1);
            corner(context, x + m, y + h - m, corner, 1, -1);
            corner(context, x + w - m, y + h - m, corner, -1, -1);
            context.fill(width / 2 - 4, height / 2, width / 2 + 5, height / 2 + 1, 0x90FFFFFF);
            context.fill(width / 2, height / 2 - 4, width / 2 + 1, height / 2 + 5, 0x90FFFFFF);
            context.fill(x + m + 4, y + m + 4, x + m + 8, y + m + 8, 0xFFE5484D);
            String level = String.format("x%.1f", zoom);
            int lw = client.textRenderer.getWidth(level);
            context.fill(x + w - m - lw - 12, y + m + 4, x + w - m - 4, y + m + 18, 0x90000000);
            context.drawTextWithShadow(client.textRenderer, level, x + w - m - lw - 8, y + m + 7, 0xFFFFFF);
            int barX = x + w - m - 8;
            int barTop = y + h / 4;
            int barBottom = y + h * 3 / 4;
            context.fill(barX, barTop, barX + 2, barBottom, 0x60FFFFFF);
            int knob = barBottom - (int) ((barBottom - barTop) * Math.log(zoom) / Math.log(MAX_ZOOM));
            context.fill(barX - 3, knob - 1, barX + 5, knob + 2, 0xFFFFFFFF);
            Text hint = Text.translatable("gui.mca_vie.phone.camera_hint");
            int tw = client.textRenderer.getWidth(hint);
            context.fill(width / 2 - tw / 2 - 6, y + h - m - 18, width / 2 + tw / 2 + 6, y + h - m - 4, 0x90000000);
            context.drawCenteredTextWithShadow(client.textRenderer, hint, width / 2, y + h - m - 15, 0xFFFFFF);
        }
        if (flash > 0) {
            int alpha = (int) (200 * (flash - tickDelta) / FLASH_TICKS);
            if (alpha > 0) {
                context.fill(0, 0, width, height, (Math.min(alpha, 255) << 24) | 0xFFFFFF);
            }
        }
    }

    private static void corner(DrawContext context, int x, int y, int size, int dx, int dy) {
        int x2 = x + dx * size;
        int y2 = y + dy * size;
        context.fill(Math.min(x, x2), Math.min(y, y + dy * 2), Math.max(x, x2), Math.max(y, y + dy * 2), 0xFFFFFFFF);
        context.fill(Math.min(x, x + dx * 2), Math.min(y, y2), Math.max(x, x + dx * 2), Math.max(y, y2), 0xFFFFFFFF);
    }
}
