package com.maitreyoshi.mcavie.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.maitreyoshi.mcavie.phone.client.CameraMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class CameraMouseMixin {
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void mcavie$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (CameraMode.active() && client.currentScreen == null && window == client.getWindow().getHandle()) {
            CameraMode.scroll(Math.signum(vertical));
            ci.cancel();
        }
    }

    @WrapOperation(method = "updateMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"))
    private void mcavie$zoomLook(ClientPlayerEntity player, double x, double y, Operation<Void> original) {
        double zoom = CameraMode.zoom();
        original.call(player, x / zoom, y / zoom);
    }
}
