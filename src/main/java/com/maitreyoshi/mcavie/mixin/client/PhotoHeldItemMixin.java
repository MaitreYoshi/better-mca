package com.maitreyoshi.mcavie.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.maitreyoshi.mcavie.phone.PhotoItem;
import com.maitreyoshi.mcavie.phone.PhoneServer;
import com.maitreyoshi.mcavie.phone.client.PhotoItemRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class PhotoHeldItemMixin {
    @Unique
    private boolean mcavie$photo;

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void mcavie$rememberPhoto(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item,
                                      float equipProgress, MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        mcavie$photo = item.getItem() instanceof PhotoItem;
    }

    @ModifyExpressionValue(method = "renderFirstPersonItem", at = @At(value = "FIELD", target = "Lnet/minecraft/item/Items;FILLED_MAP:Lnet/minecraft/item/Item;", opcode = Opcodes.GETSTATIC))
    private Item mcavie$photoAsMap(Item original) {
        return mcavie$photo ? PhoneServer.PHOTO : original;
    }

    @Inject(method = "renderFirstPersonMap", at = @At("HEAD"), cancellable = true)
    private void mcavie$renderPhoto(MatrixStack matrices, VertexConsumerProvider consumers, int light, ItemStack stack, CallbackInfo ci) {
        if (stack.getItem() instanceof PhotoItem) {
            PhotoItemRenderer.renderMap(matrices, consumers, light, stack);
            ci.cancel();
        }
    }
}
