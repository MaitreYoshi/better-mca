package com.maitreyoshi.mcavie.mixin;

import com.maitreyoshi.mcavie.life.LifeCycle;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Pregnancy;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Pregnancy.class, remap = false)
public abstract class PregnancyMixin {
    @Shadow
    @Final
    private VillagerEntityMCA mother;

    @Inject(method = "tryStartGestation", at = @At("HEAD"), cancellable = true)
    private void mcavie$menopause(CallbackInfoReturnable<Boolean> cir) {
        if (LifeCycle.isMenopausal(mother) || mcavie$sameSexPartner()) {
            cir.setReturnValue(false);
        }
    }

    @Unique
    private boolean mcavie$sameSexPartner() {
        // genres des entités (chargées, MCA l'exige pour la grossesse) : le genre stocké dans l'arbre peut être
        // faux chez les villageois d'avant l'addon, ce qui bloquait toute grossesse de ces couples
        return mother.getRelationships().getPartner()
                .filter(VillagerEntityMCA.class::isInstance)
                .map(p -> ((VillagerEntityMCA) p).getGenetics().getGender() == mother.getGenetics().getGender())
                .orElse(false);
    }
}