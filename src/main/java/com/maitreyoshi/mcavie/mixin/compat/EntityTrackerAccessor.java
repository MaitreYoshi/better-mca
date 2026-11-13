package com.maitreyoshi.mcavie.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;
import net.minecraft.server.network.ServerPlayerConnection;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public interface EntityTrackerAccessor {
    @Accessor("seenBy")
    Set<ServerPlayerConnection> mcavie$getListeners();
}
