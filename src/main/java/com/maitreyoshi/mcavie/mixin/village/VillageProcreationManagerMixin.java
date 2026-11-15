package com.maitreyoshi.mcavie.mixin.village;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.maitreyoshi.mcavie.village.VillageConfig;
import com.maitreyoshi.mcavie.village.VillageDecrees;
import com.maitreyoshi.mcavie.life.LifeCycle;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.relationship.Gender;
import net.mca.entity.ai.relationship.RelationshipState;
import net.mca.server.world.data.Village;
import java.util.List;
import java.util.stream.Collectors;
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

    // MCA tire n'importe quel résident (hommes, enfants, célibataires...) : avec 36 habitants, une femme mariée
    // n'avait qu'environ une chance sur 700 par minute. On ne tire que parmi les femmes qui peuvent concevoir.
    @ModifyExpressionValue(method = "procreate", at = @At(value = "INVOKE", target = "Lnet/mca/server/world/data/Village;getResidents"))
    private List<VillagerEntityMCA> mcavie$onlyFertileWives(List<VillagerEntityMCA> residents) {
        return residents.stream()
                .filter(v -> v.getGenetics().getGender() == Gender.FEMALE)
                .filter(v -> v.getRelationships().getFamilyEntry().getRelationshipState() == RelationshipState.MARRIED_TO_VILLAGER)
                .filter(v -> !v.getRelationships().getPregnancy().isPregnant())
                .filter(v -> v.getRelationships().getPartner().filter(VillagerEntityMCA.class::isInstance).isPresent())
                .filter(v -> !LifeCycle.isMenopausal(v))
                .collect(Collectors.toList());
    }
}
