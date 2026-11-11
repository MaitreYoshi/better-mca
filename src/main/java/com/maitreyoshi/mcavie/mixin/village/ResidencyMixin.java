package com.maitreyoshi.mcavie.mixin.village;

import com.maitreyoshi.mcavie.village.BedClaims;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Residency;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** « Définir le lit » : le lit le plus proche du joueur, réservé exactement (voir BedClaims). */
@Mixin(value = Residency.class, remap = false)
public abstract class ResidencyMixin {
    @Shadow
    @Final
    private VillagerEntityMCA entity;

    @Inject(method = "setHome", at = @At("HEAD"), cancellable = true)
    private void mcavie$setHome(ServerPlayerEntity player, CallbackInfo ci) {
        BedClaims.setHome(entity, player);
        ci.cancel();
    }
}
