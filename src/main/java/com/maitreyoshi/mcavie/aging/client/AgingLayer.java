package com.maitreyoshi.mcavie.aging.client;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.aging.Aging;
import com.maitreyoshi.mcavie.aging.AgingConfig;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.render.VillagerRenderState;
import net.conczin.mca.client.render.layer.VillagerLayer;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

public class AgingLayer extends VillagerLayer<VillagerRenderState, VillagerEntityModelMCA> {
    private static final Identifier LIGHT_MALE = texture("wrinkles_light_male");
    private static final Identifier STRONG_MALE = texture("wrinkles_strong_male");
    private static final Identifier LIGHT_FEMALE = texture("wrinkles_light_female");
    private static final Identifier STRONG_FEMALE = texture("wrinkles_strong_female");
    private static final Identifier BEARD_FULL = texture("beard_full");
    private static final Identifier BEARD_STUBBLE = texture("beard_stubble");

    public AgingLayer(RenderLayerParent<VillagerRenderState, VillagerEntityModelMCA> renderer) {
        super(renderer, model());
    }

    private static VillagerEntityModelMCA model() {
        VillagerEntityModelMCA model = new VillagerEntityModelMCA(
                LayerDefinition.create(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.03F)), 64, 64).bakeRoot());
        model.receiveDeferredAnimationPose();
        return (VillagerEntityModelMCA) model.hideWears();
    }

    private static Identifier texture(String name) {
        return MCAVie.id("textures/entity/aging/" + name + ".png");
    }

    @Override
    protected void prepareModel(VillagerRenderState state) {
        model.setAllVisible(false);
        model.head.visible = true;
    }

    @Override
    protected boolean isTranslucent() {
        return true;
    }

    @Nullable
    @Override
    public Identifier getSkin(VillagerRenderState state) {
        VillagerEntityMCA villager = AgingClient.villager(state);
        AgingConfig config = AgingConfig.get();
        if (villager == null || !config.wrinkles) {
            return null;
        }
        int years = Aging.years(villager);
        if (years < config.lightWrinklesAge) {
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
    protected Identifier getOverlay(VillagerRenderState state) {
        VillagerEntityMCA villager = AgingClient.villager(state);
        AgingConfig config = AgingConfig.get();
        if (villager == null || villager.getGenetics().getGender() != Gender.MALE || Aging.years(villager) < config.greyBeardAge
                || AgingClient.hash(villager.getUUID(), 1) >= config.greyBeardChance) {
            return null;
        }
        return AgingClient.hash(villager.getUUID(), 2) < 0.5f ? BEARD_FULL : BEARD_STUBBLE;
    }
}
