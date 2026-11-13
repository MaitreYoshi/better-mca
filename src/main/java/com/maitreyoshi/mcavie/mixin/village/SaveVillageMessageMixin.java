package com.maitreyoshi.mcavie.mixin.village;

import com.maitreyoshi.mcavie.village.VillageRights;
import net.conczin.mca.network.c2s.SaveVillageMessage;
import net.conczin.mca.resources.Rank;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SaveVillageMessage.class, remap = false)
public abstract class SaveVillageMessageMixin {
    @Shadow
    @Final
    private int id;
    @Shadow
    @Final
    private float taxes;
    @Shadow
    @Final
    private float populationThreshold;
    @Shadow
    @Final
    private float marriageThreshold;

    @Inject(method = "receive", at = @At("HEAD"), cancellable = true)
    private void mcavie$checkRights(ServerPlayer player, CallbackInfo ci) {
        ci.cancel();
        VillageManager.get(player.level()).getOrEmpty(id).ifPresent(village -> {
            Rank rank = VillageRights.personalRank(village, player);
            if (rank.isAtLeast(VillageRights.SET_TAXES)) {
                village.setTaxes(VillageRights.clampTaxes(village, taxes));
            }
            if (rank.isAtLeast(VillageRights.SET_BIRTHS)) {
                village.setPopulationThreshold(Math.max(0, Math.min(1, populationThreshold)));
            }
            if (rank.isAtLeast(VillageRights.SET_MARRIAGES)) {
                village.setMarriageThreshold(Math.max(0, Math.min(1, marriageThreshold)));
            }
            village.markDirty();
        });
    }
}
