package com.maitreyoshi.mcavie.mixin.village;

import net.mca.server.world.data.Village;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

@Mixin(value = Village.class, remap = false)
public interface VillageAccessor {
    @Accessor("residentHomes")
    Map<UUID, Long> mcavie$getResidentHomes();
}
