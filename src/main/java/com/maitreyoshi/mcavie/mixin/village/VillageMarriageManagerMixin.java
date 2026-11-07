package com.maitreyoshi.mcavie.mixin.village;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.maitreyoshi.mcavie.social.Social;
import net.mca.entity.VillagerEntityMCA;
import net.mca.server.world.data.villageComponents.VillageMarriageManager;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;
import java.util.stream.Stream;

@Mixin(value = VillageMarriageManager.class, remap = false)
public abstract class VillageMarriageManagerMixin {
    @SuppressWarnings("unchecked")
    @WrapOperation(method = "marry", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;findFirst()Ljava/util/Optional;"))
    private Optional<?> mcavie$chooseFriend(Stream<?> candidates, Operation<Optional<?>> original,
                                            @Local(argsOnly = true) ServerWorld world, @Local(ordinal = 0) VillagerEntityMCA suitor) {
        return Social.chooseSpouse(world, suitor, ((Stream<VillagerEntityMCA>) candidates).toList());
    }
}
