package com.maitreyoshi.mcavie.compat.client;

import dev.architectury.networking.NetworkManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public final class ClientPlayNetworking {
    private ClientPlayNetworking() {
    }

    public static void registerGlobalReceiver(Identifier id, PlayChannelHandler handler) {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, id, (buf, context) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            handler.receive(client, client.getNetworkHandler(), buf, null);
        });
    }

    public static void send(Identifier id, PacketByteBuf buf) {
        NetworkManager.sendToServer(id, buf);
    }

    public static boolean canSend(Identifier id) {
        return MinecraftClient.getInstance().getNetworkHandler() != null && NetworkManager.canServerReceive(id);
    }

    @FunctionalInterface
    public interface PlayChannelHandler {
        void receive(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, Object responseSender);
    }
}
