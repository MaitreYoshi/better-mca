package com.maitreyoshi.mcavie.social;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.social.SocialRegistry.Link;
import com.maitreyoshi.mcavie.work.WorkRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.relationship.Gender;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SocialNetwork {
    public static final Identifier RELATIONS_REQUEST = MCAVie.id("relations_request");
    public static final Identifier RELATIONS = MCAVie.id("relations");

    public static final int TAG_NONE = 0;
    public static final int TAG_SPOUSE = 1;
    public static final int TAG_FAMILY = 2;
    public static final int TAG_HOUSE = 3;
    public static final int TAG_COLLEAGUE = 4;

    private static final double MAX_DISTANCE_SQ = 64 * 64;
    private static final long REQUEST_COOLDOWN = 10;
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();

    private SocialNetwork() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(RELATIONS_REQUEST, (server, player, handler, buf, responseSender) -> {
            UUID uuid = buf.readUuid();
            server.execute(() -> {
                long time = player.getServerWorld().getTime();
                Long last = LAST_REQUEST.get(player.getUuid());
                if (last != null && time - last >= 0 && time - last < REQUEST_COOLDOWN) {
                    return;
                }
                LAST_REQUEST.put(player.getUuid(), time);
                if (player.getServerWorld().getEntity(uuid) instanceof VillagerEntityMCA villager && villager.squaredDistanceTo(player) <= MAX_DISTANCE_SQ) {
                    send(player, villager);
                }
            });
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> {
            LAST_REQUEST.remove(handler.player.getUuid());
            SocialChat.forgetPlayer(handler.player.getUuid());
        }));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            LAST_REQUEST.clear();
            SocialChat.clear();
        });
    }

    private static void send(ServerPlayerEntity player, VillagerEntityMCA villager) {
        ServerWorld world = player.getServerWorld();
        Map<UUID, Link> links = SocialRegistry.get(world.getServer()).links(villager.getUuid());
        FamilyTree tree = FamilyTree.get(world);
        Optional<FamilyTreeNode> node = tree.getOrEmpty(villager.getUuid());
        Optional<Social.House> house = Social.house(villager);
        WorkRegistry work = WorkRegistry.get(world.getServer());
        WorkRegistry.Record ownWork = work.get(villager.getUuid());

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(villager.getUuid());
        buf.writeVarLong(WorldCalendar.now(world).dayIndex());
        buf.writeVarInt(links.size());
        links.forEach((other, link) -> {
            buf.writeUuid(other);
            buf.writeString(Social.displayName(world, other));
            buf.writeBoolean(tree.getOrEmpty(other).map(n -> n.gender() == Gender.FEMALE).orElse(false));
            buf.writeVarInt(link.score);
            buf.writeVarLong(link.lastSeen);
            buf.writeVarInt(tag(world, node, house, ownWork, work, other, link));
            buf.writeVarInt(world.getEntity(other) instanceof VillagerEntityMCA loaded ? loaded.getId() : -1);
        });
        ServerPlayNetworking.send(player, RELATIONS, buf);
    }

    private static int tag(ServerWorld world, Optional<FamilyTreeNode> node, Optional<Social.House> house,
                           WorkRegistry.Record ownWork, WorkRegistry work, UUID other, Link link) {
        if (node.isPresent() && other.equals(node.get().partner())) {
            return TAG_SPOUSE;
        }
        if (link.family) {
            return TAG_FAMILY;
        }
        if (house.isPresent() && Social.livesIn(world, house.get(), other)) {
            return TAG_HOUSE;
        }
        if (Social.areColleagues(ownWork, work.get(other))) {
            return TAG_COLLEAGUE;
        }
        return TAG_NONE;
    }
}
