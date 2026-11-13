package com.maitreyoshi.mcavie.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.maitreyoshi.mcavie.phone.PhotoItem;
import com.maitreyoshi.mcavie.phone.PhoneServer;
import com.maitreyoshi.mcavie.phone.client.CameraMode;
import com.maitreyoshi.mcavie.phone.client.PhotoItemRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class PhotoHeldItemMixin {
    @Unique
    private boolean mcavie$photo;

    // Iris (shaders) appelle ce rendu directement, sans passer par GameRenderer.renderHand.
    @Inject(method = "renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/player/LocalPlayer;I)V", at = @At("HEAD"), cancellable = true)
    private void mcavie$hideInCamera(float tickDelta, PoseStack matrices, MultiBufferSource.BufferSource consumers, LocalPlayer player, int light, CallbackInfo ci) {
        if (CameraMode.active()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void mcavie$rememberPhoto(AbstractClientPlayer player, float tickDelta, float pitch, InteractionHand hand, float swingProgress, ItemStack item,
                                      float equipProgress, PoseStack matrices, MultiBufferSource consumers, int light, CallbackInfo ci) {
        mcavie$photo = item.getItem() instanceof PhotoItem;
    }

    @ModifyExpressionValue(method = "renderArmWithItem", at = @At(value = "FIELD", target = "Lnet/minecraft/world/item/Items;FILLED_MAP:Lnet/minecraft/world/item/Item;", opcode = Opcodes.GETSTATIC))
    private Item mcavie$photoAsMap(Item original) {
        return mcavie$photo ? PhoneServer.PHOTO : original;
    }

    @Inject(method = "renderMap", at = @At("HEAD"), cancellable = true)
    private void mcavie$renderPhoto(PoseStack matrices, MultiBufferSource consumers, int light, ItemStack stack, CallbackInfo ci) {
        if (stack.getItem() instanceof PhotoItem) {
            PhotoItemRenderer.renderMap(matrices, consumers, light, stack);
            ci.cancel();
        }
    }
}
