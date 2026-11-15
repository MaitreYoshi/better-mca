package com.maitreyoshi.mcavie.aging.client;

import net.mca.client.model.VillagerEntityModelMCA;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;

public class CaneLayer extends FeatureRenderer<VillagerEntityMCA, VillagerEntityModelMCA<VillagerEntityMCA>> {
    private static final ItemStack CANE = new ItemStack(Items.STICK);

    private final HeldItemRenderer itemRenderer;

    public CaneLayer(FeatureRendererContext<VillagerEntityMCA, VillagerEntityModelMCA<VillagerEntityMCA>> context, HeldItemRenderer itemRenderer) {
        super(context);
        this.itemRenderer = itemRenderer;
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider provider, int light, VillagerEntityMCA villager, float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        Arm arm = AgingClient.caneArm(villager);
        if (villager.isInvisible() || villager.hasCustomSkin() || arm == null) {
            return;
        }
        matrices.push();
        getContextModel().setArmAngle(arm, matrices);
        matrices.translate((arm == Arm.LEFT ? 1 : -1) / 16f, 15 / 16f, -1 / 32f);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45));
        matrices.scale(0.9f, 0.9f, 0.9f);
        itemRenderer.renderItem(villager, CANE, ModelTransformationMode.NONE, arm == Arm.LEFT, matrices, provider, light);
        matrices.pop();
    }
}
