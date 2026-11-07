package com.maitreyoshi.mcavie;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SurnameRegistry extends PersistentState {
    private static final String DATA_ID = "mca_surnames";

    private final Map<UUID, String> surnames = new HashMap<>();

    public static SurnameRegistry get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(SurnameRegistry::fromNbt, SurnameRegistry::new, DATA_ID);
    }

    private static SurnameRegistry fromNbt(NbtCompound nbt) {
        SurnameRegistry registry = new SurnameRegistry();
        NbtCompound entries = nbt.getCompound("surnames");
        for (String key : entries.getKeys()) {
            try {
                registry.surnames.put(UUID.fromString(key), entries.getString(key));
            } catch (IllegalArgumentException e) {
                MCAVie.LOGGER.warn("UUID invalide dans le registre des noms : {}", key);
            }
        }
        return registry;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound entries = new NbtCompound();
        surnames.forEach((uuid, surname) -> entries.putString(uuid.toString(), surname));
        nbt.put("surnames", entries);
        return nbt;
    }

    @Nullable
    public String get(UUID uuid) {
        return surnames.get(uuid);
    }

    public void set(UUID uuid, String surname) {
        surnames.put(uuid, surname);
        markDirty();
    }
}
