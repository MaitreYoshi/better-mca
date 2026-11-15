package com.maitreyoshi.mcavie.compat.client;

import dev.architectury.event.events.client.ClientGuiEvent;
import net.minecraft.client.gui.DrawContext;

@FunctionalInterface
public interface HudRenderCallback {
    Event EVENT = listener -> ClientGuiEvent.RENDER_HUD.register(listener::onHudRender);

    void onHudRender(DrawContext drawContext, float tickDelta);

    @FunctionalInterface
    interface Event {
        void register(HudRenderCallback listener);
    }
}
