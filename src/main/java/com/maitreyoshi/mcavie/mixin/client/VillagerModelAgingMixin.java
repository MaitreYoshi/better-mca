package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.aging.client.AgingClient;
import net.conczin.mca.client.model.VillagerEntityBaseModelMCA;
import net.conczin.mca.client.render.VillagerRenderState;
import net.conczin.mca.entity.VillagerEntityMCA;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerEntityBaseModelMCA.class)
public abstract class VillagerModelAgingMixin {
    @Inject(method = "setupAnim(Lnet/conczin/mca/client/render/VillagerRenderState;)V", at = @At("TAIL"))
    private void mcavie$elderPose(VillagerRenderState state, CallbackInfo ci) {
        VillagerEntityMCA villager = AgingClient.villager(state);
        if (villager != null) {
            AgingClient.pose((VillagerEntityBaseModelMCA) (Object) this, villager, state.attackTime);
        }
    }
}
