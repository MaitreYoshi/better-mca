package com.maitreyoshi.mcavie.mixin.path;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.maitreyoshi.mcavie.path.LadderPaths;
import net.mca.entity.ai.pathfinder.VillagerLandPathNodeMaker;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.entity.ai.pathing.PathNodeMaker;
import net.minecraft.entity.ai.pathing.PathNodeNavigator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Ajoute aux PNJ MCA les trajets par échelle, que le pathfinding terrestre ignore. */
@Mixin(PathNodeNavigator.class)
public abstract class PathNodeNavigatorMixin {
    @WrapOperation(method = "findPathToAny(Lnet/minecraft/util/profiler/Profiler;Lnet/minecraft/entity/ai/pathing/PathNode;Ljava/util/Map;FIF)Lnet/minecraft/entity/ai/pathing/Path;", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/ai/pathing/PathNodeMaker;getSuccessors([Lnet/minecraft/entity/ai/pathing/PathNode;Lnet/minecraft/entity/ai/pathing/PathNode;)I"))
    private int mcavie$ladderSuccessors(PathNodeMaker maker, PathNode[] successors, PathNode node, Operation<Integer> original) {
        int count = original.call(maker, successors, node);
        if (maker instanceof VillagerLandPathNodeMaker) {
            count = LadderPaths.addSuccessors((PathNodeMakerAccessor) maker, successors, node, count);
        }
        return count;
    }
}
