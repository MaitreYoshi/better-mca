package com.maitreyoshi.mcavie.tree.client.mixin;

import com.maitreyoshi.mcavie.tree.TreeProtocol;
import com.maitreyoshi.mcavie.tree.client.TreeClient;
import com.maitreyoshi.mcavie.compat.client.ClientPlayNetworking;
import net.conczin.mca.client.gui.FamilyTreeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void mcavie$replaceFamilyTree(Screen screen, CallbackInfo ci) {
        if (screen instanceof FamilyTreeScreen tree && ClientPlayNetworking.canSend(TreeProtocol.REQUEST)) {
            ci.cancel();
            Minecraft client = (Minecraft) (Object) this;
            TreeClient.open(((FamilyTreeScreenAccessor) tree).mcavie$getFocusedEntityId(), client.screen);
        }
    }
}
