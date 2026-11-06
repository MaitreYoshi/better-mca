package com.maitreyoshi.mcavie.compat;

import dev.architectury.event.events.common.LifecycleEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

import java.util.function.BiConsumer;

public final class ServerWorldEvents {
    public static final Event LOAD = listener -> LifecycleEvent.SERVER_LEVEL_LOAD.register(world -> listener.accept(world.getServer(), world));

    private ServerWorldEvents() {
    }

    @FunctionalInterface
    public interface Event {
        void register(BiConsumer<MinecraftServer, ServerWorld> listener);
    }
}
