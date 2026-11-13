package com.maitreyoshi.mcavie.compat;

import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import java.util.function.Consumer;

public final class ServerTickEvents {
    public static final Event<MinecraftServer> END_SERVER_TICK = listener -> TickEvent.SERVER_POST.register(listener::accept);
    public static final Event<ServerLevel> END_WORLD_TICK = listener -> TickEvent.SERVER_LEVEL_POST.register(listener::accept);

    private ServerTickEvents() {
    }

    @FunctionalInterface
    public interface Event<T> {
        void register(Consumer<T> listener);
    }
}
