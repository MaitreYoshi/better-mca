package com.maitreyoshi.mcavie.aging;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.maitreyoshi.mcavie.MCAVie;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AgingConfig {
    private static final String FILE = "mca_vie_aging.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static AgingConfig instance;

    public boolean greyHair = true;
    public int greyStartAge = 50;
    public int whiteAge = 70;
    public float greyStrength = 1.0f;

    public boolean wrinkles = true;
    public int lightWrinklesAge = 50;
    public int strongWrinklesAge = 70;

    public int greyBeardAge = 55;
    public float greyBeardChance = 0.5f;

    public boolean slowWalk = true;
    public int slowStartAge = 60;
    public int slowFullAge = 85;
    public float slowMin = 0.10f;
    public float slowMax = 0.25f;

    public boolean lowerHealth = true;
    public int healthStartAge = 65;
    public int healthFullAge = 90;
    public float healthMaxLoss = 0.20f;

    public boolean stoop = true;
    public int stoopStartAge = 70;
    public int stoopFullAge = 90;
    public float stoopDegrees = 14f;
    public float shrinkMax = 0.05f;

    public boolean cane = true;
    public int caneAge = 75;

    public int retirementAge = 65;
    public boolean stayHome = true;
    public int stayHomeAge = 70;
    public float stayHomeChance = 0.5f;
    public int stayHomeRadius = 6;

    public boolean deeperVoice = true;
    public int voiceStartAge = 50;
    public int voiceFullAge = 85;
    public float voicePitchDrop = 0.15f;

    public int tombMiddleScore = 2;
    public int tombRichScore = 6;

    public static AgingConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE);
        AgingConfig config = null;
        boolean readable = true;
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, AgingConfig.class);
            } catch (Exception e) {
                MCAVie.LOGGER.error("Impossible de lire {}, valeurs par défaut utilisées", FILE, e);
                readable = false;
            }
        }
        if (config == null) {
            config = new AgingConfig();
        }
        config.clamp();
        instance = config;
        if (!readable) {
            return;
        }

        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            MCAVie.LOGGER.warn("Impossible d'écrire {}", FILE, e);
        }
    }

    public static void applyRemote(String json) {
        try {
            AgingConfig remote = GSON.fromJson(json, AgingConfig.class);
            if (remote != null) {
                remote.clamp();
                instance = remote;
            }
        } catch (Exception e) {
            MCAVie.LOGGER.warn("Config du vieillissement reçue du serveur illisible", e);
        }
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    private void clamp() {
        greyStrength = clamp01(greyStrength);
        greyBeardChance = clamp01(greyBeardChance);
        slowMin = Math.max(0, Math.min(0.9f, slowMin));
        slowMax = Math.max(slowMin, Math.min(0.9f, slowMax));
        healthMaxLoss = Math.max(0, Math.min(0.9f, healthMaxLoss));
        stoopDegrees = Math.max(0, Math.min(45, stoopDegrees));
        shrinkMax = Math.max(0, Math.min(0.5f, shrinkMax));
        stayHomeChance = clamp01(stayHomeChance);
        stayHomeRadius = Math.max(1, Math.min(32, stayHomeRadius));
        voicePitchDrop = Math.max(0, Math.min(0.8f, voicePitchDrop));
    }

    private static float clamp01(float value) {
        return Math.max(0, Math.min(1, value));
    }

    public static float progress(int years, int start, int full) {
        if (years < start) {
            return 0;
        }
        if (full <= start) {
            return 1;
        }
        return Math.min(1, (years - start) / (float) (full - start));
    }
}
