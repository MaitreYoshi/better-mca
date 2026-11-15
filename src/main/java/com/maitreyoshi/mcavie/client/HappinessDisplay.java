package com.maitreyoshi.mcavie.client;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class HappinessDisplay {
    private static final int BAR_LENGTH = 20;
    private static final String BAR_CHAR = "|";

    private static final Map<Integer, Entry> BY_ENTITY_ID = new HashMap<>();

    public record Entry(int happiness, boolean sick) {
    }

    private HappinessDisplay() {
    }

    public static void set(int entityId, int happiness, boolean sick) {
        BY_ENTITY_ID.put(entityId, new Entry(happiness, sick));
    }

    @Nullable
    public static Entry get(int entityId) {
        return BY_ENTITY_ID.get(entityId);
    }

    public static void remove(int entityId) {
        BY_ENTITY_ID.remove(entityId);
    }

    public static void clear() {
        BY_ENTITY_ID.clear();
    }

    public static int color(int happiness) {
        float t = Math.max(0, Math.min(100, happiness)) / 100f;
        int red = t < 0.5f ? 255 : (int) (255 * (1 - t) * 2);
        int green = t < 0.5f ? (int) (255 * t * 2) : 255;
        return (red << 16) | (green << 8) | 0x20;
    }

    private static final Text[] BARS = new Text[101];

    public static Text bar(int happiness) {
        int clamped = Math.max(0, Math.min(100, happiness));
        Text bar = BARS[clamped];
        if (bar == null) {
            bar = BARS[clamped] = createBar(clamped);
        }
        return bar;
    }

    private static MutableText createBar(int happiness) {
        int filled = Math.round(Math.max(0, Math.min(100, happiness)) / 100f * BAR_LENGTH);
        return Text.literal(BAR_CHAR.repeat(filled)).setStyle(Style.EMPTY.withColor(color(happiness)))
                .append(Text.literal(BAR_CHAR.repeat(BAR_LENGTH - filled)).formatted(Formatting.DARK_GRAY));
    }

    public static Text menuLine(Entry entry) {
        MutableText text = Text.translatable("gui.mca_vie.happiness").formatted(Formatting.WHITE)
                .append(Text.literal(" "))
                .append(bar(entry.happiness()))
                .append(Text.literal(" " + entry.happiness() + " %").formatted(Formatting.WHITE));
        if (entry.sick()) {
            text.append(Text.literal(" "))
                    .append(Text.translatable("gui.mca_vie.sick").formatted(Formatting.RED));
        }
        return text;
    }
}
