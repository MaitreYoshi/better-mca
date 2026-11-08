package com.maitreyoshi.mcavie.phone.client;

import dev.architectury.platform.Platform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/** Préférences locales du téléphone (côté client, partagées par tous les téléphones du joueur). */
public final class PhoneSettings {
    private static final String FILE = "mca_vie_phone.properties";
    public static final int KIND_MESSAGE = 0;
    public static final int KIND_REMINDER = 1;
    public static final int MODE_RING = 0;
    public static final int MODE_VIBRATE = 1;
    public static final int MODE_SILENT = 2;
    public static final String[] SOUNDS = {"bell", "chime", "harp", "xylophone", "pling", "amethyst"};
    public static final float[] TEXT_SCALES = {0.85f, 1f, 1.15f, 1.3f};

    public static int messageSound;
    public static int reminderSound;
    public static int mode = MODE_RING;
    public static boolean light;
    public static int textSize = 1;
    public static boolean mapRotate;
    public static int mapZoom = 1;
    public static List<String> order = new ArrayList<>();
    public static Set<String> hidden = new LinkedHashSet<>();
    private static boolean loaded;

    private PhoneSettings() {
    }

    private static Path path() {
        return Platform.getConfigFolder().resolve(FILE);
    }

    public static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path path = path();
        if (!Files.exists(path)) {
            return;
        }
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            p.load(reader);
        } catch (IOException | IllegalArgumentException e) {
            return;
        }
        messageSound = clamp(p.getProperty("message_sound"), 0, SOUNDS.length - 1, 0);
        reminderSound = clamp(p.getProperty("reminder_sound"), 0, SOUNDS.length - 1, 0);
        mode = clamp(p.getProperty("mode"), 0, 2, MODE_RING);
        light = "light".equals(p.getProperty("theme"));
        textSize = clamp(p.getProperty("text_size"), 0, TEXT_SCALES.length - 1, 1);
        mapRotate = "true".equals(p.getProperty("map_rotate"));
        mapZoom = clamp(p.getProperty("map_zoom"), 0, 3, 1);
        order = split(p.getProperty("app_order"));
        hidden = new LinkedHashSet<>(split(p.getProperty("hidden_apps")));
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("message_sound", String.valueOf(messageSound));
        p.setProperty("reminder_sound", String.valueOf(reminderSound));
        p.setProperty("mode", String.valueOf(mode));
        p.setProperty("theme", light ? "light" : "dark");
        p.setProperty("text_size", String.valueOf(textSize));
        p.setProperty("map_rotate", String.valueOf(mapRotate));
        p.setProperty("map_zoom", String.valueOf(mapZoom));
        p.setProperty("app_order", String.join(",", order));
        p.setProperty("hidden_apps", String.join(",", hidden));
        try {
            Files.createDirectories(path().getParent());
            try (Writer writer = Files.newBufferedWriter(path())) {
                p.store(writer, "Better MCA - réglages du téléphone");
            }
        } catch (IOException ignored) {
        }
    }

    public static float textScale() {
        return TEXT_SCALES[textSize];
    }

    private static List<String> split(String value) {
        List<String> list = new ArrayList<>();
        if (value != null) {
            Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(list::add);
        }
        return list;
    }

    private static int clamp(String value, int min, int max, int fallback) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value.trim())));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static SoundEvent sound(int index) {
        return switch (index) {
            case 1 -> SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value();
            case 2 -> SoundEvents.BLOCK_NOTE_BLOCK_HARP.value();
            case 3 -> SoundEvents.BLOCK_NOTE_BLOCK_XYLOPHONE.value();
            case 4 -> SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
            case 5 -> SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME;
            default -> SoundEvents.BLOCK_NOTE_BLOCK_BELL.value();
        };
    }

    /** Joue le son choisi pour une notification, selon le mode (sonnerie / vibreur / silencieux). */
    public static void notify(int kind) {
        load();
        if (mode == MODE_SILENT) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (mode == MODE_VIBRATE) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_DIDGERIDOO.value(), 0.5f, 0.5f));
            return;
        }
        preview(kind == KIND_REMINDER ? reminderSound : messageSound, kind);
    }

    /** Petit clic discret à l'ouverture d'une appli ; muet en mode vibreur ou silencieux. */
    public static void playAppClick() {
        load();
        if (mode == MODE_SILENT || mode == MODE_VIBRATE) {
            return;
        }
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.6f, 0.2f));
    }

    public static void preview(int index, int kind) {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(sound(index), kind == KIND_REMINDER ? 1.2f : 1.6f, 0.8f));
    }

    public static void previewVibrate() {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_DIDGERIDOO.value(), 0.5f, 0.5f));
    }
}
