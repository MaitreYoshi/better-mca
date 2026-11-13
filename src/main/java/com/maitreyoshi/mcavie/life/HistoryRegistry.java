package com.maitreyoshi.mcavie.life;

import com.maitreyoshi.mcavie.MCAVie;
import net.mca.entity.ai.relationship.Gender;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HistoryRegistry extends SavedData {
    private static final String DATA_ID = "mca_vie_history";

    private final Map<UUID, Long> pendingDeaths = new HashMap<>();
    private final List<Spawn> pendingSpawns = new ArrayList<>();
    private final Map<UUID, Integer> origins = new HashMap<>();

    public record Spawn(UUID uuid, int village, String name, Gender gender) {
        CompoundTag toNbt() {
            CompoundTag nbt = new CompoundTag();
            nbt.putUUID("uuid", uuid);
            nbt.putInt("village", village);
            nbt.putString("name", name);
            nbt.putInt("gender", gender.getId());
            return nbt;
        }

        static Spawn fromNbt(CompoundTag nbt) {
            return new Spawn(nbt.getUUID("uuid"), nbt.getInt("village"), nbt.getString("name"), Gender.byId(nbt.getInt("gender")));
        }
    }

    public static HistoryRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(HistoryRegistry::fromNbt, HistoryRegistry::new, DATA_ID);
    }

    private static HistoryRegistry fromNbt(CompoundTag nbt) {
        HistoryRegistry registry = new HistoryRegistry();
        CompoundTag deaths = nbt.getCompound("deaths");
        for (String key : deaths.getAllKeys()) {
            try {
                registry.pendingDeaths.put(UUID.fromString(key), deaths.getLong(key));
            } catch (IllegalArgumentException e) {
                MCAVie.LOGGER.warn("UUID invalide dans l'historique : {}", key);
            }
        }
        ListTag spawns = nbt.getList("spawns", Tag.TAG_COMPOUND);
        for (int i = 0; i < spawns.size(); i++) {
            registry.pendingSpawns.add(Spawn.fromNbt(spawns.getCompound(i)));
        }
        CompoundTag origins = nbt.getCompound("origins");
        for (String key : origins.getAllKeys()) {
            try {
                registry.origins.put(UUID.fromString(key), origins.getInt(key));
            } catch (IllegalArgumentException e) {
                MCAVie.LOGGER.warn("UUID invalide dans l'historique : {}", key);
            }
        }
        return registry;
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        CompoundTag deaths = new CompoundTag();
        pendingDeaths.forEach((uuid, day) -> deaths.putLong(uuid.toString(), day));
        nbt.put("deaths", deaths);
        ListTag spawns = new ListTag();
        pendingSpawns.forEach(spawn -> spawns.add(spawn.toNbt()));
        nbt.put("spawns", spawns);
        CompoundTag origins = new CompoundTag();
        this.origins.forEach((uuid, village) -> origins.putInt(uuid.toString(), village));
        nbt.put("origins", origins);
        return nbt;
    }

    @Nullable
    public Long pendingDeath(UUID uuid) {
        return pendingDeaths.get(uuid);
    }

    public void addPendingDeath(UUID uuid, long day) {
        pendingDeaths.put(uuid, day);
        setDirty();
    }

    public void purgePendingDeaths(java.util.function.Predicate<UUID> stale) {
        if (pendingDeaths.keySet().removeIf(stale)) {
            setDirty();
        }
    }

    public void clearPendingDeath(UUID uuid) {
        if (pendingDeaths.remove(uuid) != null) {
            setDirty();
        }
    }

    public List<Spawn> pendingSpawns() {
        return pendingSpawns;
    }

    public void addSpawn(Spawn spawn) {
        pendingSpawns.add(spawn);
        setDirty();
    }

    public void removeSpawn(UUID uuid) {
        if (pendingSpawns.removeIf(s -> s.uuid().equals(uuid))) {
            setDirty();
        }
    }

    public Map<UUID, Integer> origins() {
        return origins;
    }

    public void setOrigin(UUID uuid, int village) {
        origins.put(uuid, village);
        setDirty();
    }

    public void removeOrigin(UUID uuid) {
        if (origins.remove(uuid) != null) {
            setDirty();
        }
    }
}
