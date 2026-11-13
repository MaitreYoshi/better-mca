package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.aging.client.AgingClient;
import net.conczin.mca.client.render.VillagerVisuals;
import net.conczin.mca.client.render.layer.HairLayer;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HairLayer.class)
public abstract class HairLayerMixin {
    @Inject(method = "getColor", at = @At("RETURN"), cancellable = true, remap = false)
    private void mcavie$greyHair(HumanoidRenderState state, float tickDelta, CallbackInfoReturnable<Integer> cir) {
        VillagerEntityMCA villager = AgingClient.villager(state);
        if (villager == null) {
            return;
        }
        VillagerVisuals visuals = VillagerVisuals.require(state);
        if (visuals.rainbowHair() || visuals.hairDye() != 0xFF000000) {
            return;
        }
        float amount = AgingClient.greyAmount(villager);
        if (amount > 0) {
            int c = cir.getReturnValue();
            float[] grey = AgingClient.greyHair(new float[]{(c >> 16 & 0xFF) / 255f, (c >> 8 & 0xFF) / 255f, (c & 0xFF) / 255f}, amount);
            cir.setReturnValue(c & 0xFF000000 | Math.round(grey[0] * 255) << 16 | Math.round(grey[1] * 255) << 8 | Math.round(grey[2] * 255));
        }
    }
}
