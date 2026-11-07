package com.maitreyoshi.mcavie.compat.client;

import com.maitreyoshi.mcavie.compat.Callbacks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;

import java.util.function.BiConsumer;

public final class ClientEntityEvents {
    public static final Callbacks<BiConsumer<Entity, ClientWorld>> ENTITY_UNLOAD = new Callbacks<>();

    private ClientEntityEvents() {
    }
}
