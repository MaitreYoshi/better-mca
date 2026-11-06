package com.maitreyoshi.mcavie.mixin.village;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.maitreyoshi.mcavie.village.VillageConfig;
import com.maitreyoshi.mcavie.village.VillageDecrees;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.villageComponents.VillageProcreationManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = VillageProcreationManager.class, remap = false)
public abstract class VillageProcreationManagerMixin {
    @Shadow
    @Final
    private Village village;

    @ModifyExpressionValue(method = "procreate", at = @At(value = "FIELD", target = "Lnet/mca/Config;villagerProcreationChancePerMinute:F"))
    private float mcavie$birthBonus(float chance) {
        return VillageDecrees.birthBonus(village) ? chance * (float) (1 + VillageConfig.get().birthBonusChance) : chance;
    }
}
