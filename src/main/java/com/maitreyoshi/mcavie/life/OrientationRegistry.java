package com.maitreyoshi.mcavie.life;

import com.maitreyoshi.mcavie.MCAVie;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class OrientationRegistry extends SavedData {
    private static final String DATA_ID = "mca_vie_orientation";
    private static final int OBSERVED = 0x10;

    private final Map<UUID, Integer> entries = new HashMap<>();

    public static OrientationRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(OrientationRegistry::fromNbt, OrientationRegistry::new, DATA_ID);
    }

    private static OrientationRegistry fromNbt(CompoundTag nbt) {
        OrientationRegistry registry = new OrientationRegistry();
        CompoundTag villagers = nbt.getCompound("villagers");
        for (String key : villagers.getAllKeys()) {
            try {
                registry.entries.put(UUID.fromString(key), (int) villagers.getByte(key));
            } catch (IllegalArgumentException e) {
                MCAVie.LOGGER.warn("UUID invalide dans le registre des orientations : {}", key);
            }
        }
        return registry;
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        CompoundTag villagers = new CompoundTag();
        entries.forEach((uuid, value) -> villagers.putByte(uuid.toString(), value.byteValue()));
        nbt.put("villagers", villagers);
        return nbt;
    }

    @Nullable
    public Orientation get(UUID uuid) {
        Integer value = entries.get(uuid);
        return value == null ? null : Orientation.byOrdinal(value & ~OBSERVED);
    }

    public boolean isObserved(UUID uuid) {
        Integer value = entries.get(uuid);
        return value != null && (value & OBSERVED) != 0;
    }

    public void set(UUID uuid, Orientation orientation, boolean observed) {
        int value = orientation.ordinal() | (observed ? OBSERVED : 0);
        Integer old = entries.put(uuid, value);
        if (old == null || old != value) {
            setDirty();
        }
    }
}
