package com.maitreyoshi.mcavie.compat;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class ServerPlayNetworking {
    private ServerPlayNetworking() {
    }

    public static void registerGlobalReceiver(Identifier id, PlayChannelHandler handler) {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, id, (buf, context) -> {
            if (context.getPlayer() instanceof ServerPlayerEntity player) {
                handler.receive(player.server, player, player.networkHandler, buf, new PacketSender(player));
            }
        });
    }

    public static void send(ServerPlayerEntity player, Identifier id, PacketByteBuf buf) {
        NetworkManager.sendToPlayer(player, id, buf);
    }

    @FunctionalInterface
    public interface PlayChannelHandler {
        void receive(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender);
    }
}
