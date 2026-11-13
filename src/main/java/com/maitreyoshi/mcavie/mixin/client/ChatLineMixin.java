package com.maitreyoshi.mcavie.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.maitreyoshi.mcavie.social.client.ChatHeads;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Dessine la tête du villageois devant chaque ligne du tchat (le dessin des lignes se fait dans cette classe anonyme). */
@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$1")
public abstract class ChatLineMixin {
    @WrapOperation(method = "accept(Lnet/minecraft/client/multiplayer/chat/GuiMessage$Line;IF)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z"))
    private boolean mcavie$drawVillagerHead(ChatComponent.ChatGraphicsAccess access, int y, float opacity, FormattedCharSequence text, Operation<Boolean> original) {
        boolean result = original.call(access, y, opacity, text);
        if (ChatHeads.context != null) {
            ChatHeads.draw(ChatHeads.context, text, 0, y, Math.round(opacity * 255) << 24 | 0xFFFFFF);
        }
        return result;
    }
}