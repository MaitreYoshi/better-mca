package com.maitreyoshi.mcavie.mixin.path;

import com.maitreyoshi.mcavie.path.LadderPaths;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fait suivre aux PNJ MCA les nœuds d'échelle : montée en sautant contre l'échelle, nœud suivant une fois la hauteur atteinte. */
@Mixin(PathNavigation.class)
public abstract class EntityNavigationMixin {
    @Shadow
    protected Mob mob;

    @Shadow
    protected Path path;

    @Shadow
    protected double speedModifier;

    @Inject(method = "tick", at = @At("TAIL"))
    private void mcavie$holdLadderColumn(CallbackInfo ci) {
        if (mob instanceof VillagerEntityMCA && path != null && !path.isDone()) {
            LadderPaths.holdColumn(mob, path, speedModifier);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void mcavie$climbLadder(CallbackInfo ci) {
        if (mob instanceof VillagerEntityMCA && path != null && !path.isDone()) {
            LadderPaths.followLadder(mob, path);
        }
    }
}
