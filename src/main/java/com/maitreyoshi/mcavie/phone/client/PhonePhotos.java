package com.maitreyoshi.mcavie.phone.client;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.phone.PhoneNetwork;
import com.maitreyoshi.mcavie.compat.client.ClientPlayNetworking;
import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PhonePhotos {
    public record Picture(Identifier texture, int width, int height) {
    }

    private record Key(UUID id, boolean full) {
    }

    private static final Map<Key, Picture> PICTURES = new HashMap<>();
    private static final Set<Key> REQUESTED = new HashSet<>();
    private static final Map<Key, Long> LAST_USE = new HashMap<>();
    private static final long UNUSED_TICKS = 1200;
    private static long ticks;

    private PhonePhotos() {
    }

    @Nullable
    public static Picture print(UUID id, boolean full) {
        Key key = new Key(id, full);
        Picture picture = PICTURES.get(key);
        LAST_USE.put(key, ticks);
        if (picture == null && REQUESTED.add(key)) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeUuid(id);
            buf.writeBoolean(full);
            ClientPlayNetworking.send(PhoneNetwork.PRINT_REQUEST, buf);
        }
        if (picture == null && full) {
            Picture thumbnail = PICTURES.get(new Key(id, false));
            if (thumbnail != null) {
                LAST_USE.put(new Key(id, false), ticks);
            }
            return thumbnail;
        }
        return picture;
    }

    @Nullable
    public static Picture portrait(UUID id) {
        Key key = new Key(id, false);
        Picture picture = PICTURES.get(key);
        LAST_USE.put(key, ticks);
        if (picture == null && REQUESTED.add(key)) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeUuid(id);
            ClientPlayNetworking.send(PhoneNetwork.PORTRAIT_REQUEST, buf);
        }
        return picture;
    }

    static void tick() {
        ticks++;
        if (ticks % 200 != 0) {
            return;
        }
        LAST_USE.entrySet().removeIf(entry -> {
            if (ticks - entry.getValue() < UNUSED_TICKS) {
                return false;
            }
            Picture picture = PICTURES.remove(entry.getKey());
            REQUESTED.remove(entry.getKey());
            if (picture != null) {
                MinecraftClient.getInstance().getTextureManager().destroyTexture(picture.texture());
            }
            return true;
        });
    }

    @Nullable
    public static Picture get(int number, UUID id, boolean full) {
        Key key = new Key(id, full);
        Picture picture = PICTURES.get(key);
        LAST_USE.put(key, ticks);
        if (picture == null && REQUESTED.add(key)) {
            PhoneClient.send(number, PhoneNetwork.OP_PHOTO, buf -> {
                buf.writeUuid(id);
                buf.writeBoolean(full);
            });
        }
        if (picture == null && full) {
            return PICTURES.get(new Key(id, false));
        }
        return picture;
    }

    static void store(UUID id, boolean full, boolean jpeg, byte[] data) {
        Key key = new Key(id, full);
        if (PICTURES.containsKey(key) || !REQUESTED.contains(key)) {
            return;
        }
        try {
            NativeImage image = jpeg ? decodeJpeg(data) : NativeImage.read(data);
            Identifier texture = MCAVie.id("phone_photo/" + id + (full ? "_full" : "_thumb"));
            NativeImageBackedTexture backed = new NativeImageBackedTexture(image);
            backed.setFilter(true, false);
            MinecraftClient.getInstance().getTextureManager().registerTexture(texture, backed);
            PICTURES.put(key, new Picture(texture, image.getWidth(), image.getHeight()));
        } catch (IOException | RuntimeException e) {
            MCAVie.LOGGER.warn("Photo illisible : {}", id);
        }
    }

    private static NativeImage decodeJpeg(byte[] data) throws IOException {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(data));
        if (source == null) {
            throw new IOException("jpeg");
        }
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = source.getRGB(0, 0, width, height, null, 0, width);
        NativeImage image = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = pixels[y * width + x];
                image.setColor(x, y, 0xFF000000 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16 & 0xFF));
            }
        }
        return image;
    }

    public static void forget(UUID id) {
        for (boolean full : new boolean[]{false, true}) {
            Key key = new Key(id, full);
            Picture picture = PICTURES.remove(key);
            REQUESTED.remove(key);
            LAST_USE.remove(key);
            if (picture != null) {
                MinecraftClient.getInstance().getTextureManager().destroyTexture(picture.texture());
            }
        }
    }

    static void clear() {
        for (Picture picture : PICTURES.values()) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(picture.texture());
        }
        PICTURES.clear();
        REQUESTED.clear();
        LAST_USE.clear();
    }
}
