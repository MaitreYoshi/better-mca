package com.maitreyoshi.mcavie.client;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

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

    private static final Component[] BARS = new Component[101];

    public static Component bar(int happiness) {
        int clamped = Math.max(0, Math.min(100, happiness));
        Component bar = BARS[clamped];
        if (bar == null) {
            bar = BARS[clamped] = createBar(clamped);
        }
        return bar;
    }

    private static MutableComponent createBar(int happiness) {
        int filled = Math.round(Math.max(0, Math.min(100, happiness)) / 100f * BAR_LENGTH);
        return Component.literal(BAR_CHAR.repeat(filled)).setStyle(Style.EMPTY.withColor(color(happiness)))
                .append(Component.literal(BAR_CHAR.repeat(BAR_LENGTH - filled)).withStyle(ChatFormatting.DARK_GRAY));
    }

    public static Component menuLine(Entry entry) {
        MutableComponent text = Component.translatable("gui.mca_vie.happiness").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(" "))
                .append(bar(entry.happiness()))
                .append(Component.literal(" " + entry.happiness() + " %").withStyle(ChatFormatting.WHITE));
        if (entry.sick()) {
            text.append(Component.literal(" "))
                    .append(Component.translatable("gui.mca_vie.sick").withStyle(ChatFormatting.RED));
        }
        return text;
    }
}
