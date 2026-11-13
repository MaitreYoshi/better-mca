package com.maitreyoshi.mcavie.compat.client;

import com.maitreyoshi.mcavie.compat.Callbacks;
import java.util.function.BiConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

public final class ClientEntityEvents {
    public static final Callbacks<BiConsumer<Entity, ClientLevel>> ENTITY_UNLOAD = new Callbacks<>();

    private ClientEntityEvents() {
    }
}
