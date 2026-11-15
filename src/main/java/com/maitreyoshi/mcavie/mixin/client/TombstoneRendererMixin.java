package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.life.TombstoneBlocks;
import com.maitreyoshi.mcavie.life.TombstoneDetails;
import com.maitreyoshi.mcavie.life.client.TombstoneClient;
import net.mca.block.TombstoneBlock;
import net.mca.client.render.TombstoneBlockEntityRenderer;
import net.minecraft.block.BlockState;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
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
    private TextRenderer text;

    @Inject(method = "render(Lnet/mca/block/TombstoneBlock$Data;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V",
            at = @At("HEAD"), cancellable = true)
    private void mcavie$renderDetails(TombstoneBlock.Data entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers,
                                      int light, int overlay, CallbackInfo ci) {
        TombstoneDetails details = ((TombstoneDetails.Holder) entity).mcavie$details();
        BlockState state = entity.getCachedState();
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

        matrices.push();
        matrices.translate(0.5, 0.5, 0.5);
        Direction facing = state.get(Properties.HORIZONTAL_FACING).getOpposite();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()));
        matrices.scale(0.010416667F, 0.010416667F, 0.010416667F);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(block.getRotation()));
        Vec3d offset = block.getNameplateOffset();
        matrices.translate(offset.getX(), offset.getY(), offset.getZ());

        TombstoneClient.render(details, block, text, matrices, consumers, light);

        matrices.pop();
    }
}
