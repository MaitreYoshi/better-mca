package com.maitreyoshi.mcavie.compat;

import com.maitreyoshi.mcavie.mixin.compat.EntityTrackerAccessor;
import com.maitreyoshi.mcavie.mixin.compat.ThreadedAnvilChunkStorageAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.EntityTrackingListener;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkManager;

import java.util.Collection;
import java.util.List;

public final class PlayerLookup {
    private PlayerLookup() {
    }

    public static Collection<ServerPlayerEntity> tracking(Entity entity) {
        if (!(entity.getWorld().getChunkManager() instanceof ServerChunkManager manager)) {
            return List.of();
        }
        Object tracker = ((ThreadedAnvilChunkStorageAccessor) manager.threadedAnvilChunkStorage).mcavie$getEntityTrackers().get(entity.getId());
        if (tracker == null) {
            return List.of();
        }
        return ((EntityTrackerAccessor) tracker).mcavie$getListeners().stream().map(EntityTrackingListener::getPlayer).toList();
    }
}
