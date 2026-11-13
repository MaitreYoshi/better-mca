package com.maitreyoshi.mcavie.mixin.path;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(NodeEvaluator.class)
public interface PathNodeMakerAccessor {
    @Accessor("level")
    PathNavigationRegion mcavie$world();

    @Accessor("mob")
    Mob mcavie$entity();

    @Invoker("getNode")
    Node mcavie$getNode(int x, int y, int z);
}
