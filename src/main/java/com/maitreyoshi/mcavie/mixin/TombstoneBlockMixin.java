package com.maitreyoshi.mcavie.mixin;

import com.maitreyoshi.mcavie.life.TombReservations;
import com.maitreyoshi.mcavie.life.VaultBlock;
import net.conczin.mca.block.TombstoneBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TombstoneBlock.class)
public abstract class TombstoneBlockMixin extends BaseEntityBlock {
    protected TombstoneBlockMixin(Properties settings) {
        super(settings);
    }

    @Inject(method = "onRemove", at = @At("TAIL"))
    private void mcavie$releaseReservation(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved, CallbackInfo ci) {
        if (world instanceof ServerLevel server && !state.is(newState.getBlock())) {
            TombReservations.tombRemoved(server, pos);
        }
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter world, BlockPos pos) {
        return !VaultBlock.silkTouch(player) && TombstoneBlock.Data.of(world.getBlockEntity(pos)).map(TombstoneBlock.Data::hasEntity).orElse(false)
                ? 0 : super.getDestroyProgress(state, player, world, pos);
    }

    @Override
    public void attack(BlockState state, Level world, BlockPos pos, Player player) {
        if (!world.isClientSide() && !VaultBlock.silkTouch(player) && TombstoneBlock.Data.of(world.getBlockEntity(pos)).map(TombstoneBlock.Data::hasEntity).orElse(false)) {
            player.sendOverlayMessage(Component.translatable("tomb.mca_vie.occupied").withStyle(ChatFormatting.GOLD));
        }
        super.attack(state, world, pos, player);
    }
}
