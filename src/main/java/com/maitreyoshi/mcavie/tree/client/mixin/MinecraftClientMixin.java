package com.maitreyoshi.mcavie.tree.client.mixin;

import com.maitreyoshi.mcavie.tree.TreeProtocol;
import com.maitreyoshi.mcavie.tree.client.TreeClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.mca.client.gui.FamilyTreeScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void mcavie$replaceFamilyTree(Screen screen, CallbackInfo ci) {
        if (screen instanceof FamilyTreeScreen tree && ClientPlayNetworking.canSend(TreeProtocol.REQUEST)) {
            ci.cancel();
            MinecraftClient client = (MinecraftClient) (Object) this;
            TreeClient.open(((FamilyTreeScreenAccessor) tree).mcavie$getFocusedEntityId(), client.currentScreen);
        }
    }
}
