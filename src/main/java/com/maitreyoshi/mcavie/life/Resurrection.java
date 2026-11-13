package com.maitreyoshi.mcavie.life;

import com.maitreyoshi.mcavie.social.SocialConversation;
import net.mca.entity.VillagerEntityMCA;
import net.mca.server.world.data.FamilyTreeNode;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Resurrection {
    private static final long FADE_TICKS = 7 * 24000L;
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

    public static void returned(VillagerEntityMCA villager, ServerLevel world) {
        SEEN.remove(villager.getUUID());
        LAST_REMARK.put(villager.getUUID(), world.getGameTime());
        SocialConversation.witness(world, villager, "resurrection", villager.getName(), villager.getUUID());
        Set<UUID> seen = SEEN.computeIfAbsent(villager.getUUID(), k -> new HashSet<>());
        world.getEntitiesOfClass(VillagerEntityMCA.class, villager.getBoundingBox().inflate(10), v -> v != villager)
                .forEach(v -> seen.add(v.getUUID()));
    }

    public static void update(VillagerEntityMCA villager, ServerLevel world) {
        UUID uuid = villager.getUUID();
        Long since = LifeRegistry.get(world.getServer()).resurrectedAt(uuid);
        long time = world.getGameTime();
        if (since == null || time - since >= FADE_TICKS || time < since) {
            SEEN.remove(uuid);
            LAST_REMARK.remove(uuid);
            return;
        }
        List<VillagerEntityMCA> nearby = world.getEntitiesOfClass(VillagerEntityMCA.class,
                villager.getBoundingBox().inflate(MEET_RADIUS), v -> v != villager && v.isAlive() && !v.isBaby());
        if (nearby.isEmpty()) {
            return;
        }
        Set<UUID> seen = SEEN.computeIfAbsent(uuid, k -> new HashSet<>());
        double elapsed = (double) (time - since) / FADE_TICKS;
        RandomSource random = world.getRandom();
        Set<UUID> family = family(villager);
        for (VillagerEntityMCA other : nearby) {
            if (!seen.add(other.getUUID())) {
                continue;
            }
            boolean relative = family.contains(other.getUUID());
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
            SocialConversation.remark(world, other, Component.translatable("chat.mca_vie.resurrection." + key, villager.getName()));
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
