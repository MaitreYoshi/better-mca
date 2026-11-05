package com.maitreyoshi.mcavie.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import net.mca.client.gui.FamilyTreeSearchScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(FamilyTreeSearchScreen.class)
public abstract class FamilyTreeSearchScreenMixin {
    @ModifyArg(method = "renderVillagers", remap = false, index = 1,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawCenteredTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V", remap = true))
    private Text mcavie$withName(Text parents, @Local FamilyTreeSearchScreen.Entry entry) {
        return Text.literal(entry.name()).append(Text.literal(" · ").append(parents).formatted(Formatting.GRAY));
    }
}
