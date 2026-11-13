package com.maitreyoshi.mcavie.aging.client;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.aging.Aging;
import com.maitreyoshi.mcavie.aging.AgingConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.mca.client.model.VillagerEntityModelMCA;
import net.mca.client.render.layer.VillagerLayer;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.relationship.Gender;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public class AgingLayer extends VillagerLayer<VillagerEntityMCA, VillagerEntityModelMCA<VillagerEntityMCA>> {
    private static final ResourceLocation LIGHT_MALE = texture("wrinkles_light_male");
    private static final ResourceLocation STRONG_MALE = texture("wrinkles_strong_male");
    private static final ResourceLocation LIGHT_FEMALE = texture("wrinkles_light_female");
    private static final ResourceLocation STRONG_FEMALE = texture("wrinkles_strong_female");
    private static final ResourceLocation BEARD_FULL = texture("beard_full");
    private static final ResourceLocation BEARD_STUBBLE = texture("beard_stubble");

    public AgingLayer(RenderLayerParent<VillagerEntityMCA, VillagerEntityModelMCA<VillagerEntityMCA>> renderer) {
        super(renderer, new VillagerEntityModelMCA<VillagerEntityMCA>(
                LayerDefinition.create(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.03F)), 64, 64).bakeRoot()).hideWears());
    }

    private static ResourceLocation texture(String name) {
        return MCAVie.id("textures/entity/aging/" + name + ".png");
    }

    @Override
    public void render(PoseStack transform, MultiBufferSource provider, int light, VillagerEntityMCA villager, float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        if (getSkin(villager) == null && getOverlay(villager) == null) {
            return;
        }
        model.setAllVisible(false);
        model.head.visible = true;
        super.render(transform, provider, light, villager, limbAngle, limbDistance, tickDelta, animationProgress, headYaw, headPitch);
    }

    @Override
    protected boolean isTranslucent() {
        return true;
    }

    @Nullable
    @Override
    public ResourceLocation getSkin(VillagerEntityMCA villager) {
        AgingConfig config = AgingConfig.get();
        int years = Aging.years(villager);
        if (!config.wrinkles || years < config.lightWrinklesAge) {
            return null;
        }
        boolean strong = years >= config.strongWrinklesAge;
        if (villager.getGenetics().getGender() == Gender.FEMALE) {
            return strong ? STRONG_FEMALE : LIGHT_FEMALE;
        }
        return strong ? STRONG_MALE : LIGHT_MALE;
    }

    @Nullable
    @Override
    protected ResourceLocation getOverlay(VillagerEntityMCA villager) {
        AgingConfig config = AgingConfig.get();
        if (villager.getGenetics().getGender() != Gender.MALE || Aging.years(villager) < config.greyBeardAge
                || AgingClient.hash(villager.getUUID(), 1) >= config.greyBeardChance) {
            return null;
        }
        return AgingClient.hash(villager.getUUID(), 2) < 0.5f ? BEARD_FULL : BEARD_STUBBLE;
    }
}
