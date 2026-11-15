package com.maitreyoshi.mcavie.compat.client;

import dev.architectury.event.events.client.ClientPlayerEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;

import java.util.function.BiConsumer;

public final class ClientPlayConnectionEvents {
    public static final Event DISCONNECT = listener -> ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> {
        MinecraftClient client = MinecraftClient.getInstance();
        listener.accept(client.getNetworkHandler(), client);
    });

    private ClientPlayConnectionEvents() {
    }

    @FunctionalInterface
    public interface Event {
        void register(BiConsumer<ClientPlayNetworkHandler, MinecraftClient> listener);
    }
}
