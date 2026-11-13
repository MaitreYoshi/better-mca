package com.maitreyoshi.mcavie.aging.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.mca.client.model.VillagerEntityModelMCA;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class CaneLayer extends RenderLayer<VillagerEntityMCA, VillagerEntityModelMCA<VillagerEntityMCA>> {
    private static final ItemStack CANE = new ItemStack(Items.STICK);

    private final ItemInHandRenderer itemRenderer;

    public CaneLayer(RenderLayerParent<VillagerEntityMCA, VillagerEntityModelMCA<VillagerEntityMCA>> context, ItemInHandRenderer itemRenderer) {
        super(context);
        this.itemRenderer = itemRenderer;
    }

    @Override
    public void render(PoseStack matrices, MultiBufferSource provider, int light, VillagerEntityMCA villager, float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        HumanoidArm arm = AgingClient.caneArm(villager);
        if (villager.isInvisible() || villager.hasCustomSkin() || arm == null) {
            return;
        }
        matrices.pushPose();
        getParentModel().translateToHand(arm, matrices);
        matrices.translate((arm == HumanoidArm.LEFT ? 1 : -1) / 16f, 15 / 16f, -1 / 32f);
        matrices.mulPose(Axis.ZP.rotationDegrees(45));
        matrices.scale(0.9f, 0.9f, 0.9f);
        itemRenderer.renderItem(villager, CANE, ItemDisplayContext.NONE, arm == HumanoidArm.LEFT, matrices, provider, light);
        matrices.popPose();
    }
}
