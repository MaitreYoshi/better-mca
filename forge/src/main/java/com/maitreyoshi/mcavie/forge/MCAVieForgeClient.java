package com.maitreyoshi.mcavie.forge;

import com.maitreyoshi.mcavie.client.MCAVieClient;
import com.maitreyoshi.mcavie.phone.PhoneServer;
import com.maitreyoshi.mcavie.phone.client.PhotoFrameRenderer;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

final class MCAVieForgeClient {
    private MCAVieForgeClient() {
    }

    static void register(IEventBus modBus) {
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(MCAVieClient::init));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
                event.registerEntityRenderer(PhoneServer.PHOTO_FRAME, PhotoFrameRenderer::new));
    }
}
