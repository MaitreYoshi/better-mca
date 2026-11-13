package com.maitreyoshi.mcavie.compat.client;

import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public final class ClientPlayNetworking {
    private ClientPlayNetworking() {
    }

    public static void registerGlobalReceiver(ResourceLocation id, PlayChannelHandler handler) {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, id, (buf, context) -> {
            Minecraft client = Minecraft.getInstance();
            handler.receive(client, client.getConnection(), buf, null);
        });
    }

    public static void send(ResourceLocation id, FriendlyByteBuf buf) {
        NetworkManager.sendToServer(id, buf);
    }

    public static boolean canSend(ResourceLocation id) {
        return Minecraft.getInstance().getConnection() != null && NetworkManager.canServerReceive(id);
    }

    @FunctionalInterface
    public interface PlayChannelHandler {
        void receive(Minecraft client, ClientPacketListener handler, FriendlyByteBuf buf, Object responseSender);
    }
}
