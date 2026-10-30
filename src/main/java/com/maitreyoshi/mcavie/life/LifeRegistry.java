package com.maitreyoshi.mcavie.life;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LifeRegistry extends PersistentState {
    private static final String DATA_ID = "mca_surnames_life";

    private final Map<UUID, Long> births = new HashMap<>();
    private final Map<UUID, Long> deaths = new HashMap<>();
    private final Map<UUID, Long> zombies = new HashMap<>();

    public static LifeRegistry get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(LifeRegistry::fromNbt, LifeRegistry::new, DATA_ID);
    }

    private static LifeRegistry fromNbt(NbtCompound nbt) {
        LifeRegistry registry = new LifeRegistry();
        read(nbt.getCompound("births"), registry.births);
        read(nbt.getCompound("deaths"), registry.deaths);
        read(nbt.getCompound("zombies"), registry.zombies);
        return registry;
    }

    private static void read(NbtCompound entries, Map<UUID, Long> into) {
        for (String key : entries.getKeys()) {
            try {
                into.put(UUID.fromString(key), entries.getLong(key));
            } catch (IllegalArgumentException e) {
                MCAVie.LOGGER.warn("UUID invalide dans le registre des âges : {}", key);
            }
        }
    }

    private static NbtCompound write(Map<UUID, Long> from) {
        NbtCompound entries = new NbtCompound();
        from.forEach((uuid, day) -> entries.putLong(uuid.toString(), day));
        return entries;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.put("births", write(births));
        nbt.put("deaths", write(deaths));
        nbt.put("zombies", write(zombies));
        return nbt;
    }

    @Nullable
    public Long getBirthDay(UUID uuid) {
        return births.get(uuid);
    }

    public void setBirthDay(UUID uuid, long day) {
        births.put(uuid, day);
        markDirty();
    }

    @Nullable
    public Long getDeathDay(UUID uuid) {
        return deaths.get(uuid);
    }

    public void setDeathDay(UUID uuid, long day) {
        deaths.put(uuid, day);
        markDirty();
    }

    public void clearDeathDay(UUID uuid) {
        if (deaths.remove(uuid) != null) {
            markDirty();
        }
    }

    public boolean isZombie(UUID uuid) {
        return zombies.containsKey(uuid);
    }

    public void setZombified(UUID uuid, long day) {
        zombies.put(uuid, day);
        markDirty();
    }

    public void clearZombie(UUID uuid) {
        if (zombies.remove(uuid) != null) {
            markDirty();
        }
    }

    public void cured(UUID uuid, long today) {
        Long since = zombies.remove(uuid);
        if (since == null) {
            return;
        }
        Long birth = births.get(uuid);
        long elapsed = WorldCalendar.calendarDay(today) - WorldCalendar.calendarDay(since);
        if (birth != null && elapsed > 0) {
            births.put(uuid, WorldCalendar.gameDayOf(WorldCalendar.calendarDay(birth) + elapsed));
        }
        markDirty();
    }
}
