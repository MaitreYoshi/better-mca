package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.life.client.GhostRenderer;
import net.conczin.mca.client.render.layer.VillagerLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Les couches MCA (peau, vêtements, cheveux) d'un fantôme sont dessinées en translucide. */
@Mixin(value = VillagerLayer.class, remap = false)
public abstract class GhostVillagerLayerMixin {
    @Inject(method = "isTranslucent", at = @At("HEAD"), cancellable = true)
    private void mcavie$ghost(CallbackInfoReturnable<Boolean> cir) {
        if (GhostRenderer.active) {
            cir.setReturnValue(true);
        }
    }

    @org.spongepowered.asm.mixin.injection.ModifyArg(method = "renderModel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"),
            index = 6)
    private int mcavie$paleColor(int color) {
        return GhostRenderer.active ? GhostRenderer.pale(color) : color;
    }
}
