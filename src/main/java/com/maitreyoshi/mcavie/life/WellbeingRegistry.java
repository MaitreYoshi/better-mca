package com.maitreyoshi.mcavie.life;

import com.maitreyoshi.mcavie.MCAVie;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WellbeingRegistry extends SavedData {
    private static final String DATA_ID = "mca_surnames_wellbeing";

    private final Map<UUID, Record> records = new HashMap<>();

    public static WellbeingRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(WellbeingRegistry::fromNbt, WellbeingRegistry::new, DATA_ID);
    }

    private static WellbeingRegistry fromNbt(CompoundTag nbt) {
        WellbeingRegistry registry = new WellbeingRegistry();
        CompoundTag entries = nbt.getCompound("villagers");
        for (String key : entries.getAllKeys()) {
            try {
                registry.records.put(UUID.fromString(key), Record.fromNbt(entries.getCompound(key)));
            } catch (IllegalArgumentException e) {
                MCAVie.LOGGER.warn("UUID invalide dans le registre du bonheur : {}", key);
            }
        }
        return registry;
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        CompoundTag entries = new CompoundTag();
        records.forEach((uuid, record) -> entries.put(uuid.toString(), record.toNbt()));
        nbt.put("villagers", entries);
        return nbt;
    }

    @Nullable
    public Record get(UUID uuid) {
        return records.get(uuid);
    }

    public void put(UUID uuid, Record record) {
        records.put(uuid, record);
        setDirty();
    }

    public void remove(UUID uuid) {
        if (records.remove(uuid) != null) {
            setDirty();
        }
    }

    public void addHappiness(UUID uuid, float delta) {
        Record record = records.get(uuid);
        if (record != null) {
            record.addHappiness(delta);
            setDirty();
        }
    }

    public static final class Record {
        public float happiness;
        public long lastProcessedDay;
        public long lastSleptNight;
        public long lastCompanyDay;
        public long lastSurroundedDay;
        public long lastFriendDay = Long.MIN_VALUE / 2;
        public int knownChildren;
        public int knownRelationshipState;
        public boolean sick;
        public long sickUntil;

        public transient int lastSentHappiness = -1;
        public transient boolean lastSentSick;

        public void addHappiness(float delta) {
            happiness = Math.max(0, Math.min(100, happiness + delta));
        }

        CompoundTag toNbt() {
            CompoundTag nbt = new CompoundTag();
            nbt.putFloat("happiness", happiness);
            nbt.putLong("lastProcessedDay", lastProcessedDay);
            nbt.putLong("lastSleptNight", lastSleptNight);
            nbt.putLong("lastCompanyDay", lastCompanyDay);
            nbt.putLong("lastSurroundedDay", lastSurroundedDay);
            nbt.putLong("lastFriendDay", lastFriendDay);
            nbt.putInt("knownChildren", knownChildren);
            nbt.putInt("knownRelationshipState", knownRelationshipState);
            nbt.putBoolean("sick", sick);
            nbt.putLong("sickUntil", sickUntil);
            return nbt;
        }

        static Record fromNbt(CompoundTag nbt) {
            Record record = new Record();
            record.happiness = nbt.getFloat("happiness");
            record.lastProcessedDay = nbt.getLong("lastProcessedDay");
            record.lastSleptNight = nbt.getLong("lastSleptNight");
            record.lastCompanyDay = nbt.getLong("lastCompanyDay");
            record.lastSurroundedDay = nbt.getLong("lastSurroundedDay");
            if (nbt.contains("lastFriendDay")) {
                record.lastFriendDay = nbt.getLong("lastFriendDay");
            }
            record.knownChildren = nbt.getInt("knownChildren");
            record.knownRelationshipState = nbt.getInt("knownRelationshipState");
            record.sick = nbt.getBoolean("sick");
            record.sickUntil = nbt.contains("sickUntil") ? nbt.getLong("sickUntil") : -1;
            return record;
        }
    }
}
