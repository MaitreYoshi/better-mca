package com.maitreyoshi.mcavie.mixin;

import com.maitreyoshi.mcavie.life.TombstoneBlocks;
import net.conczin.mca.block.BlockEntityTypesMCA;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityType.class)
public abstract class BlockEntityTypeMixin {
    @Inject(method = "isValid", at = @At("HEAD"), cancellable = true)
    private void mcavie$supportTombs(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if ((state.getBlock() instanceof TombstoneBlocks.Tomb || state.getBlock() instanceof com.maitreyoshi.mcavie.life.VaultBlock) &&(Object) this == BlockEntityTypesMCA.TOMBSTONE) {
            cir.setReturnValue(true);
        }
    }
}
