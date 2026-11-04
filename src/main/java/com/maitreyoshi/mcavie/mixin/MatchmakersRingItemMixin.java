package com.maitreyoshi.mcavie.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.maitreyoshi.mcavie.life.Orientation;
import net.mca.entity.VillagerEntityMCA;
import net.mca.item.MatchmakersRingItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

@Mixin(value = MatchmakersRingItem.class, remap = false)
public abstract class MatchmakersRingItemMixin {
    @SuppressWarnings("unchecked")
    @WrapOperation(method = "handle", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;min(Ljava/util/Comparator;)Ljava/util/Optional;"))
    private Optional<?> mcavie$respectOrientation(Stream<?> candidates, Comparator<?> comparator, Operation<Optional<?>> original,
                                                  @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) VillagerEntityMCA villager) {
        Stream<VillagerEntityMCA> allowed = ((Stream<VillagerEntityMCA>) candidates).filter(v -> Orientation.compatible(villager, v));
        return original.call(allowed, comparator);
    }
}
