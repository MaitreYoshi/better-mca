package com.maitreyoshi.mcavie.phone;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import com.maitreyoshi.mcavie.compat.ServerPlayNetworking;
import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkManager;
import com.maitreyoshi.mcavie.work.WorkRegistry;
import com.maitreyoshi.mcavie.work.WorkStatus;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.entity.ai.MoveState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

// Appli « Employés » : liste des PNJ dont le joueur est l'employeur (registre des métiers) et licenciement à distance.
// Un PNJ dont le chunk n'est pas chargé est décrit par son dernier instantané ; un licenciement s'applique à son rechargement.
public final class PhoneEmployees {
    public static final Identifier REQUEST = MCAVie.id("employees_request");
    public static final Identifier LIST = MCAVie.id("employees_list");
    public static final Identifier FIRE = MCAVie.id("employee_fire");
    public static final Identifier ORDER = MCAVie.id("employee_order");
    public static final String ORDER_FIRE = "fire";
    public static final String ORDER_FOLLOW = "follow";
    public static final String ORDER_STAY = "stay";
    public static final String ORDER_HOME = "home";
    public static final String ORDER_WORK = "work";
    public static final int ALERT_HURT = 1;
    public static final int ALERT_FULL = 2;
    public static final int ALERT_TOOL = 4;
    public static final int ALERT_WORKSTATION = 8;
    public static final int ALERT_DANGER = 16;
    private static final String[] ALERT_KEYS = {"hurt", "full", "tool", "workstation", "danger"};
    private static final long ALERT_COOLDOWN = 1200;
    private static final int HOME_REACHED = 6;
    private static final int SNAPSHOT_INTERVAL = 5;
    private static final int RATE_WINDOW = 20;
    private static final int RATE_MAX = 20;
    // [début de la fenêtre, paquets reçus] ; seulement lu et écrit sur le thread serveur
    private static final Map<ServerPlayer, long[]> RATE = new WeakHashMap<>();

    private PhoneEmployees() {
    }

