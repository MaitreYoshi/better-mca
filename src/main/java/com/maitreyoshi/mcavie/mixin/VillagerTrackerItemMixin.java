package com.maitreyoshi.mcavie.mixin;

import net.mca.item.VillagerTrackerItem;
import net.mca.server.world.data.VillagerTrackerManager;
import net.mca.util.NbtHelper;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(VillagerTrackerItem.class)
public abstract class VillagerTrackerItemMixin {
    @Inject(method = "inventoryTick", at = @At("TAIL"))
    private void mcavie$trackLoadedTarget(ItemStack stack, Level world, Entity holder, int slot, boolean selected, CallbackInfo ci) {
        if (!(world instanceof ServerLevel serverWorld) || world.getGameTime() % 20 != 0) {
            return;
        }
        CompoundTag nbt = stack.getTag();
        if (nbt == null || !nbt.hasUUID("targetUUID")) {
            return;
        }
        UUID target = nbt.getUUID("targetUUID");
        for (ServerLevel candidate : serverWorld.getServer().getAllLevels()) {
            Entity entity = candidate.getEntity(target);
            if (entity != null) {
                nbt.put("position", NbtHelper.encodeGlobalPosition(GlobalPos.of(candidate.dimension(), entity.blockPosition())));
                VillagerTrackerManager.get(candidate).set(entity);
                return;
            }
        }
    }
}
