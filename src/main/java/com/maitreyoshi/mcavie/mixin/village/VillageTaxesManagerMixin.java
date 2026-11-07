package com.maitreyoshi.mcavie.mixin.village;

import net.mca.server.world.data.villageComponents.VillageTaxesManager;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VillageTaxesManager.class, remap = false)
public abstract class VillageTaxesManagerMixin {
    @Inject(method = "taxes", at = @At("HEAD"), cancellable = true)
    private void mcavie$disableMcaTaxes(ServerWorld world, CallbackInfo ci) {
        ci.cancel();
    }
}
