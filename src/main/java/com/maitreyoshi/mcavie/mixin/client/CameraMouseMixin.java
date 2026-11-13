package com.maitreyoshi.mcavie.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.maitreyoshi.mcavie.phone.client.CameraMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class CameraMouseMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void mcavie$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (CameraMode.active() && client.gui.screen() == null && window == client.getWindow().handle()) {
            CameraMode.scroll(Math.signum(vertical));
            ci.cancel();
        }
    }

    @WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void mcavie$zoomLook(LocalPlayer player, double x, double y, Operation<Void> original) {
        double zoom = CameraMode.zoom();
        original.call(player, x / zoom, y / zoom);
    }
}
