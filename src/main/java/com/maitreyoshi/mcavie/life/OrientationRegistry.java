package com.maitreyoshi.mcavie.life;

import com.maitreyoshi.mcavie.MCAVie;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class OrientationRegistry extends PersistentState {
    private static final String DATA_ID = "mca_vie_orientation";
    private static final int OBSERVED = 0x10;

    private final Map<UUID, Integer> entries = new HashMap<>();

    public static OrientationRegistry get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(OrientationRegistry::fromNbt, OrientationRegistry::new, DATA_ID);
    }

    private static OrientationRegistry fromNbt(NbtCompound nbt) {
        OrientationRegistry registry = new OrientationRegistry();
        NbtCompound villagers = nbt.getCompound("villagers");
        for (String key : villagers.getKeys()) {
            try {
                registry.entries.put(UUID.fromString(key), (int) villagers.getByte(key));
            } catch (IllegalArgumentException e) {
                MCAVie.LOGGER.warn("UUID invalide dans le registre des orientations : {}", key);
            }
        }
        return registry;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound villagers = new NbtCompound();
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
            markDirty();
        }
    }
}
