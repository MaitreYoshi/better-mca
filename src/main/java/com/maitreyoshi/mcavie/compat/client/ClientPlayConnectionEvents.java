package com.maitreyoshi.mcavie.compat.client;

import dev.architectury.event.events.client.ClientPlayerEvent;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

public final class ClientPlayConnectionEvents {
    public static final Event DISCONNECT = listener -> ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> {
        Minecraft client = Minecraft.getInstance();
        listener.accept(client.getConnection(), client);
    });

    private ClientPlayConnectionEvents() {
    }

    @FunctionalInterface
    public interface Event {
        void register(BiConsumer<ClientPacketListener, Minecraft> listener);
    }
}
