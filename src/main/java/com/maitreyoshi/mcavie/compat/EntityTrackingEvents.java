package com.maitreyoshi.mcavie.compat;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.BiConsumer;

public final class EntityTrackingEvents {
    public static final Callbacks<BiConsumer<Entity, ServerPlayerEntity>> START_TRACKING = new Callbacks<>();

    private EntityTrackingEvents() {
    }
}
