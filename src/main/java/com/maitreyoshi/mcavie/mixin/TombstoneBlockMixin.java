package com.maitreyoshi.mcavie.mixin;

import com.maitreyoshi.mcavie.life.TombReservations;
import com.maitreyoshi.mcavie.life.VaultBlock;
import net.mca.block.TombstoneBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TombstoneBlock.class)
public abstract class TombstoneBlockMixin extends BlockWithEntity {
    protected TombstoneBlockMixin(Settings settings) {
        super(settings);
    }

    @Inject(method = "onStateReplaced", at = @At("TAIL"))
    private void mcavie$releaseReservation(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved, CallbackInfo ci) {
        if (world instanceof ServerWorld server && !state.isOf(newState.getBlock())) {
            TombReservations.tombRemoved(server, pos);
        }
    }

    @Override
    public float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
        return !VaultBlock.silkTouch(player) && TombstoneBlock.Data.of(world.getBlockEntity(pos)).map(TombstoneBlock.Data::hasEntity).orElse(false)
                ? 0 : super.calcBlockBreakingDelta(state, player, world, pos);
    }

    @Override
    public void onBlockBreakStart(BlockState state, World world, BlockPos pos, PlayerEntity player) {
        if (!world.isClient && !VaultBlock.silkTouch(player) && TombstoneBlock.Data.of(world.getBlockEntity(pos)).map(TombstoneBlock.Data::hasEntity).orElse(false)) {
            player.sendMessage(Text.translatable("tomb.mca_vie.occupied").formatted(Formatting.GOLD), true);
        }
        super.onBlockBreakStart(state, world, pos, player);
    }
}
