package com.maitreyoshi.mcavie.compat.client;

import dev.architectury.event.events.client.ClientGuiEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;

@FunctionalInterface
public interface HudRenderCallback {
    Event EVENT = listener -> ClientGuiEvent.RENDER_HUD.register((graphics, delta) -> listener.onHudRender(graphics, delta.getGameTimeDeltaPartialTick(false)));

    void onHudRender(GuiGraphicsExtractor drawContext, float tickDelta);

    @FunctionalInterface
    interface Event {
        void register(HudRenderCallback listener);
    }
}
