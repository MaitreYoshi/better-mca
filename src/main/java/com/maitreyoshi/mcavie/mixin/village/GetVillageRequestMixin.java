package com.maitreyoshi.mcavie.mixin.village;

import com.maitreyoshi.mcavie.village.VillageState;
import net.conczin.mca.network.c2s.GetVillageRequest;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marque les habitants morts avant l'envoi du village, pour la tête de mort du plan. */
@Mixin(value = GetVillageRequest.class, remap = false)
public abstract class GetVillageRequestMixin {
    @Inject(method = "receive", at = @At("HEAD"))
    private void mcavie$markDead(ServerPlayer player, CallbackInfo ci) {
        Village.findNearest(player).ifPresent(village -> {
            VillageState state = VillageState.of(village);
            state.deadResidents.clear();
            FamilyTree tree = FamilyTree.get(player.level());
            for (java.util.UUID uuid : village.getResidentNames().keySet()) {
                if (tree.getOrEmpty(uuid).map(FamilyTreeNode::isDeceased).orElse(false)) {
                    state.deadResidents.add(uuid);
                }
            }
        });
    }
}
