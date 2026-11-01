package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.village.client.PlanScreen;
import net.mca.client.gui.BlueprintScreen;
import net.mca.network.ClientInteractionManagerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Le Plan de MCA est remplacé par celui de Better MCA (même canal de données, sous-classe de BlueprintScreen). */
@Mixin(ClientInteractionManagerImpl.class)
public abstract class ClientInteractionManagerMixin {
    @Redirect(method = "handleGuiRequest", at = @At(value = "NEW", target = "net/mca/client/gui/BlueprintScreen"), remap = false)
    private BlueprintScreen mcavie$planScreen() {
        return new PlanScreen();
    }
}
