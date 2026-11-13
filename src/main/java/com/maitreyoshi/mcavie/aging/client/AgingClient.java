package com.maitreyoshi.mcavie.aging.client;

import com.maitreyoshi.mcavie.aging.AgeHolder;
import com.maitreyoshi.mcavie.aging.Aging;
import com.maitreyoshi.mcavie.aging.AgingConfig;
import com.maitreyoshi.mcavie.compat.client.ClientPlayConnectionEvents;
import com.maitreyoshi.mcavie.compat.client.ClientPlayNetworking;
import net.conczin.mca.client.model.VillagerEntityBaseModelMCA;
import net.conczin.mca.client.render.VillagerEntityMCARenderer;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class AgingClient {
    private AgingClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(Aging.AGE, (client, handler, buf, responseSender) -> {
            int id = buf.readVarInt();
            int years = buf.readVarInt();
            client.execute(() -> {
                if (client.level != null && client.level.getEntity(id) instanceof AgeHolder holder) {
                    holder.mcavie$setAgeYears(years);
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(Aging.CONFIG, (client, handler, buf, responseSender) -> {
            String json = buf.readUtf(1 << 16);
            client.execute(() -> AgingConfig.applyRemote(json));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> AgingConfig.load());
    }

    public static void addLayers(net.minecraft.client.renderer.entity.EntityRenderer<?> renderer, net.minecraft.client.renderer.ItemInHandRenderer heldItemRenderer) {
        if (renderer instanceof VillagerEntityMCARenderer villagerRenderer) {
            com.maitreyoshi.mcavie.mixin.compat.client.LivingEntityRendererInvoker features = (com.maitreyoshi.mcavie.mixin.compat.client.LivingEntityRendererInvoker) villagerRenderer;
            features.mcavie$addFeature(new AgingLayer(villagerRenderer));
            features.mcavie$addFeature(new CaneLayer(villagerRenderer, heldItemRenderer));
        }
    }

    public static float hash(UUID uuid, int salt) {
        long h = uuid.getMostSignificantBits() * 0x9E3779B97F4A7C15L + uuid.getLeastSignificantBits() + salt * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return ((h >>> 40) & 0xFFFF) / (float) 0x10000;
    }

    public static float greyAmount(VillagerEntityMCA villager) {
        AgingConfig config = AgingConfig.get();
        if (!config.greyHair) {
            return 0;
        }
        return config.greyStrength * AgingConfig.progress(Aging.years(villager), config.greyStartAge, config.whiteAge);
    }

    public static float[] greyHair(float[] color, float amount) {
        float[] grey = {0.58f, 0.58f, 0.57f};
        float[] white = {0.93f, 0.93f, 0.91f};
        float[] result = new float[3];
        for (int i = 0; i < 3; i++) {
            result[i] = amount <= 0.6f
                    ? Mth.lerp(amount / 0.6f, color[i], grey[i])
                    : Mth.lerp((amount - 0.6f) / 0.4f, grey[i], white[i]);
        }
        return result;
    }

    public static float stoopAmount(VillagerEntityMCA villager) {
        AgingConfig config = AgingConfig.get();
        int years = Aging.years(villager);
        if (!config.stoop || years < config.stoopStartAge) {
            return 0;
        }
        return 0.4f + 0.6f * AgingConfig.progress(years, config.stoopStartAge, config.stoopFullAge);
    }

    @Nullable
    public static HumanoidArm caneArm(VillagerEntityMCA villager) {
        AgingConfig config = AgingConfig.get();
        if (!config.cane || Aging.years(villager) < config.caneAge
                || villager.isSleeping() || villager.isPassenger() || villager.isSwimming() || villager.isBaby()) {
            return null;
        }
        HumanoidArm main = villager.getMainArm();
        if (villager.getMainHandItem().isEmpty()) {
            return main;
        }
        return villager.getOffhandItem().isEmpty() ? main.getOpposite() : null;
    }

    public static void pose(VillagerEntityBaseModelMCA<?> model, VillagerEntityMCA villager) {
        float stoop = stoopAmount(villager);
        boolean free = !villager.isSleeping() && !villager.isPassenger() && !villager.isCrouching()
                && !villager.isSwimming() && !villager.isFallFlying();
        float angle = free ? stoop * AgingConfig.get().stoopDegrees * Mth.DEG_TO_RAD
                + com.maitreyoshi.mcavie.work.client.WorkPoses.lean(villager) : 0;
        float sin = Mth.sin(angle);
        float cos = Mth.cos(angle);
        model.head.z = -4 * sin;
        if (angle > 0) {
            model.body.xRot += angle;
            model.head.y += 2 * sin;
            model.rightArm.z += 2 * sin;
            model.leftArm.z += 2 * sin;
            model.rightLeg.z += 12 * sin;
            model.leftLeg.z += 12 * sin;
            model.rightLeg.y -= 12 * (1 - cos);
            model.leftLeg.y -= 12 * (1 - cos);
            for (ModelPart part : model.getBreastParts()) {
                float y = part.y;
                float z = part.z;
                part.y = y * cos - z * sin;
                part.z = y * sin + z * cos;
                part.xRot += angle;
            }
        }
        model.hat.copyFrom(model.head);

        HumanoidArm caneArm = free ? caneArm(villager) : null;
        if (caneArm != null && (caneArm != villager.getMainArm() || model.attackTime <= 0)) {
            ModelPart arm = caneArm == HumanoidArm.LEFT ? model.leftArm : model.rightArm;
            arm.xRot = -0.15f;
            arm.yRot = 0;
            arm.zRot = 0;
        }
    }

    public static float shrink(VillagerEntityMCA villager) {
        return 1 - AgingConfig.get().shrinkMax * stoopAmount(villager);
    }
}
