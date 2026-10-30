package com.maitreyoshi.mcavie.work;

import com.maitreyoshi.mcavie.MCAVie;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WorkRegistry extends PersistentState {
    private static final String DATA_ID = "mca_surnames_work";
    public static final int DEFAULT_RADIUS = 24;
    public static final String RETIRED_PREFIX = "mca_vie.retired:";

    private final Map<UUID, Record> records = new HashMap<>();

    public static WorkRegistry get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(WorkRegistry::fromNbt, WorkRegistry::new, DATA_ID);
    }

    private static WorkRegistry fromNbt(NbtCompound nbt) {
        WorkRegistry registry = new WorkRegistry();
        NbtCompound entries = nbt.getCompound("villagers");
        for (String key : entries.getKeys()) {
            try {
                registry.records.put(UUID.fromString(key), Record.fromNbt(entries.getCompound(key)));
            } catch (IllegalArgumentException e) {
                MCAVie.LOGGER.warn("UUID invalide dans le registre des métiers : {}", key);
            }
        }
        return registry;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound entries = new NbtCompound();
        records.forEach((uuid, record) -> entries.put(uuid.toString(), record.toNbt()));
        nbt.put("villagers", entries);
        return nbt;
    }

    @Nullable
    public Record get(UUID uuid) {
        return records.get(uuid);
    }

    public String retiredKey(UUID uuid, boolean deceased) {
        Record record = records.get(uuid);
        String former = record == null ? null : record.formerJob;
        if (former == null) {
            return deceased ? "" : "job.mca_vie." + Job.RETIRED.id();
        }
        return deceased ? former : RETIRED_PREFIX + former;
    }

    public Job getJob(UUID uuid) {
        Record record = records.get(uuid);
        return record == null ? Job.NONE : record.job;
    }

    public Record getOrCreate(UUID uuid) {
        return records.computeIfAbsent(uuid, u -> new Record());
    }

    public static final class Record {
        public Job job = Job.NONE;
        @Nullable
        public BlockPos center;
        public int radius = DEFAULT_RADIUS;
        @Nullable
        public BlockPos depot;
        public int level = 1;
        public int experience;
        public boolean apprenticeChosen;
        @Nullable
        public String formerJob;
        @Nullable
        public UUID employer;
        @Nullable
        public Identifier depotDimension;

        NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putInt("job", job.ordinal());
            if (center != null) {
                nbt.put("center", NbtHelper.fromBlockPos(center));
            }
            nbt.putInt("radius", radius);
            if (depot != null) {
                nbt.put("depot", NbtHelper.fromBlockPos(depot));
            }
            nbt.putInt("level", level);
            nbt.putInt("experience", experience);
            nbt.putBoolean("apprenticeChosen", apprenticeChosen);
            if (formerJob != null) {
                nbt.putString("formerJob", formerJob);
            }
            if (employer != null) {
                nbt.putUuid("employer", employer);
            }
            if (depotDimension != null) {
                nbt.putString("depotDimension", depotDimension.toString());
            }
            return nbt;
        }

        static Record fromNbt(NbtCompound nbt) {
            Record record = new Record();
            record.job = Job.byId(nbt.getInt("job"));
            record.center = nbt.contains("center") ? NbtHelper.toBlockPos(nbt.getCompound("center")) : null;
            record.radius = nbt.contains("radius") ? nbt.getInt("radius") : DEFAULT_RADIUS;
            record.depot = nbt.contains("depot") ? NbtHelper.toBlockPos(nbt.getCompound("depot")) : null;
            record.level = Math.max(1, nbt.getInt("level"));
            record.experience = nbt.getInt("experience");
            record.apprenticeChosen = nbt.getBoolean("apprenticeChosen");
            record.formerJob = nbt.contains("formerJob") ? nbt.getString("formerJob") : null;
            record.employer = nbt.containsUuid("employer") ? nbt.getUuid("employer") : null;
            record.depotDimension = nbt.contains("depotDimension") ? Identifier.tryParse(nbt.getString("depotDimension")) : null;
            return record;
        }
    }
}
