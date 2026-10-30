package com.maitreyoshi.mcavie.work;

import com.maitreyoshi.mcavie.MCAVie;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.maitreyoshi.mcavie.village.VillageRights;
import net.mca.entity.VillagerEntityMCA;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageManager;
import net.minecraft.block.ChestBlock;
import net.minecraft.inventory.Inventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WorkNetwork {
    public static final Identifier SET_JOB = MCAVie.id("set_job");
    public static final Identifier PICK_DEPOT = MCAVie.id("pick_depot");
    public static final Identifier ZONE_HERE = MCAVie.id("zone_here");
    public static final Identifier JOB_INFO = MCAVie.id("job_info");

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
            UUID uuid = buf.readUuid();
            Job job = Job.byId(buf.readVarInt());
            if (!job.isSelectable()) {
                return;
            }
            server.execute(() -> findManaged(player, uuid).ifPresent(villager -> {
                WorkManager.setJob(villager, job);
                WorkRegistry.Record record = WorkManager.getRecord(villager);
                if (job == Job.NONE) {
                    record.employer = null;
                } else if (record.employer == null) {
                    record.employer = player.getUuid();
                }
                WorkRegistry.get(server).markDirty();
                player.sendMessage(job == Job.NONE
                        ? Text.translatable("job.mca_vie.fired", villager.getName())
                        : Text.translatable("job.mca_vie.hired", villager.getName(), job.getName()), true);
            }));
        });

        ServerPlayNetworking.registerGlobalReceiver(PICK_DEPOT, (server, player, handler, buf, responseSender) -> {
            UUID uuid = buf.readUuid();
            server.execute(() -> findManaged(player, uuid).ifPresent(villager -> {
                PENDING_DEPOTS.put(player.getUuid(), new PendingDepot(uuid, player.getWorld().getTime() + 600));
                player.sendMessage(Text.translatable("job.mca_vie.pick_depot", villager.getName()).formatted(Formatting.YELLOW), false);
            }));
        });

        ServerPlayNetworking.registerGlobalReceiver(ZONE_HERE, (server, player, handler, buf, responseSender) -> {
            UUID uuid = buf.readUuid();
            server.execute(() -> findManaged(player, uuid).ifPresent(villager -> {
                WorkRegistry.Record record = WorkManager.getRecord(villager);
                record.center = player.getBlockPos();
                WorkManager.dropDepotOutsideZone(record);
                WorkRegistry.get(server).markDirty();
                sendJobInfo(villager);
                player.sendMessage(Text.translatable("job.mca_vie.zone_set", villager.getName()), true);
            }));
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }
            PendingDepot pending = PENDING_DEPOTS.get(player.getUuid());
            if (pending == null) {
                return ActionResult.PASS;
            }
            BlockPos pos = hit.getBlockPos();
            boolean container = world.getBlockState(pos).getBlock() instanceof ChestBlock || world.getBlockEntity(pos) instanceof Inventory;
            if (!container || world.getTime() > pending.expiresAt()) {
                PENDING_DEPOTS.remove(player.getUuid());
                return ActionResult.PASS;
            }
            PENDING_DEPOTS.remove(player.getUuid());
            if (((ServerWorld) world).getEntity(pending.villager()) instanceof VillagerEntityMCA villager && canManage(serverPlayer, villager)) {
                if (!WorkManager.isInZone(villager, pos)) {
                    serverPlayer.sendMessage(Text.translatableWithFallback("job.mca_vie.depot_out_of_zone",
                            "Ce coffre est hors de la zone de travail de %s.", villager.getName()).formatted(Formatting.RED), false);
                    return ActionResult.SUCCESS;
                }
                WorkRegistry.Record record = WorkManager.getRecord(villager);
                record.depot = pos.toImmutable();
                record.depotDimension = world.getRegistryKey().getValue();
                WorkRegistry.get(serverPlayer.getServer()).markDirty();
                sendJobInfo(villager);
                serverPlayer.sendMessage(Text.translatable("job.mca_vie.depot_set", villager.getName()).formatted(Formatting.GREEN), false);
            }
            return ActionResult.SUCCESS;
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> PENDING_DEPOTS.remove(handler.player.getUuid())));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING_DEPOTS.clear());

        EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
            if (entity instanceof VillagerEntityMCA villager) {
                sendJobInfoTo(player, villager);
            }
        });
    }

    private static java.util.Optional<VillagerEntityMCA> findVillager(ServerPlayerEntity player, UUID uuid) {
        if (player.getServerWorld().getEntity(uuid) instanceof VillagerEntityMCA villager && villager.squaredDistanceTo(player) <= MAX_DISTANCE_SQ) {
            return java.util.Optional.of(villager);
        }
        return java.util.Optional.empty();
    }

    private static java.util.Optional<VillagerEntityMCA> findManaged(ServerPlayerEntity player, UUID uuid) {
        java.util.Optional<VillagerEntityMCA> villager = findVillager(player, uuid);
        if (villager.isPresent() && !canManage(player, villager.get())) {
            player.sendMessage(Text.translatable("job.mca_vie.denied", villager.get().getName()).formatted(Formatting.RED), true);
            return java.util.Optional.empty();
        }
        return villager;
    }

    public static boolean canManage(ServerPlayerEntity player, VillagerEntityMCA villager) {
        WorkRegistry.Record record = WorkManager.findRecord(villager);
        if (record == null || record.employer == null || record.employer.equals(player.getUuid()) || player.hasPermissionLevel(2)) {
            return true;
        }
        java.util.Optional<Village> home = villager.getResidency().getHomeVillage();
        java.util.Optional<Village> village = home.isPresent() ? home : VillageManager.get(player.getServerWorld()).findNearestVillage(villager);
        return village.isPresent() && VillageRights.personalRank(village.get(), player).isAtLeast(VillageRights.MANAGE_WORK);
    }

    public static void sendJobInfo(VillagerEntityMCA villager) {
        for (ServerPlayerEntity player : PlayerLookup.tracking(villager)) {
            sendJobInfoTo(player, villager);
        }
    }

    public static void sendJobInfoTo(ServerPlayerEntity player, VillagerEntityMCA villager) {
        if (!(villager.getWorld() instanceof ServerWorld world)) {
            return;
        }
        WorkRegistry.Record record = WorkRegistry.get(world.getServer()).get(villager.getUuid());
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(villager.getId());
        buf.writeVarInt(record == null ? 0 : record.job.ordinal());
        buf.writeBoolean(WorkManager.isWorking(villager));
        buf.writeVarInt(record == null ? 1 : record.level);
        buf.writeBoolean(record != null && record.depot != null);
        ServerPlayNetworking.send(player, JOB_INFO, buf);
    }
}
