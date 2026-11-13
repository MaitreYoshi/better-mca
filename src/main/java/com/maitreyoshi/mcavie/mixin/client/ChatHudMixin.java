package com.maitreyoshi.mcavie.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.maitreyoshi.mcavie.social.client.ChatHeads;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(ChatComponent.class)
public abstract class ChatHudMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I"))
    private int mcavie$drawVillagerHead(GuiGraphics context, Font textRenderer, FormattedCharSequence text, int x, int y, int color, Operation<Integer> original) {
        int end = original.call(context, textRenderer, text, x, y, color);
        ChatHeads.draw(context, text, x, y, color);
        return end;
    }

    // Remonte le chat au-dessus de la barre d'armure (rendu, survol et clics restent alignés).
    @ModifyConstant(method = "render", constant = @Constant(intValue = 40))
    private int mcavie$raiseChat(int bottom) {
        return bottom + ChatHeads.CHAT_RAISE;
    }

    @ModifyConstant(method = {"screenToChatY", "handleChatQueueClicked"}, constant = @Constant(doubleValue = 40.0))
    private double mcavie$raiseChatMouse(double bottom) {
        return bottom + ChatHeads.CHAT_RAISE;
    }
}
