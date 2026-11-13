package com.maitreyoshi.mcavie.compat.client;

import com.maitreyoshi.mcavie.compat.RawPayload;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

public final class ClientPlayNetworking {
    private ClientPlayNetworking() {
    }

    public static void registerGlobalReceiver(Identifier id, PlayChannelHandler handler) {
        RawPayload.registerClient(id, (buf, context) -> {
            Minecraft client = Minecraft.getInstance();
            handler.receive(client, client.getConnection(), buf, null);
        });
    }

    public static void send(Identifier id, FriendlyByteBuf buf) {
        NetworkManager.sendToServer(RawPayload.of(RawPayload.C2S, id, buf));
    }

    public static boolean canSend(Identifier id) {
        return Minecraft.getInstance().getConnection() != null && NetworkManager.canServerReceive(RawPayload.C2S);
    }

    @FunctionalInterface
    public interface PlayChannelHandler {
        void receive(Minecraft client, ClientPacketListener handler, FriendlyByteBuf buf, Object responseSender);
    }
}
