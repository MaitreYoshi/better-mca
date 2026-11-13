package com.maitreyoshi.mcavie.aging.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.render.VillagerRenderState;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class CaneLayer extends RenderLayer<VillagerRenderState, VillagerEntityModelMCA> {
    private final ItemStackRenderState item = new ItemStackRenderState();
    private ItemStack cane;

    public CaneLayer(RenderLayerParent<VillagerRenderState, VillagerEntityModelMCA> context) {
        super(context);
    }

    @Override
    public void submit(PoseStack matrices, SubmitNodeCollector collector, int light, VillagerRenderState state, float yRot, float xRot) {
        VillagerEntityMCA villager = AgingClient.villager(state);
        HumanoidArm arm = villager == null ? null : AgingClient.caneArm(villager);
        if (state.isInvisible || arm == null) {
            return;
        }
        Minecraft.getInstance().getItemModelResolver().updateForLiving(item, cane(), ItemDisplayContext.NONE, villager);
        matrices.pushPose();
        getParentModel().translateToHand(state, arm, matrices);
        matrices.translate((arm == HumanoidArm.LEFT ? 1 : -1) / 16f, 15 / 16f, -1 / 32f);
        matrices.mulPose(Axis.ZP.rotationDegrees(45));
        matrices.scale(0.9f, 0.9f, 0.9f);
        item.submit(matrices, collector, light, LivingEntityRenderer.getOverlayCoords(state, 0.0F), state.outlineColor);
        matrices.popPose();
    }

    private ItemStack cane() {
        if (cane == null) {
            cane = new ItemStack(Items.STICK);
        }
        return cane;
    }
}
