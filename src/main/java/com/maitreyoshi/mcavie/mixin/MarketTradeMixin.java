package com.maitreyoshi.mcavie.mixin;

import com.maitreyoshi.mcavie.village.VillageMarketExtras;
import net.conczin.mca.entity.VillagerEntityMCA;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.world.entity.npc.villager.Villager.class)
public abstract class MarketTradeMixin {
    @Inject(method = "updateSpecialPrices", at = @At("TAIL"))
    private void mcavie$marketDayOffers(net.minecraft.world.entity.player.Player player, CallbackInfo ci) {
        if ((Object) this instanceof VillagerEntityMCA villager) {
            VillageMarketExtras.prepareOffers(villager);
        }
    }
}
