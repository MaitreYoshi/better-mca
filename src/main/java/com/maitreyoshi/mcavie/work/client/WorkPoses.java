package com.maitreyoshi.mcavie.work.client;

import com.maitreyoshi.mcavie.work.WorkGesture;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;

public final class WorkPoses {
    private WorkPoses() {
    }

    public static void apply(BipedEntityModel<?> model, LivingEntity entity, float progress) {
        if (!(entity instanceof VillagerEntityMCA)) {
            return;
        }
        WorkClient.Info info = WorkClient.get(entity.getId());
        if (info == null || !info.working() || info.gesture() == WorkGesture.NONE) {
            return;
        }
        ModelPart right = model.rightArm;
        ModelPart left = model.leftArm;
        switch (info.gesture()) {
            case CHOP -> {
                float t = cycle(progress, 20F);
                float swing = t < 0.65F ? ease(t / 0.65F) : 1F - ease((t - 0.65F) / 0.35F);
                float pitch = MathHelper.lerp(swing, -2.7F, -0.6F);
                pose(right, pitch, -0.35F, 0F);
                pose(left, pitch, 0.35F, 0F);
                model.body.yaw = 0.15F * (1F - swing);
                model.head.pitch = 0.25F;
            }
            case HOE -> {
                float s = MathHelper.sin(progress * 0.25F);
                pose(right, -0.75F + 0.35F * s, -0.1F, 0F);
                pose(left, -0.55F + 0.35F * s, 0.25F, 0F);
                model.head.pitch = 0.45F;
            }
            case FISH -> {
                pose(right, -1.05F + 0.04F * MathHelper.sin(progress * 0.08F), -0.15F, 0F);
                pose(left, -0.35F, 0.2F, 0F);
                model.head.pitch = 0.2F;
            }
            case TEND -> {
                float s = MathHelper.sin(progress * 0.18F);
                pose(right, -0.9F + 0.2F * s, -0.1F, 0F);
                pose(left, -0.9F - 0.2F * s, 0.1F, 0F);
                model.head.pitch = 0.5F;
            }
            case HEAL -> {
                float s = MathHelper.sin(progress * 0.12F);
                pose(right, -1.35F + 0.08F * s, -0.25F, 0F);
                pose(left, -1.35F - 0.08F * s, 0.25F, 0F);
                model.head.pitch = 0.15F;
            }
            case HAMMER -> {
                float t = cycle(progress, 10F);
                float swing = t < 0.6F ? ease(t / 0.6F) : 1F - ease((t - 0.6F) / 0.4F);
                pose(right, MathHelper.lerp(swing, -2.3F, -0.7F), -0.1F, 0F);
                pose(left, -0.75F, 0.3F, 0F);
                model.head.pitch = 0.4F;
            }
            case KNEAD -> {
                float s = MathHelper.sin(progress * 0.3F);
                pose(right, -0.95F + 0.25F * s, -0.2F, 0F);
                pose(left, -0.95F - 0.25F * s, 0.2F, 0F);
                model.head.pitch = 0.4F;
            }
            case TEACH -> {
                float s = MathHelper.sin(progress * 0.06F);
                pose(right, -2.2F + 0.25F * s, -0.4F, 0F);
                pose(left, -0.6F, 0.2F, 0F);
            }
            case MINE -> {
                float t = cycle(progress, 16F);
                float swing = t < 0.6F ? ease(t / 0.6F) : 1F - ease((t - 0.6F) / 0.4F);
                pose(right, MathHelper.lerp(swing, -2.9F, -0.8F), -0.2F, 0F);
                pose(left, MathHelper.lerp(swing, -1.6F, -0.6F), 0.25F, 0F);
                model.head.pitch = 0.2F;
            }
            case STIR -> {
                float a = progress * 0.2F;
                pose(right, -1.0F + 0.15F * MathHelper.sin(a), -0.25F + 0.3F * MathHelper.cos(a), 0F);
                pose(left, -0.45F, 0.2F, 0F);
                model.head.pitch = 0.45F;
            }
            default -> {
            }
        }
    }

    public static float lean(LivingEntity entity) {
        WorkClient.Info info = WorkClient.get(entity.getId());
        if (info == null || !info.working()) {
            return 0F;
        }
        return switch (info.gesture()) {
            case HOE -> 0.2F;
            case TEND -> 0.35F;
            case KNEAD -> 0.15F;
            default -> 0F;
        };
    }

    private static void pose(ModelPart arm, float pitch, float yaw, float roll) {
        arm.pitch = pitch;
        arm.yaw = yaw;
        arm.roll = roll;
    }

    private static float cycle(float progress, float period) {
        return (progress % period) / period;
    }

    private static float ease(float t) {
        return t * t * (3F - 2F * t);
    }
}
