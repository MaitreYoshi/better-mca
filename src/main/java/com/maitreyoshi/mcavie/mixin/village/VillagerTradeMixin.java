package com.maitreyoshi.mcavie.mixin.village;

import com.maitreyoshi.mcavie.village.VillageEconomy;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerEntity.class)
public abstract class VillagerTradeMixin {
    @Inject(method = "prepareOffersFor", at = @At("TAIL"))
    private void mcavie$marketDiscount(PlayerEntity player, CallbackInfo ci) {
        if ((Object) this instanceof VillagerEntityMCA villager) {
            VillageEconomy.applyMarketDiscount(villager, player);
        }
    }
}
