package com.maitreyoshi.mcavie.phone;

import com.maitreyoshi.mcavie.MCAVie;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PortraitRegistry extends SavedData {
    private static final String DATA_ID = "mca_vie_portraits";

    private final Map<UUID, UUID> portraits = new HashMap<>();
    private final Set<UUID> images = new HashSet<>();

    public static PortraitRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PortraitRegistry::fromNbt, PortraitRegistry::new, DATA_ID);
    }

    private static PortraitRegistry fromNbt(CompoundTag nbt) {
        PortraitRegistry registry = new PortraitRegistry();
        CompoundTag entries = nbt.getCompound("portraits");
        for (String key : entries.getAllKeys()) {
            try {
                UUID image = UUID.fromString(entries.getString(key));
                registry.portraits.put(UUID.fromString(key), image);
                registry.images.add(image);
            } catch (IllegalArgumentException e) {
                MCAVie.LOGGER.warn("Portrait invalide dans le registre : {}", key);
            }
        }
        return registry;
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        CompoundTag entries = new CompoundTag();
        portraits.forEach((person, image) -> entries.putString(person.toString(), image.toString()));
        nbt.put("portraits", entries);
        return nbt;
    }

    @Nullable
    public UUID get(UUID person) {
        return portraits.get(person);
    }

    public boolean isPortrait(UUID image) {
        return images.contains(image);
    }

    @Nullable
    public UUID set(UUID person, @Nullable UUID image) {
        UUID old = image == null ? portraits.remove(person) : portraits.put(person, image);
        if (old != null) {
            images.remove(old);
        }
        if (image != null) {
            images.add(image);
        }
        setDirty();
        return old;
    }
}
