package com.maitreyoshi.mcavie.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.maitreyoshi.mcavie.social.client.ChatHeads;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)I"))
    private int mcavie$drawVillagerHead(DrawContext context, TextRenderer textRenderer, OrderedText text, int x, int y, int color, Operation<Integer> original) {
        int end = original.call(context, textRenderer, text, x, y, color);
        ChatHeads.draw(context, text, x, y, color);
        return end;
    }

    // Remonte le chat au-dessus de la barre d'armure (rendu, survol et clics restent alignés).
    @ModifyConstant(method = "render", constant = @Constant(intValue = 40))
    private int mcavie$raiseChat(int bottom) {
        return bottom + ChatHeads.CHAT_RAISE;
    }

    @ModifyConstant(method = {"toChatLineY", "mouseClicked"}, constant = @Constant(doubleValue = 40.0))
    private double mcavie$raiseChatMouse(double bottom) {
        return bottom + ChatHeads.CHAT_RAISE;
    }
}
