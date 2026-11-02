package com.maitreyoshi.mcavie.life.client;

import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.life.TombstoneBlocks;
import com.maitreyoshi.mcavie.life.TombstoneDetails;
import com.maitreyoshi.mcavie.life.TombstonePortraits;
import com.maitreyoshi.mcavie.phone.client.PhonePhotos;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.mca.block.TombstoneBlock;
import net.minecraft.block.BlockState;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class TombstoneClient {
    private static final long REFRESH_MS = 10_000;
    private static final float MAX_SCALE = 0.75f;
    private static final int FRAME_COLOR = 0xFF3A3226;
    private static final int EMPTY_COLOR = 0xFF202030;
    private static final int WOOD_FRAME_COLOR = 0xFF4A2F18;
    private static final int OVAL_RIM_COLOR = 0xFFD4AF37;
    private static final int OVAL_STRIPS = 20;

    private record Portrait(Optional<UUID> image, long time) {
    }

    private static final Map<UUID, Portrait> PORTRAITS = new HashMap<>();
    private static final Map<UUID, Long> REQUESTED = new HashMap<>();

    private TombstoneClient() {
    }

    public static void register() {
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(), TombstoneBlocks.WOODEN_CROSS, TombstoneBlocks.STELE, TombstoneBlocks.MARBLE);
        ClientPlayNetworking.registerGlobalReceiver(TombstonePortraits.RESULT, (client, handler, buf, responseSender) -> {
            UUID person = buf.readUuid();
            UUID image = buf.readBoolean() ? buf.readUuid() : null;
            client.execute(() -> PORTRAITS.put(person, new Portrait(Optional.ofNullable(image), Util.getMeasuringTimeMs())));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            PORTRAITS.clear();
            REQUESTED.clear();
        });
    }

    @Nullable
    private static UUID portrait(UUID person) {
        long now = Util.getMeasuringTimeMs();
        Portrait portrait = PORTRAITS.get(person);
        Long requested = REQUESTED.get(person);
        if ((portrait == null || now - portrait.time() > REFRESH_MS) && (requested == null || now - requested > REFRESH_MS)) {
            REQUESTED.put(person, now);
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeUuid(person);
            ClientPlayNetworking.send(TombstonePortraits.REQUEST, buf);
        }
        return portrait == null ? null : portrait.image().orElse(null);
    }

    private static Text date(long day) {
        LocalDate date = WorldCalendar.dateOfDay(day);
        return Text.translatable("block.mca_vie.tombstone.date", date.getDayOfMonth(),
                Text.translatable("block.mca_vie.tombstone.month." + date.getMonthValue()), WorldCalendar.displayYearOfDay(day));
    }

    private static String lives(TombstoneDetails details) {
        List<Long> past = details.pastLives();
        StringBuilder out = new StringBuilder();
        String start = details.birth() == null ? "?" : String.valueOf(WorldCalendar.displayYearOfDay(details.birth()));
        for (int i = 0; i + 1 < past.size(); i += 2) {
            out.append('(').append(start).append('-').append(WorldCalendar.displayYearOfDay(past.get(i))).append(") ");
            start = String.valueOf(WorldCalendar.displayYearOfDay(past.get(i + 1)));
        }
        return out.append('(').append(start).append('-').append(WorldCalendar.displayYearOfDay(details.death())).append(')').toString();
    }

    public static void render(TombstoneDetails details, TombstoneBlock block, TextRenderer text, MatrixStack matrices,
                              VertexConsumerProvider consumers, int light) {
        layout(details, TombstoneModels.Look.MCA, block.getLineWidth(), block.getMaxNameHeight() + 30, text, matrices, consumers, light);
    }

    public static void renderStyled(TombstoneDetails details, int tier, BlockState state, TextRenderer text, MatrixStack matrices,
                                    VertexConsumerProvider consumers, int light) {
        matrices.push();
        matrices.translate(0.5, 0, 0.5);
        Direction facing = state.get(Properties.HORIZONTAL_FACING).getOpposite();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()));
        TombstoneModels.TextArea area = TombstoneModels.area(tier);
        matrices.translate(0, area.top(), area.z());
        matrices.scale(0.010416667F, 0.010416667F, 0.010416667F);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180));
        layout(details, area.look(), area.width(), area.height(), text, matrices, consumers, light);
        matrices.pop();
    }

    private static void layout(TombstoneDetails details, TombstoneModels.Look look, float width, float height, TextRenderer text,
                               MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        UUID image = portrait(details.person());

        List<Text> lines = new ArrayList<>();
        lines.add(Text.literal(details.firstName()));
        if (!details.lastName().isEmpty()) {
            lines.add(Text.literal(details.lastName().toUpperCase(Locale.ROOT)));
        }
        if (details.job() != null) {
            lines.add(Text.translatable(details.job()));
        }
        Text born = details.birth() == null ? null : Text.translatable("block.mca_vie.tombstone.born", date(details.birth()));
        Text died = Text.translatable("block.mca_vie.tombstone.died", date(details.death()));

        float medallion = image == null ? 0 : Math.max(14, Math.min(36, Math.round(height * 0.4f)));
        float gap = image == null ? 0 : 3;
        float available = height - medallion - gap;

        if (!details.pastLives().isEmpty()) {
            // ressuscité : une période par vie, (naissance-mort) (retour-mort)…
            lines.add(Text.literal(lives(details)));
        } else if (born == null) {
            lines.add(died);
        } else {
            Text both = Text.translatable("block.mca_vie.tombstone.dates", born, died);
            float scale = Math.min(MAX_SCALE, available / ((lines.size() + 1) * 10));
            if (text.getWidth(both) * scale <= width) {
                lines.add(both);
            } else {
                lines.add(born);
                lines.add(died);
            }
        }

        float scale = Math.min(MAX_SCALE, available / (lines.size() * 10));
        float y = (height - medallion - gap - lines.size() * 10 * scale) / 2;

        if (image != null) {
            if (look == TombstoneModels.Look.ENGRAVED || look == TombstoneModels.Look.GOLD) {
                drawOval(image, matrices, consumers, y, medallion, light);
            } else {
                drawMedallion(image, matrices, consumers, -medallion / 2, y, medallion, look == TombstoneModels.Look.WOOD ? WOOD_FRAME_COLOR : FRAME_COLOR, light);
            }
            y += medallion + gap;
        }

        for (int i = 0; i < lines.size(); i++) {
            Text line = lines.get(i);
            float lineScale = Math.min(scale, width / Math.max(1, text.getWidth(line)));
            matrices.push();
            matrices.translate(0, y + (10 * scale - 10 * lineScale) / 2, 0);
            matrices.scale(lineScale, lineScale, lineScale);
            boolean name = i == 0 || (i == 1 && !details.lastName().isEmpty());
            float x = -text.getWidth(line) / 2F;
            Matrix4f position = matrices.peek().getPositionMatrix();
            switch (look) {
                case WOOD -> text.draw(line.asOrderedText(), x, 0, name ? 0x2E1C0C : 0x4A3420, false, position, consumers,
                        TextRenderer.TextLayerType.NORMAL, 0, light);
                case ENGRAVED -> text.draw(line.asOrderedText(), x, 0, name ? 0x262626 : 0x3C3C3C, false, position, consumers,
                        TextRenderer.TextLayerType.NORMAL, 0, light);
                case GOLD -> text.drawWithOutline(line.asOrderedText(), x, 0, name ? 0xF2CC5A : 0xD9B24C, 0x3E2A0A, position, consumers, light);
                default -> text.drawWithOutline(line.asOrderedText(), x, 0, name ? 0xFFFFFF : 0xD8D8D8, 0x000000, position, consumers, light);
            }
            matrices.pop();
            y += 10 * scale;
        }
    }

    private static void drawOval(UUID image, MatrixStack matrices, VertexConsumerProvider consumers, float y, float size, int light) {
        Matrix4f position = matrices.peek().getPositionMatrix();
        float border = Math.max(1, size / 14);
        float height = size;
        float width = size * 0.78f;
        float cy = y + height / 2;
        VertexConsumer frame = consumers.getBuffer(RenderLayer.getTextBackground());
        for (int i = 0; i < OVAL_STRIPS; i++) {
            float y0 = y + height * i / OVAL_STRIPS;
            float y1 = y + height * (i + 1) / OVAL_STRIPS;
            float half = halfWidth(width / 2, height / 2, (y0 + y1) / 2 - cy);
            rect(frame, position, -half, y0, half, y1, 0, OVAL_RIM_COLOR, light);
        }
        PhonePhotos.Picture picture = PhonePhotos.portrait(image);
        float innerW = width / 2 - border;
        float innerH = height / 2 - border;
        float top = cy - innerH;
        float ratio = innerW / innerH;
        if (picture == null) {
            for (int i = 0; i < OVAL_STRIPS; i++) {
                float y0 = top + 2 * innerH * i / OVAL_STRIPS;
                float y1 = top + 2 * innerH * (i + 1) / OVAL_STRIPS;
                float half = halfWidth(innerW, innerH, (y0 + y1) / 2 - cy);
                rect(frame, position, -half, y0, half, y1, -0.15f, EMPTY_COLOR, light);
            }
            return;
        }
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getText(picture.texture()));
        for (int i = 0; i < OVAL_STRIPS; i++) {
            float y0 = top + 2 * innerH * i / OVAL_STRIPS;
            float y1 = top + 2 * innerH * (i + 1) / OVAL_STRIPS;
            float half = halfWidth(innerW, innerH, (y0 + y1) / 2 - cy);
            float u0 = 0.5f - ratio / 2 * half / innerW;
            float u1 = 0.5f + ratio / 2 * half / innerW;
            float v0 = (y0 - top) / (2 * innerH);
            float v1 = (y1 - top) / (2 * innerH);
            vertex(buffer, position, -half, y0, -0.15f, u0, v0);
            vertex(buffer, position, -half, y1, -0.15f, u0, v1);
            vertex(buffer, position, half, y1, -0.15f, u1, v1);
            vertex(buffer, position, half, y0, -0.15f, u1, v0);
        }
    }

    private static float halfWidth(float a, float b, float dy) {
        float t = dy / b;
        return a * (float) Math.sqrt(Math.max(0, 1 - t * t));
    }

    private static void drawMedallion(UUID image, MatrixStack matrices, VertexConsumerProvider consumers, float x, float y, float size, int frameColor, int light) {
        Matrix4f position = matrices.peek().getPositionMatrix();
        float border = Math.max(1, size / 14);
        float x1 = x + size;
        float y1 = y + size;
        VertexConsumer frame = consumers.getBuffer(RenderLayer.getTextBackground());
        rect(frame, position, x, y, x1, y + border, 0, frameColor, light);
        rect(frame, position, x, y1 - border, x1, y1, 0, frameColor, light);
        rect(frame, position, x, y + border, x + border, y1 - border, 0, frameColor, light);
        rect(frame, position, x1 - border, y + border, x1, y1 - border, 0, frameColor, light);

        PhonePhotos.Picture picture = PhonePhotos.portrait(image);
        float ix = x + border;
        float iy = y + border;
        float ix1 = x1 - border;
        float iy1 = y1 - border;
        if (picture == null) {
            rect(frame, position, ix, iy, ix1, iy1, 0, EMPTY_COLOR, light);
            return;
        }
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getText(picture.texture()));
        vertex(buffer, position, ix, iy, 0, 0, 0);
        vertex(buffer, position, ix, iy1, 0, 0, 1);
        vertex(buffer, position, ix1, iy1, 0, 1, 1);
        vertex(buffer, position, ix1, iy, 0, 1, 0);
    }

    private static void rect(VertexConsumer buffer, Matrix4f position, float x0, float y0, float x1, float y1, float z, int color, int light) {
        int a = color >>> 24;
        int r = color >> 16 & 0xFF;
        int g = color >> 8 & 0xFF;
        int b = color & 0xFF;
        buffer.vertex(position, x0, y0, z).color(r, g, b, a).light(light).next();
        buffer.vertex(position, x0, y1, z).color(r, g, b, a).light(light).next();
        buffer.vertex(position, x1, y1, z).color(r, g, b, a).light(light).next();
        buffer.vertex(position, x1, y0, z).color(r, g, b, a).light(light).next();
        buffer.vertex(position, x1, y0, z).color(r, g, b, a).light(light).next();
        buffer.vertex(position, x1, y1, z).color(r, g, b, a).light(light).next();
        buffer.vertex(position, x0, y1, z).color(r, g, b, a).light(light).next();
        buffer.vertex(position, x0, y0, z).color(r, g, b, a).light(light).next();
    }

    private static void vertex(VertexConsumer buffer, Matrix4f position, float x, float y, float z, float u, float v) {
        buffer.vertex(position, x, y, z).color(255, 255, 255, 255).texture(u, v).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).next();
    }
}
