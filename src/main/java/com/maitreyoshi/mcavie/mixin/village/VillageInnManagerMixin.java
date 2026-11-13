package com.maitreyoshi.mcavie.mixin.village;

import com.maitreyoshi.mcavie.Surnames;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.server.world.data.villageComponents.VillageInnManager;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Le voyageur reçoit son nom de famille avant l'annonce d'arrivée à l'auberge : le message affiche prénom + nom.
@Mixin(value = VillageInnManager.class, remap = false)
public abstract class VillageInnManagerMixin {
    @Inject(method = "spawnInnVillager", at = @At("RETURN"))
    private void mcavie$fullName(CallbackInfoReturnable<VillagerEntityMCA> cir) {
        VillagerEntityMCA adventurer = cir.getReturnValue();
        if (adventurer == null) {
            return;
        }
        Surnames.update(adventurer);
        String full = (adventurer.getCustomName() == null ? "" : adventurer.getCustomName().getString());
        if (full != null && !full.isBlank() && (adventurer.getCustomName() == null || !full.equals(adventurer.getCustomName().getString()))) {
            adventurer.setCustomName(Component.literal(full));
        }
    }
}
