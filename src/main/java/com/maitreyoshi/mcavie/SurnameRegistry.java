package com.maitreyoshi.mcavie;

import com.maitreyoshi.mcavie.compat.Saved;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SurnameRegistry extends SavedData {
    private static final String DATA_ID = "mca_surnames";

    private final Map<UUID, String> surnames = new HashMap<>();

    public static SurnameRegistry get(MinecraftServer server) {
        return Saved.get(server.overworld(), SurnameRegistry::fromNbt, SurnameRegistry::new, SurnameRegistry::save, DATA_ID);
    }

    private static SurnameRegistry fromNbt(CompoundTag nbt) {
        SurnameRegistry registry = new SurnameRegistry();
        CompoundTag entries = nbt.getCompoundOrEmpty("surnames");
        for (String key : entries.keySet()) {
            try {
                String stored = entries.getStringOr(key, "");
                String upper = SurnameText.upper(stored);
                registry.surnames.put(UUID.fromString(key), upper);
                if (!upper.equals(stored)) {
                    registry.setDirty();
                }
            } catch (IllegalArgumentException e) {
                MCAVie.LOGGER.warn("UUID invalide dans le registre des noms : {}", key);
            }
        }
        return registry;
    }

        public CompoundTag save(CompoundTag nbt) {
        CompoundTag entries = new CompoundTag();
        surnames.forEach((uuid, surname) -> entries.putString(uuid.toString(), surname));
        nbt.put("surnames", entries);
        return nbt;
    }

    @Nullable
    public String get(UUID uuid) {
        return surnames.get(uuid);
    }

    public void set(UUID uuid, String surname) {
        String upper = SurnameText.upper(surname);
        if (!upper.equals(surnames.put(uuid, upper))) {
            setDirty();
        }
    }
}
