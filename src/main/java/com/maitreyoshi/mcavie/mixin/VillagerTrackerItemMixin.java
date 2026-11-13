package com.maitreyoshi.mcavie.mixin;

import net.conczin.mca.item.VillagerTrackerItem;
import net.conczin.mca.registry.DataComponentsMCA;
import net.conczin.mca.server.world.data.VillagerTrackerManager;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(VillagerTrackerItem.class)
public abstract class VillagerTrackerItemMixin {
    @Inject(method = "inventoryTick", at = @At("HEAD"))
    private void mcavie$trackLoadedTarget(ItemStack stack, ServerLevel world, Entity holder, EquipmentSlot slot, CallbackInfo ci) {
        if (world.getGameTime() % 20 != 0) {
            return;
        }
        UUID target = stack.get(DataComponentsMCA.TRACKER_UUID);
        if (target == null) {
            return;
        }
        for (ServerLevel candidate : world.getServer().getAllLevels()) {
            Entity entity = candidate.getEntity(target);
            if (entity != null) {
                stack.set(DataComponentsMCA.TRACKER_POS, GlobalPos.of(candidate.dimension(), entity.blockPosition()));
                VillagerTrackerManager.get(candidate).set(entity);
                return;
            }
        }
    }
}
