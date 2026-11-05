package com.maitreyoshi.mcavie.mixin;

import com.maitreyoshi.mcavie.village.VillageMarketExtras;
import net.mca.entity.VillagerEntityMCA;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerEntityMCA.class)
public abstract class MarketTradeMixin {
    @Inject(method = "copiedPrepareOffersFor", at = @At("TAIL"), remap = false)
    private void mcavie$marketDayOffers(net.minecraft.entity.player.PlayerEntity player, CallbackInfo ci) {
        VillageMarketExtras.prepareOffers((VillagerEntityMCA) (Object) this);
    }
}
