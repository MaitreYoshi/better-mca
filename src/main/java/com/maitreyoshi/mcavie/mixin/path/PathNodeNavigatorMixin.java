package com.maitreyoshi.mcavie.mixin.path;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.maitreyoshi.mcavie.path.LadderPaths;
import net.conczin.mca.entity.ai.navigation.MCAWalkNodeEvaluator;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathFinder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Ajoute aux PNJ MCA les trajets par échelle, que le pathfinding terrestre ignore. */
@Mixin(PathFinder.class)
public abstract class PathNodeNavigatorMixin {
    @WrapOperation(method = "findPath(Lnet/minecraft/util/profiling/ProfilerFiller;Lnet/minecraft/world/level/pathfinder/Node;Ljava/util/Map;FIF)Lnet/minecraft/world/level/pathfinder/Path;", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/pathfinder/NodeEvaluator;getNeighbors([Lnet/minecraft/world/level/pathfinder/Node;Lnet/minecraft/world/level/pathfinder/Node;)I"))
    private int mcavie$ladderSuccessors(NodeEvaluator maker, Node[] successors, Node node, Operation<Integer> original) {
        int count = original.call(maker, successors, node);
        if (maker instanceof MCAWalkNodeEvaluator) {
            count = LadderPaths.addSuccessors((PathNodeMakerAccessor) maker, successors, node, count);
        }
        return count;
    }
}
