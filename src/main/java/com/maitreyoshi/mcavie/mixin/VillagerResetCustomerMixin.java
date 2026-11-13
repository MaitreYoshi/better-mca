package com.maitreyoshi.mcavie.mixin;

import com.maitreyoshi.mcavie.village.VillageMarketExtras;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Retire l'offre rare du jour de marché à la fin de l'échange. */
@Mixin(Villager.class)
public abstract class VillagerResetCustomerMixin {
    @Inject(method = "stopTrading", at = @At("HEAD"))
    private void mcavie$clearMarketOffer(CallbackInfo ci) {
        VillageMarketExtras.clearRare((Villager) (Object) this);
    }
}
