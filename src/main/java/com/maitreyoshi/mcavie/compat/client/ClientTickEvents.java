package com.maitreyoshi.mcavie.compat.client;

import dev.architectury.event.events.client.ClientTickEvent;
import net.minecraft.client.MinecraftClient;

import java.util.function.Consumer;

public final class ClientTickEvents {
    public static final Event START_CLIENT_TICK = listener -> ClientTickEvent.CLIENT_PRE.register(listener::accept);
    public static final Event END_CLIENT_TICK = listener -> ClientTickEvent.CLIENT_POST.register(listener::accept);

    private ClientTickEvents() {
    }

    @FunctionalInterface
    public interface Event {
        void register(Consumer<MinecraftClient> listener);
    }
}
