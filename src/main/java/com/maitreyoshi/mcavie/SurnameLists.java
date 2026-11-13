package com.maitreyoshi.mcavie;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;

public class SurnameLists extends SimpleJsonResourceReloadListener {
    public static final String DEFAULT_REGION = "default";
    public static final Identifier ID = MCAVie.id("surname_lists");

    private static final Map<String, Pool> POOLS = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();

    public SurnameLists() {
        super(new Gson(), "mca_surnames");
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager, ProfilerFiller profiler) {
        POOLS.clear();
        prepared.forEach((id, json) -> {
            Pool pool = POOLS.computeIfAbsent(id.getPath(), r -> new Pool());
            if (json.isJsonArray()) {
                json.getAsJsonArray().forEach(e -> pool.add(e.getAsString(), 1));
            } else if (json.isJsonObject()) {
                JsonObject obj = json.getAsJsonObject();
                obj.entrySet().forEach(e -> pool.add(e.getKey(), e.getValue().getAsFloat()));
            }
        });
        MCAVie.LOGGER.info("Listes de noms de famille chargées : {} régions", POOLS.size());
    }

    public static String pick(String region) {
        Pool pool = POOLS.get(region);
        if (pool == null || pool.isEmpty()) {
            pool = POOLS.get(DEFAULT_REGION);
        }
        if (pool == null || pool.isEmpty()) {
            return "Sansnom";
        }
        return pool.pick(RANDOM);
    }

    private static class Pool {
        private final List<String> names = new ArrayList<>();
        private final List<Float> cumulative = new ArrayList<>();
        private float total;

        void add(String name, float weight) {
            if (name == null || name.isBlank() || weight <= 0) {
                return;
            }
            total += weight;
            names.add(name.trim());
            cumulative.add(total);
        }

        boolean isEmpty() {
            return names.isEmpty();
        }

        String pick(RandomSource random) {
            float r = random.nextFloat() * total;
            for (int i = 0; i < names.size(); i++) {
                if (r < cumulative.get(i)) {
                    return names.get(i);
                }
            }
            return names.get(names.size() - 1);
        }
    }
}
