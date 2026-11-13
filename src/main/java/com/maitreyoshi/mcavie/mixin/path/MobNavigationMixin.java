package com.maitreyoshi.mcavie.mixin.path;

import com.maitreyoshi.mcavie.path.LadderPaths;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Un PNJ MCA sur une échelle peut recalculer son chemin (sinon il reste figé dessus, sans trajet). */
@Mixin(GroundPathNavigation.class)
public abstract class MobNavigationMixin extends PathNavigation {
    private MobNavigationMixin(Mob entity, Level world) {
        super(entity, world);
    }

    @Inject(method = "canUpdatePath", at = @At("RETURN"), cancellable = true)
    private void mcavie$validOnLadder(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && mob instanceof VillagerEntityMCA && LadderPaths.inShaft(mob)) {
            cir.setReturnValue(true);
        }
    }
}
