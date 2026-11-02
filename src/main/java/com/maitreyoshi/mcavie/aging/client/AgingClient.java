package com.maitreyoshi.mcavie.aging.client;

import com.maitreyoshi.mcavie.aging.AgeHolder;
import com.maitreyoshi.mcavie.aging.Aging;
import com.maitreyoshi.mcavie.aging.AgingConfig;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.mca.client.model.VillagerEntityBaseModelMCA;
import net.mca.client.render.VillagerEntityMCARenderer;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.client.model.ModelPart;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
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
                if (client.world != null && client.world.getEntityById(id) instanceof AgeHolder holder) {
                    holder.mcavie$setAgeYears(years);
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(Aging.CONFIG, (client, handler, buf, responseSender) -> {
            String json = buf.readString(1 << 16);
            client.execute(() -> AgingConfig.applyRemote(json));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> AgingConfig.load());
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof VillagerEntityMCARenderer villagerRenderer) {
                helper.register(new AgingLayer(villagerRenderer));
                helper.register(new CaneLayer(villagerRenderer, context.getHeldItemRenderer()));
            }
        });
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
                    ? MathHelper.lerp(amount / 0.6f, color[i], grey[i])
                    : MathHelper.lerp((amount - 0.6f) / 0.4f, grey[i], white[i]);
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

    // la main dominante si elle est libre, sinon l'autre : un objet en main ne doit pas faire disparaître la canne
    @Nullable
    public static Arm caneArm(VillagerEntityMCA villager) {
        AgingConfig config = AgingConfig.get();
        if (!config.cane || Aging.years(villager) < config.caneAge
                || villager.isSleeping() || villager.hasVehicle() || villager.isSwimming() || villager.isBaby()) {
            return null;
        }
        Arm main = villager.getMainArm();
        if (villager.getMainHandStack().isEmpty()) {
            return main;
        }
        return villager.getOffHandStack().isEmpty() ? main.getOpposite() : null;
    }

    public static void pose(VillagerEntityBaseModelMCA<?> model, VillagerEntityMCA villager) {
        float stoop = stoopAmount(villager);
        boolean free = !villager.isSleeping() && !villager.hasVehicle() && !villager.isInSneakingPose()
                && !villager.isSwimming() && !villager.isFallFlying();
        float angle = free ? stoop * AgingConfig.get().stoopDegrees * MathHelper.RADIANS_PER_DEGREE : 0;
        float sin = MathHelper.sin(angle);
        float cos = MathHelper.cos(angle);
        model.head.pivotZ = -4 * sin;
        if (angle > 0) {
            model.body.pitch += angle;
            model.head.pivotY += 2 * sin;
            model.rightArm.pivotZ += 2 * sin;
            model.leftArm.pivotZ += 2 * sin;
            model.rightLeg.pivotZ += 12 * sin;
            model.leftLeg.pivotZ += 12 * sin;
            model.rightLeg.pivotY -= 12 * (1 - cos);
            model.leftLeg.pivotY -= 12 * (1 - cos);
            for (ModelPart part : model.getBreastParts()) {
                float y = part.pivotY;
                float z = part.pivotZ;
                part.pivotY = y * cos - z * sin;
                part.pivotZ = y * sin + z * cos;
                part.pitch += angle;
            }
        }
        model.hat.copyTransform(model.head);

        Arm caneArm = free ? caneArm(villager) : null;
        if (caneArm != null && (caneArm != villager.getMainArm() || model.handSwingProgress <= 0)) {
            ModelPart arm = caneArm == Arm.LEFT ? model.leftArm : model.rightArm;
            arm.pitch = -0.15f;
            arm.yaw = 0;
            arm.roll = 0;
        }
    }

    public static float shrink(VillagerEntityMCA villager) {
        return 1 - AgingConfig.get().shrinkMax * stoopAmount(villager);
    }
}
