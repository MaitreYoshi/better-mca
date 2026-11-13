package com.maitreyoshi.mcavie.mixin.path;

import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.entity.ai.pathing.PathNodeMaker;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.chunk.ChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PathNodeMaker.class)
public interface PathNodeMakerAccessor {
    @Accessor("cachedWorld")
    ChunkCache mcavie$world();

    @Accessor("entity")
    MobEntity mcavie$entity();

    @Invoker("getNode")
    PathNode mcavie$getNode(int x, int y, int z);
}
