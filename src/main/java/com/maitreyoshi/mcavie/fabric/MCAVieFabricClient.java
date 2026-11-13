package com.maitreyoshi.mcavie.fabric;

import com.maitreyoshi.mcavie.client.MCAVieClient;
import com.maitreyoshi.mcavie.book.client.MemorialModelRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;

public final class MCAVieFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PictureInPictureRendererRegistry.register(context -> MemorialModelRenderer.create());
        MCAVieClient.init();
    }
}
