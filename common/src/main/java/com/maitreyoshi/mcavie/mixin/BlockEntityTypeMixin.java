package com.maitreyoshi.mcavie.mixin;

import com.maitreyoshi.mcavie.life.TombstoneBlocks;
import net.mca.block.BlockEntityTypesMCA;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityType.class)
public abstract class BlockEntityTypeMixin {
    @Inject(method = "supports", at = @At("HEAD"), cancellable = true)
    private void mcavie$supportTombs(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (state.getBlock() instanceof TombstoneBlocks.Tomb && (Object) this == BlockEntityTypesMCA.TOMBSTONE.get()) {
            cir.setReturnValue(true);
        }
    }
}
