package com.maitreyoshi.mcavie.client;

import com.maitreyoshi.mcavie.SurnameNetwork;
import com.maitreyoshi.mcavie.phone.client.PhoneClient;
import com.maitreyoshi.mcavie.tree.client.TreeClient;
import com.maitreyoshi.mcavie.village.VillageConfig;
import com.maitreyoshi.mcavie.village.VillageNetwork;
import com.maitreyoshi.mcavie.social.client.RelationsClient;
import com.maitreyoshi.mcavie.work.client.WorkClient;
import com.maitreyoshi.mcavie.compat.client.ClientPlayConnectionEvents;
import com.maitreyoshi.mcavie.compat.client.ClientPlayNetworking;
import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import dev.architectury.platform.Platform;
import net.minecraft.network.PacketByteBuf;

import java.util.UUID;

public final class MCAVieClient {
    private static String ownSurname = "";

    private MCAVieClient() {
    }

    public static void init() {
        dev.architectury.registry.client.rendering.RenderTypeRegistry.register(net.minecraft.client.render.RenderLayer.getCutout(),
                com.maitreyoshi.mcavie.work.block.WorkBlocks.all().toArray(new net.minecraft.block.Block[0]));
        CalendarHud.register();
        TreeClient.register();
        WorkClient.register();
        RelationsClient.register();
        PhoneClient.register();
        com.maitreyoshi.mcavie.life.client.TombstoneClient.register();
        com.maitreyoshi.mcavie.aging.client.AgingClient.register();
        ClientPlayNetworking.registerGlobalReceiver(VillageNetwork.CONFIG, (client, handler, buf, responseSender) -> {
            String json = buf.readString(1 << 20);
            client.execute(() -> VillageConfig.applyRemote(json));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> VillageConfig.load());

        if (Platform.isDevelopmentEnvironment()) {
            for (String screen : new String[]{"net.mca.client.gui.InteractScreen", "net.mca.client.gui.DestinyScreen", "net.mca.client.gui.BlueprintScreen",
                    "net.mca.network.ClientInteractionManagerImpl", "com.maitreyoshi.mcavie.village.client.PlanScreen"}) {
                try {
                    Class.forName(screen);
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(SurnameNetwork.OWN_SURNAME, (client, handler, buf, responseSender) -> {
            String surname = buf.readString();
            client.execute(() -> ownSurname = surname);
        });

        ClientPlayNetworking.registerGlobalReceiver(SurnameNetwork.HAPPINESS, (client, handler, buf, responseSender) -> {
            int entityId = buf.readVarInt();
            int happiness = buf.readByte();
            boolean sick = buf.readBoolean();
            client.execute(() -> HappinessDisplay.set(entityId, happiness, sick));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> HappinessDisplay.clear());
        ClientPlayNetworking.registerGlobalReceiver(com.maitreyoshi.mcavie.calendar.WorldCalendar.SEGMENTS_PACKET, (client, handler, buf, responseSender) -> {
            java.util.List<com.maitreyoshi.mcavie.calendar.WorldCalendar.Segment> segments = com.maitreyoshi.mcavie.calendar.WorldCalendar.readSegments(buf);
            client.execute(() -> com.maitreyoshi.mcavie.calendar.WorldCalendar.setClientSegments(segments));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> com.maitreyoshi.mcavie.calendar.WorldCalendar.resetClientSegments());
        com.maitreyoshi.mcavie.compat.client.ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> HappinessDisplay.remove(entity.getId()));

        ClientPlayNetworking.registerGlobalReceiver(SurnameNetwork.BIRTH_DAY_RESULT, (client, handler, buf, responseSender) -> {
            UUID villager = buf.readUuid();
            long birthDay = buf.readLong();
            client.execute(() -> {
                if (client.currentScreen instanceof BirthDayHolder holder && holder.mcavie$getVillagerUuid().equals(villager)) {
                    holder.mcavie$setBirthDay(birthDay);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SurnameNetwork.RANDOM_SURNAME_RESULT, (client, handler, buf, responseSender) -> {
            String surname = buf.readString();
            client.execute(() -> {
                if (client.currentScreen instanceof SurnameFieldHolder holder) {
                    holder.mcavie$setSurname(surname);
                }
            });
        });
    }

    public static void requestBirthDay(UUID villager) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(villager);
        ClientPlayNetworking.send(SurnameNetwork.BIRTH_DAY_REQUEST, buf);
    }

    public static String getOwnSurname() {
        return ownSurname;
    }

    public static void sendSurname(String surname) {
        ownSurname = surname;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(surname);
        ClientPlayNetworking.send(SurnameNetwork.SET_SURNAME, buf);
    }

    public static void requestRandomSurname() {
        ClientPlayNetworking.send(SurnameNetwork.RANDOM_SURNAME, PacketByteBufs.empty());
    }

    public static void requestBabySurname(int slot) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(slot);
        ClientPlayNetworking.send(SurnameNetwork.BABY_SURNAME, buf);
    }
}
