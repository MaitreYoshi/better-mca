package com.maitreyoshi.mcavie.compat;

import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public final class ServerEntityEvents {
    public static final Callbacks<BiConsumer<Entity, ServerLevel>> ENTITY_UNLOAD = new Callbacks<>();

    private ServerEntityEvents() {
    }
}
