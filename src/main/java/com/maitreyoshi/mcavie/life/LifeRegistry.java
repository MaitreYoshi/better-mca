package com.maitreyoshi.mcavie.life;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LifeRegistry extends PersistentState {
    private static final String DATA_ID = "mca_surnames_life";

    private final Map<UUID, Long> births = new HashMap<>();
    private final Map<UUID, Long> deaths = new HashMap<>();
    private final Map<UUID, Long> zombies = new HashMap<>();
    private final Map<UUID, Long> exactBirths = new HashMap<>();
    private final Map<UUID, Long> exactDeaths = new HashMap<>();
    private final Map<UUID, long[]> pastLives = new HashMap<>();
    private final Map<UUID, long[]> legacyLives = new HashMap<>();
    private final Map<UUID, Long> resurrectedAt = new HashMap<>();

    public static LifeRegistry get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(LifeRegistry::fromNbt, LifeRegistry::new, DATA_ID);
    }

    private static LifeRegistry fromNbt(NbtCompound nbt) {
        LifeRegistry registry = new LifeRegistry();
        read(nbt.getCompound("births"), registry.births);
        read(nbt.getCompound("deaths"), registry.deaths);
        read(nbt.getCompound("zombies"), registry.zombies);
        read(nbt.getCompound("resurrected_at"), registry.resurrectedAt);
        read(nbt.getCompound("exact_births"), registry.exactBirths);
        read(nbt.getCompound("exact_deaths"), registry.exactDeaths);
        readLives(nbt.getCompound("lives"), registry.pastLives);
        readLives(nbt.getCompound("past_lives"), registry.legacyLives);
        return registry;
    }

    private static void readLives(NbtCompound entries, Map<UUID, long[]> into) {
        for (String key : entries.getKeys()) {
            try {
                into.put(UUID.fromString(key), entries.getLongArray(key));
            } catch (IllegalArgumentException e) {
                MCAVie.LOGGER.warn("UUID invalide dans le registre des âges : {}", key);
            }
        }
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
        nbt.put("resurrected_at", write(resurrectedAt));
        nbt.put("exact_births", write(exactBirths));
        nbt.put("exact_deaths", write(exactDeaths));
        NbtCompound lives = new NbtCompound();
        pastLives.forEach((uuid, days) -> lives.putLongArray(uuid.toString(), days));
        nbt.put("lives", lives);
        NbtCompound legacy = new NbtCompound();
        legacyLives.forEach((uuid, days) -> legacy.putLongArray(uuid.toString(), days));
        nbt.put("past_lives", legacy);
        return nbt;
    }

    @Nullable
    public Long getBirthDay(UUID uuid) {
        return births.get(uuid);
    }

    public void setBirthDay(UUID uuid, long day) {
        births.put(uuid, day);
        exactBirths.remove(uuid);
        markDirty();
    }

    @Nullable
    public Long getDeathDay(UUID uuid) {
        return deaths.get(uuid);
    }

    public void setDeathDay(UUID uuid, long day) {
        deaths.put(uuid, day);
        exactDeaths.remove(uuid);
        markDirty();
    }

    public void clearDeathDay(UUID uuid) {
        exactDeaths.remove(uuid);
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
            exactBirths.computeIfPresent(uuid, (k, exact) -> exact + elapsed);
        }
        markDirty();
    }

    public void recordBirth(UUID uuid, long day, long exactCalendarDay) {
        setBirthDay(uuid, day);
        exactBirths.put(uuid, exactCalendarDay);
    }

    public void recordDeath(UUID uuid, long day, long exactCalendarDay) {
        setDeathDay(uuid, day);
        exactDeaths.put(uuid, exactCalendarDay);
    }

    @Nullable
    public Long exactBirth(UUID uuid) {
        Long exact = exactBirths.get(uuid);
        Long birth = births.get(uuid);
        if (exact != null || birth == null) {
            return exact;
        }
        return WorldCalendar.spreadCalendarDay(birth, uuid.getMostSignificantBits());
    }

    @Nullable
    public Long exactDeath(UUID uuid) {
        Long exact = exactDeaths.get(uuid);
        Long death = deaths.get(uuid);
        if (exact != null || death == null) {
            return exact;
        }
        return WorldCalendar.spreadCalendarDay(death, uuid.getLeastSignificantBits());
    }

    public void resurrected(UUID uuid, long exactToday, long gameTime) {
        Long death = exactDeath(uuid);
        long[] previous = lives(uuid);
        long[] lives = java.util.Arrays.copyOf(previous, previous.length + 2);
        lives[previous.length] = death == null ? exactToday : death;
        lives[previous.length + 1] = exactToday;
        pastLives.put(uuid, lives);
        resurrectedAt.put(uuid, gameTime);
        deaths.remove(uuid);
        exactDeaths.remove(uuid);
        markDirty();
    }

    private long[] lives(UUID uuid) {
        long[] legacy = legacyLives.remove(uuid);
        if (legacy != null) {
            long[] converted = new long[legacy.length];
            for (int i = 0; i < legacy.length; i++) {
                converted[i] = WorldCalendar.spreadCalendarDay(legacy[i], uuid.getLeastSignificantBits() + i);
            }
            pastLives.put(uuid, converted);
            markDirty();
        }
        return pastLives.getOrDefault(uuid, new long[0]);
    }

    public List<Long> pastLives(UUID uuid) {
        return java.util.Arrays.stream(lives(uuid)).boxed().toList();
    }

    @Nullable
    public Long resurrectedAt(UUID uuid) {
        return resurrectedAt.get(uuid);
    }

    @Nullable
    public Long getBodyBirthDay(UUID uuid) {
        Long birth = births.get(uuid);
        long[] lives = lives(uuid);
        if (birth == null || lives.length == 0) {
            return birth;
        }
        long buried = 0;
        for (int i = 0; i + 1 < lives.length; i += 2) {
            buried += Math.max(0, lives[i + 1] - lives[i]);
        }
        return buried == 0 ? birth : WorldCalendar.gameDayOf(WorldCalendar.calendarDay(birth) + buried);
    }
}
