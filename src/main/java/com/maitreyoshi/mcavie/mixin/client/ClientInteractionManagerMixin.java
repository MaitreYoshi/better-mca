package com.maitreyoshi.mcavie.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.maitreyoshi.mcavie.social.client.ChatHeads;
import com.maitreyoshi.mcavie.village.client.PlanScreen;
import net.mca.client.gui.BlueprintScreen;
import net.mca.network.ClientInteractionManagerImpl;
import net.mca.network.s2c.VillagerMessage;
import net.minecraft.client.network.message.MessageHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientInteractionManagerImpl.class)
public abstract class ClientInteractionManagerMixin {
    @Redirect(method = "handleGuiRequest", at = @At(value = "NEW", target = "net/mca/client/gui/BlueprintScreen"), remap = false)
    private BlueprintScreen mcavie$planScreen() {
        return new PlanScreen();
    }

    @WrapOperation(method = "handleVillagerMessage", remap = false, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/message/MessageHandler;onGameMessage(Lnet/minecraft/text/Text;Z)V", remap = true))
    private void mcavie$villagerHead(MessageHandler handler, Text text, boolean overlay, Operation<Void> original, VillagerMessage message) {
        original.call(handler, ChatHeads.withHead(text, message.getUuid()), overlay);
    }
}
