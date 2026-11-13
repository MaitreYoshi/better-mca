package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.life.client.GhostRenderer;
import net.mca.client.render.layer.VillagerLayer;
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
}
