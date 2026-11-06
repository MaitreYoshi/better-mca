package com.maitreyoshi.mcavie.forge;

import com.maitreyoshi.mcavie.client.MCAVieClient;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

final class MCAVieForgeClient {
    private MCAVieForgeClient() {
    }

    static void register(IEventBus modBus) {
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(MCAVieClient::init));
    }
}
