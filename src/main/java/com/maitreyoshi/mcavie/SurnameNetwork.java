package com.maitreyoshi.mcavie;

import com.maitreyoshi.mcavie.life.LifeRegistry;
import com.maitreyoshi.mcavie.life.Wellbeing;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.relationship.Gender;
import net.mca.server.world.data.PlayerSaveData;
import net.minecraft.entity.Entity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.UUID;

public final class SurnameNetwork {
    public static final Identifier SET_SURNAME = MCAVie.id("set_surname");
    public static final Identifier RANDOM_SURNAME = MCAVie.id("random_surname");
    public static final Identifier OWN_SURNAME = MCAVie.id("own_surname");
    public static final Identifier RANDOM_SURNAME_RESULT = MCAVie.id("random_surname_result");
    public static final Identifier BIRTH_DAY_REQUEST = MCAVie.id("birth_day_request");
    public static final Identifier BIRTH_DAY_RESULT = MCAVie.id("birth_day_result");
    public static final Identifier HAPPINESS = MCAVie.id("happiness");

    private SurnameNetwork() {
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(SET_SURNAME, (server, player, handler, buf, responseSender) -> {
            String surname = SurnameText.sanitize(buf.readString(64));
            server.execute(() -> {
                if (!surname.isEmpty()) {
                    Surnames.setPlayerSurname(player, surname, false);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RANDOM_SURNAME, (server, player, handler, buf, responseSender) ->
                server.execute(() -> {
                    PacketByteBuf out = PacketByteBufs.create();
                    PlayerSaveData data = PlayerSaveData.get(player);
                    Gender gender = data.getEntityData() != null ? data.getGender() : Gender.MALE;
                    out.writeString(SurnameText.display(Surnames.randomSurnameFor(player), gender));
                    ServerPlayNetworking.send(player, RANDOM_SURNAME_RESULT, out);
                }));

        ServerPlayNetworking.registerGlobalReceiver(BIRTH_DAY_REQUEST, (server, player, handler, buf, responseSender) -> {
            UUID villager = buf.readUuid();
            server.execute(() -> {
                Long birth = LifeRegistry.get(server).getBirthDay(villager);
                if (birth != null) {
                    PacketByteBuf out = PacketByteBufs.create();
                    out.writeUuid(villager);
                    out.writeLong(birth);
                    ServerPlayNetworking.send(player, BIRTH_DAY_RESULT, out);
                }
            });
        });

        EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
            if (entity instanceof VillagerEntityMCA villager) {
                Wellbeing.sendTo(villager, player);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            String surname = SurnameRegistry.get(server).get(handler.player.getUuid());
            sendOwnSurname(handler.player, surname == null ? "" : surname);
        });
    }

    public static void sendHappiness(Entity villager, int happiness, boolean sick) {
        for (ServerPlayerEntity player : PlayerLookup.tracking(villager)) {
            sendHappinessTo(player, villager, happiness, sick);
        }
    }

    public static void sendHappinessTo(ServerPlayerEntity player, Entity villager, int happiness, boolean sick) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(villager.getId());
        buf.writeByte(happiness);
        buf.writeBoolean(sick);
        ServerPlayNetworking.send(player, HAPPINESS, buf);
    }

    public static void sendOwnSurname(ServerPlayerEntity player, String surname) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(surname);
        ServerPlayNetworking.send(player, OWN_SURNAME, buf);
    }
}
