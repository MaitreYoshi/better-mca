package com.maitreyoshi.mcavie.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.maitreyoshi.mcavie.social.client.ChatHeads;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatHudMixin {
    @Unique
    private static GuiGraphicsExtractor mcavie$context;

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V", at = @At("HEAD"))
    private void mcavie$begin(GuiGraphicsExtractor context, Font font, int a, int b, int c, ChatComponent.DisplayMode mode, boolean d, CallbackInfo ci) {
        mcavie$context = context;
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V", at = @At("RETURN"))
    private void mcavie$end(GuiGraphicsExtractor context, Font font, int a, int b, int c, ChatComponent.DisplayMode mode, boolean d, CallbackInfo ci) {
        mcavie$context = null;
    }

    @WrapOperation(method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z"))
    private boolean mcavie$drawVillagerHead(ChatComponent.ChatGraphicsAccess access, int y, float opacity, FormattedCharSequence text, Operation<Boolean> original) {
        boolean result = original.call(access, y, opacity, text);
        if (mcavie$context != null) {
            ChatHeads.draw(mcavie$context, text, 0, y, Math.round(opacity * 255) << 24 | 0xFFFFFF);
        }
        return result;
    }

    @ModifyConstant(method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V", constant = @Constant(intValue = 40))
    private int mcavie$raiseChat(int bottom) {
        return bottom + ChatHeads.CHAT_RAISE;
    }
}
