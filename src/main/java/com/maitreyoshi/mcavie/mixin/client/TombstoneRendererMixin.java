package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.life.TombstoneBlocks;
import com.maitreyoshi.mcavie.life.TombstoneDetails;
import com.maitreyoshi.mcavie.life.client.TombstoneClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.conczin.mca.block.TombstoneBlock;
import net.conczin.mca.client.render.TombstoneBlockEntityRenderer;
import net.minecraft.client.gui.Font;
import com.maitreyoshi.mcavie.compat.client.Buffers;
import net.conczin.mca.client.render.TombstoneBlockEntityRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TombstoneBlockEntityRenderer.class)
public abstract class TombstoneRendererMixin {
    @Shadow(remap = false)
    @Final
    private Font text;

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void mcavie$renderDetails(TombstoneBlockEntityRenderState renderState, PoseStack matrices, SubmitNodeCollector collector,
                                      CameraRenderState camera, CallbackInfo ci) {
        TombstoneBlock.Data entity = renderState.data;
        if (entity == null) {
            return;
        }
        int light = renderState.lightCoords;
        Buffers consumers = new Buffers(collector, matrices);
        TombstoneDetails details = ((TombstoneDetails.Holder) entity).mcavie$details();
        BlockState state = entity.getBlockState();
        if (state.getBlock() instanceof com.maitreyoshi.mcavie.life.VaultBlock) {
            ci.cancel();
            com.maitreyoshi.mcavie.life.client.VaultClient.render(entity, entity.hasEntity() ? details : null, state, text, matrices, consumers, light);
            return;
        }
        if (details == null || !entity.hasEntity() || !(state.getBlock() instanceof TombstoneBlock block)) {
            return;
        }
        ci.cancel();

        if (block instanceof TombstoneBlocks.Tomb tomb) {
            TombstoneClient.renderStyled(details, tomb.tier(), state, text, matrices, consumers, light);
            return;
        }

        matrices.pushPose();
        matrices.translate(0.5, 0.5, 0.5);
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
        matrices.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        matrices.scale(0.010416667F, 0.010416667F, 0.010416667F);
        matrices.mulPose(Axis.ZP.rotationDegrees(180));
        matrices.mulPose(Axis.XP.rotationDegrees(block.getRotation()));
        Vec3 offset = block.getNameplateOffset();
        matrices.translate(offset.x(), offset.y(), offset.z());

        TombstoneClient.render(details, block, text, matrices, consumers, light);

        matrices.popPose();
    }
}
