package com.maitreyoshi.mcavie.life;

import com.maitreyoshi.mcavie.social.SocialConversation;
import net.mca.entity.VillagerEntityMCA;
import net.mca.server.world.data.FamilyTreeNode;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.random.Random;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Retour d'entre les morts : les témoins s'exclament tout de suite, puis chaque villageois qui croise le
 * ressuscité pour la première fois peut s'en étonner. L'étonnement s'estompe sur quelques jours de jeu,
 * puisque tout le village finit par l'avoir vu ou en avoir entendu parler.
 */
public final class Resurrection {
    /** Durée pendant laquelle les villageois s'étonnent encore (7 jours Minecraft). */
    private static final long FADE_TICKS = 7 * 24000L;
    /** En dessous de cette part de la durée, c'est le choc ; après, la rumeur a fait le tour. */
    private static final double SHOCK_PART = 0.3;
    private static final double MEET_RADIUS = 6;
    private static final long REMARK_GAP_TICKS = 200;
    private static final int SHOCK_VARIANTS = 4;
    private static final int RUMOR_VARIANTS = 3;
    private static final int FAMILY_VARIANTS = 3;

    private static final Map<UUID, Set<UUID>> SEEN = new HashMap<>();
    private static final Map<UUID, Long> LAST_REMARK = new HashMap<>();

    private Resurrection() {
    }

    public static void returned(VillagerEntityMCA villager, ServerWorld world) {
        SEEN.remove(villager.getUuid());
        LAST_REMARK.put(villager.getUuid(), world.getTime());
        SocialConversation.witness(world, villager, "resurrection", villager.getName(), villager.getUuid());
        // les témoins de la scène ne s'étonneront pas une seconde fois
        Set<UUID> seen = SEEN.computeIfAbsent(villager.getUuid(), k -> new HashSet<>());
        world.getEntitiesByClass(VillagerEntityMCA.class, villager.getBoundingBox().expand(10), v -> v != villager)
                .forEach(v -> seen.add(v.getUuid()));
    }

    public static void update(VillagerEntityMCA villager, ServerWorld world) {
        UUID uuid = villager.getUuid();
        Long since = LifeRegistry.get(world.getServer()).resurrectedAt(uuid);
        long time = world.getTime();
        if (since == null || time - since >= FADE_TICKS || time < since) {
            SEEN.remove(uuid);
            LAST_REMARK.remove(uuid);
            return;
        }
        List<VillagerEntityMCA> nearby = world.getEntitiesByClass(VillagerEntityMCA.class,
                villager.getBoundingBox().expand(MEET_RADIUS), v -> v != villager && v.isAlive() && !v.isBaby());
        if (nearby.isEmpty()) {
            return;
        }
        Set<UUID> seen = SEEN.computeIfAbsent(uuid, k -> new HashSet<>());
        double elapsed = (double) (time - since) / FADE_TICKS;
        Random random = world.getRandom();
        Set<UUID> family = family(villager);
        for (VillagerEntityMCA other : nearby) {
            if (!seen.add(other.getUuid())) {
                continue;
            }
            boolean relative = family.contains(other.getUuid());
            // la famille s'émeut toujours, les autres de moins en moins avec le temps
            if (!relative && random.nextDouble() > 1 - elapsed) {
                continue;
            }
            if (time - LAST_REMARK.getOrDefault(uuid, Long.MIN_VALUE / 2) < REMARK_GAP_TICKS) {
                continue;
            }
            LAST_REMARK.put(uuid, time);
            String key = relative ? "family." + random.nextInt(FAMILY_VARIANTS)
                    : elapsed < SHOCK_PART ? "shock." + random.nextInt(SHOCK_VARIANTS)
                    : "rumor." + random.nextInt(RUMOR_VARIANTS);
            SocialConversation.remark(world, other, Text.translatable("chat.mca_vie.resurrection." + key, villager.getName()));
        }
    }

    private static Set<UUID> family(VillagerEntityMCA villager) {
        FamilyTreeNode node = villager.getRelationships().getFamilyEntry();
        Set<UUID> family = new HashSet<>(node.children());
        family.addAll(node.siblings());
        node.getParents().forEach(parent -> family.add(parent.id()));
        if (FamilyTreeNode.isValid(node.partner())) {
            family.add(node.partner());
        }
        return family;
    }
}
