package com.maitreyoshi.mcavie.mixin.village;

import com.maitreyoshi.mcavie.village.VillageEconomy;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VillagerEntityMCA.class, remap = false)
public abstract class VillagerEntityMCATradeMixin {
    @Inject(method = "copiedPrepareOffersFor", at = @At("TAIL"))
    private void mcavie$marketDiscount(PlayerEntity player, CallbackInfo ci) {
        VillageEconomy.applyMarketDiscount((VillagerEntityMCA) (Object) this, player);
    }
}
