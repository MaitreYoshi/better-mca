package com.maitreyoshi.mcavie.neoforge;

import com.maitreyoshi.mcavie.book.client.MemorialModelRenderer;
import com.maitreyoshi.mcavie.client.MCAVieClient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;

final class MCAVieNeoForgeClient {
    private MCAVieNeoForgeClient() {
    }

    static void init(IEventBus modBus) {
        modBus.addListener(RegisterPictureInPictureRenderersEvent.class,
                event -> event.register(MemorialModelRenderer.State.class, MemorialModelRenderer::create));
        MCAVieClient.init();
    }
}