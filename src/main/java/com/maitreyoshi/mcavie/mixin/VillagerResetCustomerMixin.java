package com.maitreyoshi.mcavie.mixin;

import com.maitreyoshi.mcavie.village.VillageMarketExtras;
import net.minecraft.entity.passive.VillagerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Retire l'offre rare du jour de marché à la fin de l'échange. */
@Mixin(VillagerEntity.class)
public abstract class VillagerResetCustomerMixin {
    @Inject(method = "resetCustomer", at = @At("HEAD"))
    private void mcavie$clearMarketOffer(CallbackInfo ci) {
        VillageMarketExtras.clearRare((VillagerEntity) (Object) this);
    }
}
