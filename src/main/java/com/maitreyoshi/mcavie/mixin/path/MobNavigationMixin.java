package com.maitreyoshi.mcavie.mixin.path;

import com.maitreyoshi.mcavie.path.LadderPaths;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Un PNJ MCA sur une échelle peut recalculer son chemin (sinon il reste figé dessus, sans trajet). */
@Mixin(MobNavigation.class)
public abstract class MobNavigationMixin extends EntityNavigation {
    private MobNavigationMixin(MobEntity entity, World world) {
        super(entity, world);
    }

    @Inject(method = "isAtValidPosition", at = @At("RETURN"), cancellable = true)
    private void mcavie$validOnLadder(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && entity instanceof VillagerEntityMCA && LadderPaths.inShaft(entity)) {
            cir.setReturnValue(true);
        }
    }
}
