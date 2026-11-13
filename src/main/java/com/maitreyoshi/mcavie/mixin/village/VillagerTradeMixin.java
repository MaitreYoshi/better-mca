package com.maitreyoshi.mcavie.mixin.village;

import com.maitreyoshi.mcavie.village.VillageEconomy;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerTradeMixin {
    @Inject(method = "updateSpecialPrices", at = @At("TAIL"))
    private void mcavie$marketDiscount(Player player, CallbackInfo ci) {
        if ((Object) this instanceof VillagerEntityMCA villager) {
            VillageEconomy.applyMarketDiscount(villager, player);
        }
    }
}
