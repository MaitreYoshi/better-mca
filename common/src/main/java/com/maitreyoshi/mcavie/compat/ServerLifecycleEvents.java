package com.maitreyoshi.mcavie.compat;

import dev.architectury.event.events.common.LifecycleEvent;
import net.minecraft.server.MinecraftServer;

import java.util.function.Consumer;

public final class ServerLifecycleEvents {
    public static final Event SERVER_STARTED = listener -> LifecycleEvent.SERVER_STARTED.register(listener::accept);
    public static final Event SERVER_STOPPING = listener -> LifecycleEvent.SERVER_STOPPING.register(listener::accept);
    public static final Event SERVER_STOPPED = listener -> LifecycleEvent.SERVER_STOPPED.register(listener::accept);

    private ServerLifecycleEvents() {
    }

    @FunctionalInterface
    public interface Event {
        void register(Consumer<MinecraftServer> listener);
    }
}
