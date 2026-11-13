package com.maitreyoshi.mcavie.compat;

import com.maitreyoshi.mcavie.mixin.compat.EntityTrackerAccessor;
import com.maitreyoshi.mcavie.mixin.compat.ThreadedAnvilChunkStorageAccessor;
import java.util.Collection;
import java.util.List;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;

public final class PlayerLookup {
    private PlayerLookup() {
    }

    public static Collection<ServerPlayer> tracking(Entity entity) {
        if (!(entity.level().getChunkSource() instanceof ServerChunkCache manager)) {
            return List.of();
        }
        Object tracker = ((ThreadedAnvilChunkStorageAccessor) manager.chunkMap).mcavie$getEntityTrackers().get(entity.getId());
        if (tracker == null) {
            return List.of();
        }
        return ((EntityTrackerAccessor) tracker).mcavie$getListeners().stream().map(ServerPlayerConnection::getPlayer).toList();
    }
}
