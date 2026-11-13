package com.maitreyoshi.mcavie.compat;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

public final class ServerPlayNetworking {
    private ServerPlayNetworking() {
    }

    public static void registerGlobalReceiver(Identifier id, PlayChannelHandler handler) {
        RawPayload.initServer();
        RawPayload.SERVER.put(id, (buf, context) -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                handler.receive(player.level().getServer(), player, player.connection, buf, new PacketSender(player));
            }
        });
    }

    public static void send(ServerPlayer player, Identifier id, FriendlyByteBuf buf) {
        NetworkManager.sendToPlayer(player, RawPayload.of(RawPayload.S2C, id, buf));
    }

    @FunctionalInterface
    public interface PlayChannelHandler {
        void receive(MinecraftServer server, ServerPlayer player, ServerGamePacketListenerImpl handler, FriendlyByteBuf buf, PacketSender responseSender);
    }
}
