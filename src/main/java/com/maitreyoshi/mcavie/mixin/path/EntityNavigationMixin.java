package com.maitreyoshi.mcavie.mixin.path;

import com.maitreyoshi.mcavie.path.LadderPaths;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fait suivre aux PNJ MCA les nœuds d'échelle : montée en sautant contre l'échelle, nœud suivant une fois la hauteur atteinte. */
@Mixin(EntityNavigation.class)
public abstract class EntityNavigationMixin {
    @Shadow
    protected MobEntity entity;

    @Shadow
    protected Path currentPath;

    @Shadow
    protected double speed;

    @Inject(method = "tick", at = @At("TAIL"))
    private void mcavie$holdLadderColumn(CallbackInfo ci) {
        if (entity instanceof VillagerEntityMCA && currentPath != null && !currentPath.isFinished()) {
            LadderPaths.holdColumn(entity, currentPath, speed);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void mcavie$climbLadder(CallbackInfo ci) {
        if (entity instanceof VillagerEntityMCA && currentPath != null && !currentPath.isFinished()) {
            LadderPaths.followLadder(entity, currentPath);
        }
    }
}
