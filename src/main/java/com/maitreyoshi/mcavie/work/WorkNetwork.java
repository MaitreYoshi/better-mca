package com.maitreyoshi.mcavie.work;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.compat.ServerLifecycleEvents;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.InteractionEvent;
import com.maitreyoshi.mcavie.compat.ServerPlayConnectionEvents;
import com.maitreyoshi.mcavie.compat.EntityTrackingEvents;
import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import com.maitreyoshi.mcavie.compat.PlayerLookup;
import com.maitreyoshi.mcavie.compat.ServerPlayNetworking;
import com.maitreyoshi.mcavie.village.VillageRights;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WorkNetwork {
    public static final Identifier SET_JOB = MCAVie.id("set_job");
    public static final Identifier PICK_DEPOT = MCAVie.id("pick_depot");
    public static final Identifier ZONE_HERE = MCAVie.id("zone_here");
    public static final Identifier JOB_INFO = MCAVie.id("job_info");
    public static final Identifier SHOW_WORKSTATION = MCAVie.id("show_workstation");
    public static final Identifier JOB_REFUSED = MCAVie.id("job_refused");

    private static final double MAX_DISTANCE_SQ = 16 * 16;
    private static final Map<UUID, PendingDepot> PENDING_DEPOTS = new ConcurrentHashMap<>();

    private record PendingDepot(UUID villager, long expiresAt) {
    }

    private WorkNetwork() {
    }

    public static void register() {
        WorkManager.register();
        PlacedLogs.register();

        ServerPlayNetworking.registerGlobalReceiver(SET_JOB, (server, player, handler, buf, responseSender) -> {
            UUID uuid = buf.readUUID();
            Job job = Job.byId(buf.readVarInt());
            if (!job.isSelectable()) {
                return;
            }
            server.execute(() -> findManaged(player, uuid).ifPresent(villager -> {
                String refusal = job == Job.NONE ? null : WorkManager.jobRefusal(villager);
                if (refusal != null) {
                    // métier refusé (trop jeune ou trop vieux) : ni métier, ni employeur
                    FriendlyByteBuf refused = PacketByteBufs.create();
                    refused.writeComponent(Component.translatable(refusal, villager.getName()).withStyle(ChatFormatting.RED));
                    ServerPlayNetworking.send(player, JOB_REFUSED, refused);
                    return;
                }
                WorkManager.setJob(villager, job);
                WorkRegistry.Record record = WorkManager.getRecord(villager);
                if (job == Job.NONE) {
                    record.employer = null;
                } else if (record.employer == null) {
                    record.employer = player.getUUID();
                }
                WorkRegistry.get(server).setDirty();
                player.displayClientMessage(job == Job.NONE
                        ? Component.translatable("job.mca_vie.fired", villager.getName())
                        : Component.translatable("job.mca_vie.hired", villager.getName(), job.getName()), true);
            }));
        });

        ServerPlayNetworking.registerGlobalReceiver(PICK_DEPOT, (server, player, handler, buf, responseSender) -> {
            UUID uuid = buf.readUUID();
            server.execute(() -> findManaged(player, uuid).ifPresent(villager -> {
                PENDING_DEPOTS.put(player.getUUID(), new PendingDepot(uuid, player.level().getGameTime() + 600));
                player.displayClientMessage(Component.translatable("job.mca_vie.pick_depot", villager.getName()).withStyle(ChatFormatting.YELLOW), false);
            }));
        });

        ServerPlayNetworking.registerGlobalReceiver(ZONE_HERE, (server, player, handler, buf, responseSender) -> {
            UUID uuid = buf.readUUID();
            server.execute(() -> findManaged(player, uuid).ifPresent(villager -> {
                WorkRegistry.Record record = WorkManager.getRecord(villager);
                record.center = player.blockPosition();
                WorkManager.dropDepotOutsideZone(record);
                WorkRegistry.get(server).setDirty();
                sendJobInfo(villager);
                player.displayClientMessage(Component.translatable("job.mca_vie.zone_set", villager.getName()), true);
            }));
        });

        ServerPlayNetworking.registerGlobalReceiver(SHOW_WORKSTATION, (server, player, handler, buf, responseSender) -> {
            UUID uuid = buf.readUUID();
            server.execute(() -> findVillager(player, uuid).ifPresent(villager -> showWorkstation(player, villager)));
        });

        InteractionEvent.RIGHT_CLICK_BLOCK.register((player, hand, pos, face) -> {
            Level world = player.level();
            if (world.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
                return EventResult.pass();
            }
            PendingDepot pending = PENDING_DEPOTS.get(player.getUUID());
            if (pending == null) {
                return EventResult.pass();
            }
            boolean workstation = world.getBlockState(pos).getBlock() instanceof com.maitreyoshi.mcavie.work.block.WorkstationBlock;
            if (!workstation || world.getGameTime() > pending.expiresAt()) {
                PENDING_DEPOTS.remove(player.getUUID());
                return EventResult.pass();
            }
            PENDING_DEPOTS.remove(player.getUUID());
            if (((ServerLevel) world).getEntity(pending.villager()) instanceof VillagerEntityMCA villager && canManage(serverPlayer, villager)) {
                WorkRegistry.Record record = WorkManager.getRecord(villager);
                Job stationJob = ((com.maitreyoshi.mcavie.work.block.WorkstationBlock) world.getBlockState(pos).getBlock()).job();
                if (stationJob != record.job) {
                    serverPlayer.displayClientMessage(Component.translatable("job.mca_vie.wrong_workstation", villager.getName(), record.job.getName()).withStyle(ChatFormatting.RED), false);
                    return EventResult.interruptTrue();
                }
                Identifier dimension = world.dimension().location();
                if (WorkRegistry.get(serverPlayer.getServer()).isClaimed(dimension, pos, villager.getUUID())) {
                    serverPlayer.displayClientMessage(Component.translatable("job.mca_vie.workstation_taken").withStyle(ChatFormatting.RED), false);
                    return EventResult.interruptTrue();
                }
                record.depot = pos.immutable();
                record.depotDimension = dimension;
                record.center = record.depot;
                WorkRegistry.get(serverPlayer.getServer()).setDirty();
                sendJobInfo(villager);
                serverPlayer.displayClientMessage(Component.translatable("job.mca_vie.depot_set", villager.getName()).withStyle(ChatFormatting.GREEN), false);
            }
            return EventResult.interruptTrue();
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> PENDING_DEPOTS.remove(handler.player.getUUID())));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING_DEPOTS.clear());

        EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
            if (entity instanceof VillagerEntityMCA villager) {
                sendJobInfoTo(player, villager);
            }
        });
    }

    private static java.util.Optional<VillagerEntityMCA> findVillager(ServerPlayer player, UUID uuid) {
        if (player.serverLevel().getEntity(uuid) instanceof VillagerEntityMCA villager && villager.distanceToSqr(player) <= MAX_DISTANCE_SQ) {
            return java.util.Optional.of(villager);
        }
        return java.util.Optional.empty();
    }

    private static java.util.Optional<VillagerEntityMCA> findManaged(ServerPlayer player, UUID uuid) {
        java.util.Optional<VillagerEntityMCA> villager = findVillager(player, uuid);
        if (villager.isPresent() && !canManage(player, villager.get())) {
            player.displayClientMessage(Component.translatable("job.mca_vie.denied", villager.get().getName()).withStyle(ChatFormatting.RED), true);
            return java.util.Optional.empty();
        }
        return villager;
    }

    private static void showWorkstation(ServerPlayer player, VillagerEntityMCA villager) {
        WorkRegistry.Record record = WorkManager.findRecord(villager);
        if (record == null || record.depot == null) {
            player.displayClientMessage(Component.translatable("job.mca_vie.show_none", villager.getName()).withStyle(ChatFormatting.GOLD), true);
            return;
        }
        ServerLevel world = player.serverLevel();
        BlockPos depot = record.depot;
        for (int i = 0; i < 12; i++) {
            world.sendParticles(player, net.minecraft.core.particles.ParticleTypes.END_ROD, true,
                    depot.getX() + 0.5, depot.getY() + 1.0 + i * 0.4, depot.getZ() + 0.5, 2, 0.1, 0.1, 0.1, 0.0);
        }
        BlockPos center = record.center != null ? record.center : depot;
        int radius = record.radius;
        int points = Math.max(48, radius * 8);
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            double x = center.getX() + 0.5 + Math.cos(angle) * radius;
            double z = center.getZ() + 0.5 + Math.sin(angle) * radius;
            int y = world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
            world.sendParticles(player, net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, true, x, y + 0.2, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        player.displayClientMessage(Component.translatable("job.mca_vie.shown", villager.getName(),
                depot.getX(), depot.getY(), depot.getZ()).withStyle(ChatFormatting.GREEN), false);
    }

    public static boolean canManage(ServerPlayer player, VillagerEntityMCA villager) {
        WorkRegistry.Record record = WorkManager.findRecord(villager);
        if (record == null || record.employer == null || record.employer.equals(player.getUUID()) || player.hasPermissions(2)) {
            return true;
        }
        java.util.Optional<Village> home = villager.getResidency().getHomeVillage();
        java.util.Optional<Village> village = home.isPresent() ? home : VillageManager.get(player.serverLevel()).findNearestVillage(villager);
        return village.isPresent() && VillageRights.personalRank(village.get(), player).isAtLeast(VillageRights.MANAGE_WORK);
    }

    public static void sendJobInfo(VillagerEntityMCA villager) {
        for (ServerPlayer player : PlayerLookup.tracking(villager)) {
            sendJobInfoTo(player, villager);
        }
    }

    public static void sendJobInfoTo(ServerPlayer player, VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel world)) {
            return;
        }
        WorkRegistry.Record record = WorkRegistry.get(world.getServer()).get(villager.getUUID());
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(villager.getId());
        buf.writeVarInt(record == null ? 0 : record.job.ordinal());
        buf.writeBoolean(WorkManager.isWorking(villager));
        buf.writeVarInt(record == null ? 1 : record.level);
        buf.writeBoolean(record != null && record.depot != null);
        buf.writeVarInt(WorkManager.getStatus(villager).ordinal());
        buf.writeVarInt(record == null ? 0 : record.experience);
        buf.writeVarInt(WorkManager.getGesture(villager).ordinal());
        ServerPlayNetworking.send(player, JOB_INFO, buf);
    }
}
