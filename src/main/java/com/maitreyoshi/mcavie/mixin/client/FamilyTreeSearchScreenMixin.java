package com.maitreyoshi.mcavie.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import net.mca.client.gui.FamilyTreeSearchScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(FamilyTreeSearchScreen.class)
public abstract class FamilyTreeSearchScreenMixin {
    @ModifyArg(method = "renderVillagers", remap = false, index = 1,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V", remap = true))
    private Component mcavie$withName(Component parents, @Local FamilyTreeSearchScreen.Entry entry) {
        return Component.literal(entry.name()).append(Component.literal(" · ").append(parents).withStyle(ChatFormatting.GRAY));
    }
}
