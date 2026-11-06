package com.maitreyoshi.mcavie.aging.client;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.aging.Aging;
import com.maitreyoshi.mcavie.aging.AgingConfig;
import net.mca.client.model.VillagerEntityModelMCA;
import net.mca.client.render.layer.VillagerLayer;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.relationship.Gender;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public class AgingLayer extends VillagerLayer<VillagerEntityMCA, VillagerEntityModelMCA<VillagerEntityMCA>> {
    private static final Identifier LIGHT_MALE = texture("wrinkles_light_male");
    private static final Identifier STRONG_MALE = texture("wrinkles_strong_male");
    private static final Identifier LIGHT_FEMALE = texture("wrinkles_light_female");
    private static final Identifier STRONG_FEMALE = texture("wrinkles_strong_female");
    private static final Identifier BEARD_FULL = texture("beard_full");
    private static final Identifier BEARD_STUBBLE = texture("beard_stubble");

    public AgingLayer(FeatureRendererContext<VillagerEntityMCA, VillagerEntityModelMCA<VillagerEntityMCA>> renderer) {
        super(renderer, new VillagerEntityModelMCA<VillagerEntityMCA>(
                TexturedModelData.of(VillagerEntityModelMCA.bodyData(new Dilation(0.03F)), 64, 64).createModel()).hideWears());
    }

    private static Identifier texture(String name) {
        return MCAVie.id("textures/entity/aging/" + name + ".png");
    }

    @Override
    public void render(MatrixStack transform, VertexConsumerProvider provider, int light, VillagerEntityMCA villager, float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        if (getSkin(villager) == null && getOverlay(villager) == null) {
            return;
        }
        model.setVisible(false);
        model.head.visible = true;
        super.render(transform, provider, light, villager, limbAngle, limbDistance, tickDelta, animationProgress, headYaw, headPitch);
    }

    @Override
    protected boolean isTranslucent() {
        return true;
    }

    @Nullable
    @Override
    public Identifier getSkin(VillagerEntityMCA villager) {
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
    protected Identifier getOverlay(VillagerEntityMCA villager) {
        AgingConfig config = AgingConfig.get();
        if (villager.getGenetics().getGender() != Gender.MALE || Aging.years(villager) < config.greyBeardAge
                || AgingClient.hash(villager.getUuid(), 1) >= config.greyBeardChance) {
            return null;
        }
        return AgingClient.hash(villager.getUuid(), 2) < 0.5f ? BEARD_FULL : BEARD_STUBBLE;
    }
}
