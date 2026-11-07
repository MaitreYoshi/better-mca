package com.maitreyoshi.mcavie.compat;

import dev.architectury.event.events.common.PlayerEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;

public final class ServerPlayConnectionEvents {
    public static final Event<Join> JOIN = listener -> PlayerEvent.PLAYER_JOIN.register(player ->
            listener.onPlayReady(player.networkHandler, new PacketSender(player), player.server));
    public static final Event<Disconnect> DISCONNECT = listener -> PlayerEvent.PLAYER_QUIT.register(player ->
            listener.onPlayDisconnect(player.networkHandler, player.server));

    private ServerPlayConnectionEvents() {
    }

    @FunctionalInterface
    public interface Event<T> {
        void register(T listener);
    }

    @FunctionalInterface
    public interface Join {
        void onPlayReady(ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server);
    }

    @FunctionalInterface
    public interface Disconnect {
        void onPlayDisconnect(ServerPlayNetworkHandler handler, MinecraftServer server);
    }
}
