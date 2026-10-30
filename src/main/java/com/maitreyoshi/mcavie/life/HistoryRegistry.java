package com.maitreyoshi.mcavie.life;

import com.maitreyoshi.mcavie.MCAVie;
import net.mca.entity.ai.relationship.Gender;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HistoryRegistry extends PersistentState {
    private static final String DATA_ID = "mca_vie_history";

    private final Map<UUID, Long> pendingDeaths = new HashMap<>();
    private final List<Spawn> pendingSpawns = new ArrayList<>();
    private final Map<UUID, Integer> origins = new HashMap<>();

    public record Spawn(UUID uuid, int village, String name, Gender gender) {
        NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putUuid("uuid", uuid);
            nbt.putInt("village", village);
            nbt.putString("name", name);
            nbt.putInt("gender", gender.getId());
            return nbt;
        }

        static Spawn fromNbt(NbtCompound nbt) {
            return new Spawn(nbt.getUuid("uuid"), nbt.getInt("village"), nbt.getString("name"), Gender.byId(nbt.getInt("gender")));
        }
    }

    public static HistoryRegistry get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(HistoryRegistry::fromNbt, HistoryRegistry::new, DATA_ID);
    }

    private static HistoryRegistry fromNbt(NbtCompound nbt) {
        HistoryRegistry registry = new HistoryRegistry();
        NbtCompound deaths = nbt.getCompound("deaths");
        for (String key : deaths.getKeys()) {
            try {
                registry.pendingDeaths.put(UUID.fromString(key), deaths.getLong(key));
            } catch (IllegalArgumentException e) {
                MCAVie.LOGGER.warn("UUID invalide dans l'historique : {}", key);
            }
        }
        NbtList spawns = nbt.getList("spawns", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < spawns.size(); i++) {
            registry.pendingSpawns.add(Spawn.fromNbt(spawns.getCompound(i)));
        }
        NbtCompound origins = nbt.getCompound("origins");
        for (String key : origins.getKeys()) {
            try {
                registry.origins.put(UUID.fromString(key), origins.getInt(key));
            } catch (IllegalArgumentException e) {
                MCAVie.LOGGER.warn("UUID invalide dans l'historique : {}", key);
            }
        }
        return registry;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound deaths = new NbtCompound();
        pendingDeaths.forEach((uuid, day) -> deaths.putLong(uuid.toString(), day));
        nbt.put("deaths", deaths);
        NbtList spawns = new NbtList();
        pendingSpawns.forEach(spawn -> spawns.add(spawn.toNbt()));
        nbt.put("spawns", spawns);
        NbtCompound origins = new NbtCompound();
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
        markDirty();
    }

    public void purgePendingDeaths(java.util.function.Predicate<UUID> stale) {
        if (pendingDeaths.keySet().removeIf(stale)) {
            markDirty();
        }
    }

    public void clearPendingDeath(UUID uuid) {
        if (pendingDeaths.remove(uuid) != null) {
            markDirty();
        }
    }

    public List<Spawn> pendingSpawns() {
        return pendingSpawns;
    }

    public void addSpawn(Spawn spawn) {
        pendingSpawns.add(spawn);
        markDirty();
    }

    public void removeSpawn(UUID uuid) {
        if (pendingSpawns.removeIf(s -> s.uuid().equals(uuid))) {
            markDirty();
        }
    }

    public Map<UUID, Integer> origins() {
        return origins;
    }

    public void setOrigin(UUID uuid, int village) {
        origins.put(uuid, village);
        markDirty();
    }

    public void removeOrigin(UUID uuid) {
        if (origins.remove(uuid) != null) {
            markDirty();
        }
    }
}
