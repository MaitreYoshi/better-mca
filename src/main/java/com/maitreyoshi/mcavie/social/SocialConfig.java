package com.maitreyoshi.mcavie.social;

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

public final class SocialConfig {
    private static final String FILE = "mca_vie_social.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static SocialConfig instance;

    public boolean showChats = true;
    public int chatRadius = 12;
    public int lookSeconds = 3;
    /** Recopie aussi les répliques dans le chat (les bulles restent l'affichage principal). */
    public boolean chatCopy = false;

    public static SocialConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE);
        SocialConfig config = null;
        boolean readable = true;
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, SocialConfig.class);
            } catch (Exception e) {
                MCAVie.LOGGER.error("Impossible de lire {}, valeurs par défaut utilisées", FILE, e);
                readable = false;
            }
        }
        if (config == null) {
            config = new SocialConfig();
        }
        config.chatRadius = Math.max(1, Math.min(64, config.chatRadius));
        config.lookSeconds = Math.max(0, Math.min(30, config.lookSeconds));
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
}
