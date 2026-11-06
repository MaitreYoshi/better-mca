package com.maitreyoshi.mcavie.compat;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;

import java.util.function.BiConsumer;

public final class ServerEntityEvents {
    public static final Callbacks<BiConsumer<Entity, ServerWorld>> ENTITY_UNLOAD = new Callbacks<>();

    private ServerEntityEvents() {
    }
}
