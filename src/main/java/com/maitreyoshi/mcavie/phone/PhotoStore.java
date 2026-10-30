package com.maitreyoshi.mcavie.phone;

import com.maitreyoshi.mcavie.MCAVie;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.UUID;

public final class PhotoStore {
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    public record Image(byte[] data, boolean jpeg) {
    }

    private PhotoStore() {
    }

    private static Path file(MinecraftServer server, UUID id, String extension) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("mca_vie_photos").resolve(id + extension);
    }

    public static boolean validThumbnail(byte[] data) {
        if (data.length < 33 || data.length > PhoneNetwork.MAX_THUMB_BYTES || !Arrays.equals(Arrays.copyOf(data, 8), PNG_SIGNATURE)) {
            return false;
        }
        ByteBuffer header = ByteBuffer.wrap(data, 12, 12);
        byte[] type = new byte[4];
        header.get(type);
        return Arrays.equals(type, new byte[]{'I', 'H', 'D', 'R'})
                && header.getInt() == PhoneNetwork.THUMB_WIDTH
                && header.getInt() == PhoneNetwork.THUMB_HEIGHT;
    }

    public static boolean validFull(byte[] data) {
        if (data.length < 4 || data.length > PhoneNetwork.MAX_FULL_BYTES || (data[0] & 0xFF) != 0xFF || (data[1] & 0xFF) != 0xD8) {
            return false;
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("jpeg");
            if (input == null || !readers.hasNext()) {
                return false;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return reader.getWidth(0) == PhoneNetwork.FULL_WIDTH && reader.getHeight(0) == PhoneNetwork.FULL_HEIGHT;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    public static boolean write(MinecraftServer server, UUID id, byte[] thumbnail, byte[] full) {
        try {
            Path thumb = file(server, id, ".png");
            Files.createDirectories(thumb.getParent());
            Files.write(thumb, thumbnail);
            Files.write(file(server, id, ".jpg"), full);
            return true;
        } catch (IOException e) {
            MCAVie.LOGGER.error("Impossible d'enregistrer la photo {}", id, e);
            delete(server, id);
            return false;
        }
    }

    @Nullable
    public static Image read(MinecraftServer server, UUID id, boolean full) {
        try {
            Path jpeg = file(server, id, ".jpg");
            if (full && Files.exists(jpeg)) {
                return new Image(Files.readAllBytes(jpeg), true);
            }
            Path png = file(server, id, ".png");
            return Files.exists(png) ? new Image(Files.readAllBytes(png), false) : null;
        } catch (IOException e) {
            MCAVie.LOGGER.error("Impossible de lire la photo {}", id, e);
            return null;
        }
    }

    public static boolean writePortrait(MinecraftServer server, UUID from, UUID to) {
        Image source = read(server, from, true);
        if (source == null) {
            return false;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(source.data()));
            if (image == null) {
                return false;
            }
            int side = Math.min(image.getWidth(), image.getHeight());
            int size = Math.min(side, PhoneNetwork.PORTRAIT_SIZE);
            BufferedImage portrait = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = portrait.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            int x = (image.getWidth() - side) / 2;
            int y = (image.getHeight() - side) / 2;
            graphics.drawImage(image, 0, 0, size, size, x, y, x + side, y + side, null);
            graphics.dispose();
            Path png = file(server, to, ".png");
            Files.createDirectories(png.getParent());
            return ImageIO.write(portrait, "png", png.toFile());
        } catch (IOException | RuntimeException e) {
            MCAVie.LOGGER.error("Impossible de créer le portrait {}", to, e);
            delete(server, to);
            return false;
        }
    }

    public static boolean copy(MinecraftServer server, UUID from, UUID to) {
        try {
            Path png = file(server, from, ".png");
            if (!Files.exists(png)) {
                return false;
            }
            Files.copy(png, file(server, to, ".png"));
            Path jpeg = file(server, from, ".jpg");
            if (Files.exists(jpeg)) {
                Files.copy(jpeg, file(server, to, ".jpg"));
            }
            return true;
        } catch (IOException e) {
            MCAVie.LOGGER.error("Impossible de copier la photo {}", from, e);
            delete(server, to);
            return false;
        }
    }

    public static void delete(MinecraftServer server, UUID id) {
        try {
            Files.deleteIfExists(file(server, id, ".png"));
            Files.deleteIfExists(file(server, id, ".jpg"));
        } catch (IOException e) {
            MCAVie.LOGGER.error("Impossible de supprimer la photo {}", id, e);
        }
    }
}
