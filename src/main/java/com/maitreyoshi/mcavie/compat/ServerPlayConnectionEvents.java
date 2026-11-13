package com.maitreyoshi.mcavie.compat;

import dev.architectury.event.events.common.PlayerEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

public final class ServerPlayConnectionEvents {
    public static final Event<Join> JOIN = listener -> PlayerEvent.PLAYER_JOIN.register(player ->
            listener.onPlayReady(player.connection, new PacketSender(player), player.server));
    public static final Event<Disconnect> DISCONNECT = listener -> PlayerEvent.PLAYER_QUIT.register(player ->
            listener.onPlayDisconnect(player.connection, player.server));

    private ServerPlayConnectionEvents() {
    }

    @FunctionalInterface
    public interface Event<T> {
        void register(T listener);
    }

    @FunctionalInterface
    public interface Join {
        void onPlayReady(ServerGamePacketListenerImpl handler, PacketSender sender, MinecraftServer server);
    }

    @FunctionalInterface
    public interface Disconnect {
        void onPlayDisconnect(ServerGamePacketListenerImpl handler, MinecraftServer server);
    }
}
