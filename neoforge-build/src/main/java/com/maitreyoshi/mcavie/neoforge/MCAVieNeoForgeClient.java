package com.maitreyoshi.mcavie.neoforge;

import com.maitreyoshi.mcavie.book.client.MemorialModelRenderer;
import com.maitreyoshi.mcavie.client.MCAVieClient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;

final class MCAVieNeoForgeClient {
    private MCAVieNeoForgeClient() {
    }

    static void init(IEventBus modBus) {
        modBus.addListener(RegisterPictureInPictureRenderersEvent.class,
                event -> event.register(MemorialModelRenderer.State.class, MemorialModelRenderer::create));
        // Les types de blocs ne sont disponibles qu'après l'enregistrement des registres.
        java.util.List<Runnable> late = new java.util.ArrayList<>();
        MCAVieClient.AFTER_REGISTRIES = late::add;
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(() -> late.forEach(Runnable::run)));
        MCAVieClient.init();
    }
}