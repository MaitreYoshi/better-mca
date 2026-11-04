package com.maitreyoshi.mcavie.work.client;

import com.maitreyoshi.mcavie.work.WorkGesture;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;

// un geste par métier, joué en boucle tant que le PNJ est à son poste (angles en radians, négatif = bras vers l'avant)
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
                // hache à deux mains : levée au-dessus de l'épaule puis abattue, un coup par seconde
                float t = cycle(progress, 20F);
                float swing = t < 0.65F ? ease(t / 0.65F) : 1F - ease((t - 0.65F) / 0.35F);
                float pitch = MathHelper.lerp(swing, -2.7F, -0.6F);
                pose(right, pitch, -0.35F, 0F);
                pose(left, pitch, 0.35F, 0F);
                model.body.yaw = 0.15F * (1F - swing);
                model.head.pitch = 0.25F;
            }
            case HOE -> {
                // binage : la houe gratte le sol en va-et-vient
                float s = MathHelper.sin(progress * 0.25F);
                pose(right, -0.75F + 0.35F * s, -0.1F, 0F);
                pose(left, -0.55F + 0.35F * s, 0.25F, 0F);
                model.body.pitch = 0.2F;
                model.head.pitch = 0.45F;
            }
            case FISH -> {
                // canne tenue devant soi, légère oscillation du poignet
                pose(right, -1.05F + 0.04F * MathHelper.sin(progress * 0.08F), -0.15F, 0F);
                pose(left, -0.35F, 0.2F, 0F);
                model.head.pitch = 0.2F;
            }
            case TEND -> {
                // penché vers les bêtes, les mains basses
                float s = MathHelper.sin(progress * 0.18F);
                pose(right, -0.9F + 0.2F * s, -0.1F, 0F);
                pose(left, -0.9F - 0.2F * s, 0.1F, 0F);
                model.body.pitch = 0.35F;
                model.head.pitch = 0.5F;
            }
            case HEAL -> {
                // mains tendues vers le patient
                float s = MathHelper.sin(progress * 0.12F);
                pose(right, -1.35F + 0.08F * s, -0.25F, 0F);
                pose(left, -1.35F - 0.08F * s, 0.25F, 0F);
                model.head.pitch = 0.15F;
            }
            case HAMMER -> {
                // marteau qui frappe l'enclume, deux coups par seconde ; l'autre main tient la pièce
                float t = cycle(progress, 10F);
                float swing = t < 0.6F ? ease(t / 0.6F) : 1F - ease((t - 0.6F) / 0.4F);
                pose(right, MathHelper.lerp(swing, -2.3F, -0.7F), -0.1F, 0F);
                pose(left, -0.75F, 0.3F, 0F);
                model.head.pitch = 0.4F;
            }
            case KNEAD -> {
                // pétrissage : les deux mains appuient tour à tour sur la pâte
                float s = MathHelper.sin(progress * 0.3F);
                pose(right, -0.95F + 0.25F * s, -0.2F, 0F);
                pose(left, -0.95F - 0.25F * s, 0.2F, 0F);
                model.body.pitch = 0.15F;
                model.head.pitch = 0.4F;
            }
            case TEACH -> {
                // montre le tableau d'une main, le livre dans l'autre
                float s = MathHelper.sin(progress * 0.06F);
                pose(right, -2.2F + 0.25F * s, -0.4F, 0F);
                pose(left, -0.6F, 0.2F, 0F);
            }
            case MINE -> {
                // pioche levée par-dessus l'épaule puis abattue sur la paroi
                float t = cycle(progress, 16F);
                float swing = t < 0.6F ? ease(t / 0.6F) : 1F - ease((t - 0.6F) / 0.4F);
                pose(right, MathHelper.lerp(swing, -2.9F, -0.8F), -0.2F, 0F);
                pose(left, MathHelper.lerp(swing, -1.6F, -0.6F), 0.25F, 0F);
                model.head.pitch = 0.2F;
            }
            case STIR -> {
                // remue la marmite en rond avec la louche
                float a = progress * 0.2F;
                pose(right, -1.0F + 0.15F * MathHelper.sin(a), -0.25F + 0.3F * MathHelper.cos(a), 0F);
                pose(left, -0.45F, 0.2F, 0F);
                model.head.pitch = 0.45F;
            }
            default -> {
            }
        }
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
