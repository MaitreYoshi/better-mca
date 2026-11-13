package com.maitreyoshi.mcavie.compat;

import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class EntityTrackingEvents {
    public static final Callbacks<BiConsumer<Entity, ServerPlayer>> START_TRACKING = new Callbacks<>();

    private EntityTrackingEvents() {
    }
}