    // status : ordinal de WorkStatus, ou -1 si le PNJ ne travaille pas (repos)
    public record Employee(UUID uuid, String name, int job, int status, boolean loaded, float health, float maxHealth,
                           BlockPos pos, String dimension, long seenAgo, @Nullable CompoundTag look,
                           int alerts, String order, String pending, CompoundTag report) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUUID(uuid);
            buf.writeUtf(name);
            buf.writeVarInt(job);
            buf.writeVarInt(status);
            buf.writeBoolean(loaded);
            buf.writeFloat(health);
            buf.writeFloat(maxHealth);
            buf.writeBlockPos(pos);
            buf.writeUtf(dimension);
            buf.writeVarLong(seenAgo);
            buf.writeNbt(look);
            buf.writeVarInt(alerts);
            buf.writeUtf(order);
            buf.writeUtf(pending);
            buf.writeNbt(report);
        }

        public static Employee read(FriendlyByteBuf buf) {
            return new Employee(buf.readUUID(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                    buf.readFloat(), buf.readFloat(), buf.readBlockPos(), buf.readUtf(), buf.readVarLong(), buf.readNbt(),
                    buf.readVarInt(), buf.readUtf(), buf.readUtf(), orEmpty(buf.readNbt()));
        }

        private static CompoundTag orEmpty(@Nullable CompoundTag nbt) {
            return nbt != null ? nbt : new CompoundTag();
        }
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(REQUEST, (server, player, handler, buf, responseSender) ->
                server.execute(() -> {
                    if (allowed(player)) {
                        send(player);
                    }
                }));
        ServerPlayNetworking.registerGlobalReceiver(FIRE, (server, player, handler, buf, responseSender) -> {
            UUID uuid = buf.readUUID();
            server.execute(() -> {
                if (!allowed(player)) {
                    return;
                }
                fire(player, uuid);
                send(player);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(ORDER, (server, player, handler, buf, responseSender) -> {
            UUID uuid = buf.readUUID();
            String order = buf.readUtf(16);
            server.execute(() -> {
                if (!allowed(player)) {
                    return;
                }
                Boolean loaded = order(player, uuid, order);
                if (loaded != null) {
                    player.displayClientMessage(Component.translatable(loaded ? "msg.mca_vie.employee.order." + order
                            : "msg.mca_vie.employee.order_pending", nameOf(server, uuid, WorkRegistry.get(server).get(uuid))), true);
                }
                send(player);
            });
        });
        EmployeeChat.register();
    }

    // appelé par WorkManager.update (toutes les ~20 ticks) pour un PNJ chargé
    public static void update(VillagerEntityMCA villager, ServerLevel world, WorkRegistry.Record record) {
        MinecraftServer server = world.getServer();
        if (ORDER_FIRE.equals(record.pendingOrder)) {
            record.pendingOrder = null;
            record.order = null;
            WorkManager.setJob(villager, Job.NONE);
            record.employer = null;
            WorkRegistry.get(server).setDirty();
            return;
        }
        if (record.employer == null) {
            return;
        }
        ServerPlayer employer = server.getPlayerList().getPlayer(record.employer);
        if (record.pendingOrder != null) {
            String order = record.pendingOrder;
            record.pendingOrder = null;
            apply(villager, record, order, employer);
            WorkRegistry.get(server).setDirty();
        }
        if (employer != null && (record.pendingHearts != 0 || record.pendingMood != 0)) {
            if (record.pendingHearts != 0) {
                villager.getVillagerBrain().getMemoriesForPlayer(employer).modHearts(record.pendingHearts);
            }
            villager.getVillagerBrain().modifyMoodValue(record.pendingMood);
            record.pendingHearts = 0;
            record.pendingMood = 0;
            WorkRegistry.get(server).setDirty();
        }
        if (ORDER_HOME.equals(record.order)) {
            BlockPos center = record.center;
            MoveState state = villager.getVillagerBrain().getMoveState();
            if (center == null || state != MoveState.MOVE || villager.blockPosition().closerThan(center, HOME_REACHED)) {
                if (state == MoveState.MOVE) {
                    record.order = ORDER_WORK;
                }
            } else {
                villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(center, 0.6f, 2));
            }
        }
        updateAlerts(villager, world, record, employer);
        if ((world.getGameTime() / 20 + villager.getId()) % SNAPSHOT_INTERVAL == 0) {
            record.lastName = villager.getName().getString();
            record.lastPos = villager.blockPosition();
            record.lastDimension = world.dimension().identifier();
            record.lastHealth = villager.getHealth();
            record.lastMaxHealth = villager.getMaxHealth();
            record.lastStatus = statusOf(villager);
            record.lastSeen = server.overworld().getGameTime();
            record.look = look(villager);
            record.order = activeOrder(villager, record);
            EmployeeChat.snapshot(villager, record);
            WorkRegistry.get(server).setDirty();
        }
    }

    public static int alertsOf(VillagerEntityMCA villager) {
        int alerts = 0;
        if (villager.getHealth() < villager.getMaxHealth() * 0.5f) {
            alerts |= ALERT_HURT;
        }
        WorkStatus status = WorkManager.getStatus(villager);
        if (status == WorkStatus.INVENTORY_FULL) {
            alerts |= ALERT_FULL;
        } else if (status == WorkStatus.NO_TOOL) {
            alerts |= ALERT_TOOL;
        } else if (status == WorkStatus.NO_WORKSTATION) {
            alerts |= ALERT_WORKSTATION;
        }
        if (villager.getVillagerBrain().isPanicking() || villager.getLastHurtByMob() != null) {
            alerts |= ALERT_DANGER;
        }
        return alerts;
    }

    private static void updateAlerts(VillagerEntityMCA villager, ServerLevel world, WorkRegistry.Record record, @Nullable ServerPlayer employer) {
        int alerts = alertsOf(villager);
        int fresh = alerts & ~record.alerts;
        if (alerts != record.alerts) {
            record.alerts = alerts;
            WorkRegistry.get(world.getServer()).setDirty();
        }
        long now = world.getServer().overworld().getGameTime();
        if (fresh == 0 || employer == null || now < record.alertCooldown || !hasPhone(employer)) {
            return;
        }
        record.alertCooldown = now + ALERT_COOLDOWN;
        int bit = Integer.numberOfTrailingZeros(Integer.highestOneBit(fresh));
        PhoneServer.ring(employer, PhoneNetwork.NOTIFY_MESSAGE);
        employer.sendOverlayMessage(Component.translatable("msg.mca_vie.employee.alert." + ALERT_KEYS[bit], villager.getName()));
    }

    // appelé sur le thread serveur pour chaque paquet de l'appli
    static boolean allowed(ServerPlayer player) {
        if (!hasPhone(player)) {
            return false;
        }
        long now = player.level().getServer().getTickCount();
        long[] rate = RATE.computeIfAbsent(player, p -> new long[2]);
        if (now - rate[0] >= RATE_WINDOW) {
            rate[0] = now;
            rate[1] = 0;
        }
        return ++rate[1] <= RATE_MAX;
    }

    private static boolean hasPhone(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(PhoneServer.PHONE)) {
                return true;
            }
        }
        return false;
    }

    private static String activeOrder(VillagerEntityMCA villager, WorkRegistry.Record record) {
        return switch (villager.getVillagerBrain().getMoveState()) {
            case FOLLOW -> ORDER_FOLLOW;
            case STAY -> ORDER_STAY;
            default -> ORDER_HOME.equals(record.order) ? ORDER_HOME : ORDER_WORK;
        };
    }

    private static void apply(VillagerEntityMCA villager, WorkRegistry.Record record, String order, @Nullable ServerPlayer employer) {
        switch (order) {
            case ORDER_FOLLOW -> {
                if (employer == null) {
                    return;
                }
                villager.getVillagerBrain().setMoveState(MoveState.FOLLOW, employer);
            }
            case ORDER_STAY -> villager.getVillagerBrain().setMoveState(MoveState.STAY, null);
            case ORDER_HOME, ORDER_WORK -> {
                if (villager.getVillagerBrain().getCurrentJob() != Chore.NONE) {
                    villager.getVillagerBrain().abandonJob();
                }
                villager.getVillagerBrain().setMoveState(MoveState.MOVE, null);
                if (ORDER_HOME.equals(order) && record.center != null) {
                    villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(record.center, 0.6f, 2));
                }
            }
            default -> {
                return;
            }
        }
        record.order = order;
    }

    /**
     * Ordre à distance donné par l'employeur : true s'il est appliqué tout de suite, false s'il attend le
     * rechargement du PNJ, null si refusé (pas l'employeur, ordre inconnu).
     */
    @Nullable
    public static Boolean order(ServerPlayer player, UUID uuid, String order) {
        MinecraftServer server = player.level().getServer();
        WorkRegistry registry = WorkRegistry.get(server);
        WorkRegistry.Record record = registry.get(uuid);
        if (record == null || !player.getUUID().equals(record.employer) || record.job == Job.NONE) {
            return null;
        }
        if (!ORDER_FOLLOW.equals(order) && !ORDER_STAY.equals(order) && !ORDER_HOME.equals(order) && !ORDER_WORK.equals(order)) {
            return null;
        }
        VillagerEntityMCA villager = find(server, uuid);
        if (villager != null) {
            apply(villager, record, order, player);
            registry.setDirty();
            return true;
        }
        if (!ORDER_FIRE.equals(record.pendingOrder)) {
            record.pendingOrder = order;
            registry.setDirty();
        }
        return false;
    }

    public static void recordDeposit(VillagerEntityMCA villager, Item item, int count) {
        if (!(villager.level() instanceof ServerLevel world)) {
            return;
        }
        WorkRegistry.Record record = WorkManager.findRecord(villager);
        if (record == null) {
            return;
        }
        long day = today(world.getServer());
        if (record.reportDay != day) {
            record.reportDay = day;
            record.report = new CompoundTag();
        }
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        record.report.putInt(id, record.report.getIntOr(id, 0) + count);
        WorkRegistry.get(world.getServer()).setDirty();
    }

    public static long today(MinecraftServer server) {
        return server.overworld().getOverworldClockTime() / 24000L;
    }

    public static CompoundTag report(MinecraftServer server, WorkRegistry.Record record) {
        return record.reportDay == today(server) ? record.report : new CompoundTag();
    }

    // apparence compacte pour l'appli : données suivies par MCA (genre, génétique, vêtements, cheveux, traits…) + âge
    public static CompoundTag look(VillagerEntityMCA villager) {
        CompoundTag nbt = new CompoundTag();
        villager.getTypeDataManager().save(villager, nbt);
        nbt.putInt("Age", villager.getAge());
        return nbt;
    }

    static int statusOf(VillagerEntityMCA villager) {
        if (WorkManager.isWorking(villager)) {
            return WorkManager.getStatus(villager).ordinal();
        }
        WorkStatus status = WorkManager.getStatus(villager);
        return status == WorkStatus.NO_WORKSTATION || status == WorkStatus.INVENTORY_FULL ? status.ordinal() : -1;
    }

    @Nullable
    public static VillagerEntityMCA find(MinecraftServer server, UUID uuid) {
        for (ServerLevel world : server.getAllLevels()) {
            if (world.getEntity(uuid) instanceof VillagerEntityMCA villager && villager.isAlive()) {
                return villager;
            }
        }
        return null;
    }

    static boolean isDead(FamilyTree tree, UUID uuid) {
        return tree.getOrEmpty(uuid).map(FamilyTreeNode::isDeceased).orElse(false);
    }

    // mort et non ressuscité : ni réponse aux SMS ni place dans l'appli
    public static boolean isDead(MinecraftServer server, UUID uuid) {
        return find(server, uuid) == null && isDead(FamilyTree.get(server.overworld()), uuid);
    }

    private static void fire(ServerPlayer player, UUID uuid) {
        MinecraftServer server = player.level().getServer();
        WorkRegistry.Record record = WorkRegistry.get(server).get(uuid);
        if (record == null || !player.getUUID().equals(record.employer)) {
            return;
        }
        String name = dismiss(server, uuid, record);
        player.sendOverlayMessage(Component.translatable("job.mca_vie.fired", name));
    }

    // fin du contrat (licenciement ou démission) : immédiate si le PNJ est chargé, sinon à son rechargement
    public static String dismiss(MinecraftServer server, UUID uuid, WorkRegistry.Record record) {
        VillagerEntityMCA villager = find(server, uuid);
        String name = villager != null ? villager.getName().getString() : nameOf(server, uuid, record);
        if (villager != null) {
            WorkManager.setJob(villager, Job.NONE);
            record.pendingOrder = null;
        } else {
            record.pendingOrder = ORDER_FIRE;
        }
        record.order = null;
        record.employer = null;
        WorkRegistry.get(server).setDirty();
        return name;
    }

    public static String nameOf(MinecraftServer server, UUID uuid, WorkRegistry.Record record) {
        if (record.lastName != null) {
            String surname = com.maitreyoshi.mcavie.SurnameRegistry.get(server).get(uuid);
            if (surname == null) {
                return record.lastName;
            }
            for (String form : surname.split("/")) {
                String first = com.maitreyoshi.mcavie.SurnameText.firstName(record.lastName, form);
                if (!first.equals(record.lastName.trim())) {
                    return com.maitreyoshi.mcavie.SurnameText.withSurname(first, form);
                }
            }
            return record.lastName;
        }
        return FamilyTree.get(server.overworld()).getOrEmpty(uuid).map(FamilyTreeNode::getName).orElse("?");
    }

    private static String pendingOf(WorkRegistry.Record record) {
        return record.pendingOrder != null ? record.pendingOrder : "";
    }

    public static void send(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        long now = server.overworld().getGameTime();
        List<Employee> list = new ArrayList<>();
        FamilyTree tree = FamilyTree.get(server.overworld());
        for (Map.Entry<UUID, WorkRegistry.Record> entry : WorkRegistry.get(server).entries()) {
            WorkRegistry.Record record = entry.getValue();
            if (!player.getUUID().equals(record.employer) || record.job == Job.NONE || record.job == Job.RETIRED) {
                continue;
            }
            UUID uuid = entry.getKey();
            VillagerEntityMCA villager = find(server, uuid);
            // un mort ne figure plus dans la liste ; sa fiche reste pour le cas où il serait ressuscité
            if (villager == null && isDead(tree, uuid)) {
                continue;
            }
            if (villager != null) {
                list.add(new Employee(uuid, villager.getName().getString(), record.job.ordinal(), statusOf(villager), true,
                        villager.getHealth(), villager.getMaxHealth(), villager.blockPosition(),
                        villager.level().dimension().identifier().toString(), 0, look(villager),
                        alertsOf(villager), activeOrder(villager, record), pendingOf(record), report(server, record)));
            } else {
                list.add(new Employee(uuid, nameOf(server, uuid, record), record.job.ordinal(), record.lastStatus, false,
                        record.lastHealth, record.lastMaxHealth,
                        record.lastPos != null ? record.lastPos : record.center != null ? record.center : BlockPos.ZERO,
                        record.lastDimension != null ? record.lastDimension.toString() : "",
                        record.lastSeen > 0 ? Math.max(0, now - record.lastSeen) : -1, record.look,
                        record.alerts, record.order != null ? record.order : "", pendingOf(record), report(server, record)));
            }
        }
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(list.size());
        list.forEach(e -> e.write(buf));
        ServerPlayNetworking.send(player, LIST, buf);
    }
}
