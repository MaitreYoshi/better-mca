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

    public boolean isClaimed(Identifier dimension, BlockPos pos, UUID except) {
        for (Map.Entry<UUID, Record> entry : records.entrySet()) {
            Record record = entry.getValue();
            if (!entry.getKey().equals(except) && pos.equals(record.depot)
                    && (record.depotDimension == null || record.depotDimension.equals(dimension))) {
                return true;
            }
        }
        return false;
    }

    public java.util.Set<Map.Entry<UUID, Record>> entries() {
        return records.entrySet();
    }

    public java.util.Collection<Record> all() {
        return java.util.Collections.unmodifiableCollection(records.values());
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
        // appli Employés : dernier état connu (PNJ hors chunk chargé) et ordre en attente
        @Nullable
        public String lastName;
        @Nullable
        public BlockPos lastPos;
        @Nullable
        public Identifier lastDimension;
        public float lastHealth;
        public float lastMaxHealth = 20;
        public int lastStatus = -1;
        public long lastSeen;
        // apparence MCA compacte (PhoneEmployees.look) pour afficher le PNJ dans l'appli même hors chunk chargé
        @Nullable
        public NbtCompound look;
        @Nullable
        public String pendingOrder;
        // ordre actif donné depuis l'appli (follow/stay/home/work), alertes courantes, rapport du jour
        @Nullable
        public String order;
        public int alerts;
        public long alertCooldown;
        public long reportDay = -1;
        public NbtCompound report = new NbtCompound();
        // conséquences de la messagerie à appliquer au rechargement du PNJ (cœurs envers l'employeur, humeur)
        public int pendingHearts;
        public int pendingMood;
        // messagerie de l'appli Employés (géré par EmployeeChat)
        @Nullable
        public NbtCompound chat;

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
            if (lastName != null) {
                nbt.putString("lastName", lastName);
            }
            if (lastPos != null) {
                nbt.put("lastPos", NbtHelper.fromBlockPos(lastPos));
            }
            if (lastDimension != null) {
                nbt.putString("lastDimension", lastDimension.toString());
            }
            nbt.putFloat("lastHealth", lastHealth);
            nbt.putFloat("lastMaxHealth", lastMaxHealth);
            nbt.putInt("lastStatus", lastStatus);
            nbt.putLong("lastSeen", lastSeen);
            if (look != null) {
                nbt.put("look", look);
            }
            if (pendingOrder != null) {
                nbt.putString("pendingOrder", pendingOrder);
            }
            if (chat != null) {
                nbt.put("chat", chat);
            }
            if (order != null) {
                nbt.putString("order", order);
            }
            nbt.putInt("alerts", alerts);
            nbt.putLong("reportDay", reportDay);
            nbt.put("report", report);
            nbt.putInt("pendingHearts", pendingHearts);
            nbt.putInt("pendingMood", pendingMood);
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
            record.lastName = nbt.contains("lastName") ? nbt.getString("lastName") : null;
            record.lastPos = nbt.contains("lastPos") ? NbtHelper.toBlockPos(nbt.getCompound("lastPos")) : null;
            record.lastDimension = nbt.contains("lastDimension") ? Identifier.tryParse(nbt.getString("lastDimension")) : null;
            record.lastHealth = nbt.getFloat("lastHealth");
            record.lastMaxHealth = nbt.contains("lastMaxHealth") ? nbt.getFloat("lastMaxHealth") : 20;
            record.lastStatus = nbt.contains("lastStatus") ? nbt.getInt("lastStatus") : -1;
            record.lastSeen = nbt.getLong("lastSeen");
            record.look = nbt.contains("look") ? nbt.getCompound("look") : null;
            record.pendingOrder = nbt.contains("pendingOrder") ? nbt.getString("pendingOrder") : null;
            record.chat = nbt.contains("chat") ? nbt.getCompound("chat") : null;
            record.order = nbt.contains("order") ? nbt.getString("order") : null;
            record.alerts = nbt.getInt("alerts");
            record.reportDay = nbt.contains("reportDay") ? nbt.getLong("reportDay") : -1;
            record.report = nbt.getCompound("report");
            record.pendingHearts = nbt.getInt("pendingHearts");
            record.pendingMood = nbt.getInt("pendingMood");
            return record;
        }
    }
}
