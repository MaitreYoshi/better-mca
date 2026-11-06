package com.maitreyoshi.mcavie.fabric;

import com.maitreyoshi.mcavie.client.MCAVieClient;
import net.fabricmc.api.ClientModInitializer;

public final class MCAVieFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MCAVieClient.init();
    }
}
