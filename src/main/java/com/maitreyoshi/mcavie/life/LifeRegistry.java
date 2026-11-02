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
    /** Vies passées des ressuscités : jours de mort et de résurrection à la suite (mort, retour, mort, retour…). */
    private final Map<UUID, long[]> pastLives = new HashMap<>();
    /** Heure de jeu (ticks) de la dernière résurrection, pour que l'étonnement des villageois s'estompe. */
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
        NbtCompound lives = nbt.getCompound("past_lives");
        for (String key : lives.getKeys()) {
            try {
                registry.pastLives.put(UUID.fromString(key), lives.getLongArray(key));
            } catch (IllegalArgumentException e) {
                MCAVie.LOGGER.warn("UUID invalide dans le registre des âges : {}", key);
            }
        }
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
        nbt.put("resurrected_at", write(resurrectedAt));
        NbtCompound lives = new NbtCompound();
        pastLives.forEach((uuid, days) -> lives.putLongArray(uuid.toString(), days));
        nbt.put("past_lives", lives);
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

    /** Enregistre un retour à la vie : la mort précédente et le jour du retour s'ajoutent aux vies passées. */
    public void resurrected(UUID uuid, long death, long today, long gameTime) {
        long[] previous = pastLives.getOrDefault(uuid, new long[0]);
        long[] lives = java.util.Arrays.copyOf(previous, previous.length + 2);
        lives[previous.length] = death;
        lives[previous.length + 1] = today;
        pastLives.put(uuid, lives);
        resurrectedAt.put(uuid, gameTime);
        deaths.remove(uuid);
        markDirty();
    }

    /** Jours de mort et de résurrection à la suite, vide pour quelqu'un qui n'est jamais revenu. */
    public List<Long> pastLives(UUID uuid) {
        long[] lives = pastLives.get(uuid);
        return lives == null ? List.of() : java.util.Arrays.stream(lives).boxed().toList();
    }

    @Nullable
    public Long resurrectedAt(UUID uuid) {
        return resurrectedAt.get(uuid);
    }

    /**
     * Naissance du corps : la vraie naissance décalée du temps passé dans la tombe. Sert à la vieillesse,
     * à la santé et au travail ; l'âge affiché reste celui de la vraie naissance.
     */
    @Nullable
    public Long getBodyBirthDay(UUID uuid) {
        Long birth = births.get(uuid);
        long[] lives = pastLives.get(uuid);
        if (birth == null || lives == null) {
            return birth;
        }
        long buried = 0;
        for (int i = 0; i + 1 < lives.length; i += 2) {
            buried += Math.max(0, WorldCalendar.calendarDay(lives[i + 1]) - WorldCalendar.calendarDay(lives[i]));
        }
        return buried == 0 ? birth : WorldCalendar.gameDayOf(WorldCalendar.calendarDay(birth) + buried);
    }
}
