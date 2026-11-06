package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.aging.client.AgingClient;
import net.mca.client.render.layer.HairLayer;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Traits;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HairLayer.class)
public abstract class HairLayerMixin {
    @Inject(method = "getColor", at = @At("RETURN"), cancellable = true, remap = false)
    private void mcavie$greyHair(LivingEntity entity, float tickDelta, CallbackInfoReturnable<float[]> cir) {
        if (!(entity instanceof VillagerEntityMCA villager) || villager.getTraits().hasTrait(Traits.RAINBOW) || villager.getHairDye()[0] > 0) {
            return;
        }
        float amount = AgingClient.greyAmount(villager);
        if (amount > 0) {
            cir.setReturnValue(AgingClient.greyHair(cir.getReturnValue(), amount));
        }
    }
}
