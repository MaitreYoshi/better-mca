package com.maitreyoshi.mcavie.mixin;

import net.mca.item.VillagerTrackerItem;
import net.mca.server.world.data.VillagerTrackerManager;
import net.mca.util.NbtHelper;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * MCA ne connaît la position que des villageois à qui on a déjà parlé, et seulement toutes les 10 secondes :
 * pour les autres le traqueur restait muet. Si la cible est chargée (n'importe quelle dimension), on prend sa
 * position réelle chaque seconde et on la mémorise pour quand elle sera hors de portée.
 */
@Mixin(VillagerTrackerItem.class)
public abstract class VillagerTrackerItemMixin {
    @Inject(method = "inventoryTick", at = @At("TAIL"))
    private void mcavie$trackLoadedTarget(ItemStack stack, World world, Entity holder, int slot, boolean selected, CallbackInfo ci) {
        if (!(world instanceof ServerWorld serverWorld) || world.getTime() % 20 != 0) {
            return;
        }
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.containsUuid("targetUUID")) {
            return;
        }
        UUID target = nbt.getUuid("targetUUID");
        for (ServerWorld candidate : serverWorld.getServer().getWorlds()) {
            Entity entity = candidate.getEntity(target);
            if (entity != null) {
                nbt.put("position", NbtHelper.encodeGlobalPosition(GlobalPos.create(candidate.getRegistryKey(), entity.getBlockPos())));
                VillagerTrackerManager.get(candidate).set(entity);
                return;
            }
        }
    }
}
