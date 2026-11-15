package com.maitreyoshi.mcavie.social;

import com.maitreyoshi.mcavie.SurnameRegistry;
import com.maitreyoshi.mcavie.SurnameText;
import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.life.WellbeingRegistry;
import com.maitreyoshi.mcavie.mixin.village.VillageAccessor;
import com.maitreyoshi.mcavie.social.SocialRegistry.Link;
import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkRegistry;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.relationship.AgeState;
import net.mca.server.world.data.Building;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.mca.server.world.data.Village;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class Social {
    public static final int MAX_LINKS = 20;
    public static final int MIN_SCORE = -100;
    public static final int MAX_SCORE = 100;
    public static final int FAMILY_START = 40;
    public static final int TALK = 2;
    public static final int QUARREL = -4;
    public static final double QUARREL_CHANCE = 0.1;
    public static final double QUARREL_CHANCE_UNHAPPY = 0.25;
    public static final double QUARREL_CHANCE_ENEMY = 0.5;
    public static final float UNHAPPY_BELOW = 30;
    public static final int FRIEND = 50;
    public static final int ENEMY = -50;
    public static final int MAX_TALKS_PER_PAIR_DAY = 3;
    public static final int COLLEAGUE_DAY = 1;
    public static final int THEFT = -15;
    public static final int THEFT_GUARD = -30;
    public static final int HIT = -10;
    public static final int FORGET_AFTER_DAYS = 7;
    public static final int MAX_CATCH_UP_DAYS = 30;
    public static final int VIRTUAL_TALKS = 2;
    public static final long TALK_COOLDOWN = 600;
    public static final double TALK_DISTANCE = 4;
    public static final double FRIEND_DISTANCE = 32;
    public static final float FRIEND_NEAR_DAY = 1;
    public static final float ENEMY_AT_HOME_DAY = -1;
    public static final float LOST_FRIEND = -10;
    public static final int MARRIAGE_MIN = 30;

    public enum Mode {
        NATURAL,
        FORCED,
        VIRTUAL
    }

    private Social() {
    }

    public static void update(VillagerEntityMCA villager) {
        if (!(villager.getWorld() instanceof ServerWorld world) || !canTalk(villager)) {
            return;
        }
        SocialRegistry registry = SocialRegistry.get(world.getServer());
        if (world.getTime() - registry.lastTalk(villager.getUuid()) < TALK_COOLDOWN) {
            return;
        }
        VillagerEntityMCA partner = findPartner(world, villager);
        if (partner != null) {
            talk(world, villager.getUuid(), partner.getUuid(), Mode.NATURAL);
        }
    }

    private static boolean canTalk(VillagerEntityMCA villager) {
        if (!villager.isAlive() || villager.isSleeping() || villager.getAgeState() == AgeState.BABY) {
            return false;
        }
        Optional<Activity> activity = villager.getBrain().getFirstPossibleNonCoreActivity();
        return activity.isPresent() && (activity.get() == Activity.IDLE || activity.get() == Activity.MEET || activity.get() == Activity.PLAY
                || activity.get() == Activity.WORK);
    }

    @Nullable
    private static VillagerEntityMCA findPartner(ServerWorld world, VillagerEntityMCA villager) {
        double max = TALK_DISTANCE * TALK_DISTANCE;
        Optional<?> target = villager.getBrain().getOptionalRegisteredMemory(MemoryModuleType.INTERACTION_TARGET);
        if (target.isPresent() && target.get() instanceof VillagerEntityMCA other && other != villager
                && villager.squaredDistanceTo(other) <= max && canTalk(other) && villager.canSee(other)) {
            return other;
        }
        return world.getEntitiesByClass(VillagerEntityMCA.class, villager.getBoundingBox().expand(TALK_DISTANCE), e -> e != villager && canTalk(e)).stream()
                .filter(e -> villager.squaredDistanceTo(e) <= max && villager.canSee(e))
                .min(Comparator.comparingDouble(villager::squaredDistanceTo))
                .orElse(null);
    }

    public static int talk(ServerWorld world, UUID a, UUID b, Mode mode) {
        SocialRegistry registry = SocialRegistry.get(world.getServer());
        long time = world.getTime();
        long today = WorldCalendar.now(world).dayIndex();
        boolean capped = false;
        if (mode == Mode.NATURAL) {
            if (time - registry.lastTalk(b) < TALK_COOLDOWN) {
                return 0;
            }
            registry.setLastTalk(a, time);
            registry.setLastTalk(b, time);
            Link link = registry.get(a, b);
            capped = link != null && link.talkDay == today && link.talks >= MAX_TALKS_PER_PAIR_DAY;
        } else if (mode == Mode.FORCED) {
            registry.setLastTalk(a, time);
            registry.setLastTalk(b, time);
        } else if (score(registry, a, b) <= ENEMY || score(registry, b, a) <= ENEMY) {
            return 0;
        }
        int delta = capped ? TALK : world.getRandom().nextDouble() < quarrelChance(world, a, b) ? QUARREL : TALK;
        if (!capped) {
            countTalk(change(world, a, b, delta, today, true), today);
            countTalk(change(world, b, a, delta, today, true), today);
        }
        if (mode != Mode.VIRTUAL && world.getEntity(a) instanceof VillagerEntityMCA first && world.getEntity(b) instanceof VillagerEntityMCA second) {
            Link link = registry.get(a, b);
            SocialChat.show(world, first, second, delta, Math.min(score(registry, a, b), score(registry, b, a)), link != null && link.family);
        }
        return capped ? 0 : delta;
    }

    private static void countTalk(@Nullable Link link, long today) {
        if (link == null) {
            return;
        }
        if (link.talkDay != today) {
            link.talkDay = today;
            link.talks = 0;
        }
        link.talks++;
    }

    private static double quarrelChance(ServerWorld world, UUID a, UUID b) {
        SocialRegistry registry = SocialRegistry.get(world.getServer());
        if (score(registry, a, b) <= ENEMY || score(registry, b, a) <= ENEMY) {
            return QUARREL_CHANCE_ENEMY;
        }
        WellbeingRegistry wellbeing = WellbeingRegistry.get(world.getServer());
        if (unhappy(wellbeing, a) || unhappy(wellbeing, b)) {
            return QUARREL_CHANCE_UNHAPPY;
        }
        return QUARREL_CHANCE;
    }

    private static boolean unhappy(WellbeingRegistry wellbeing, UUID uuid) {
        WellbeingRegistry.Record record = wellbeing.get(uuid);
        return record != null && record.happiness < UNHAPPY_BELOW;
    }

    public static int score(SocialRegistry registry, UUID owner, UUID other) {
        Link link = registry.get(owner, other);
        return link == null ? 0 : link.score;
    }

    @Nullable
    public static Link change(ServerWorld world, UUID owner, UUID other, int delta, long today, boolean met) {
        if (owner.equals(other)) {
            return null;
        }
        SocialRegistry registry = SocialRegistry.get(world.getServer());
        boolean family = isCloseFamily(world, owner, other);
        Link link = registry.get(owner, other);
        if (link == null) {
            int initial = family ? FAMILY_START : 0;
            link = registry.create(owner, other, initial, initial + delta, family);
            if (link == null) {
                return null;
            }
            link.lastSeen = today;
        } else if (link.family != family) {
            link.family = family;
            if (family) {
                link.score = Math.max(link.score, FAMILY_START);
            }
        }
        link.score = Math.max(MIN_SCORE, Math.min(MAX_SCORE, link.score + delta));
        if (met) {
            link.lastSeen = today;
        }
        registry.markDirty();
        return link;
    }

    private static boolean isCloseFamily(ServerWorld world, UUID owner, UUID other) {
        Optional<FamilyTreeNode> found = FamilyTree.get(world).getOrEmpty(owner);
        if (found.isEmpty()) {
            return false;
        }
        FamilyTreeNode node = found.get();
        return other.equals(node.partner()) || other.equals(node.father()) || other.equals(node.mother())
                || node.children().contains(other) || node.siblings().contains(other);
    }

    public static void daily(Village village, ServerWorld world, long today) {
        SocialRegistry registry = SocialRegistry.get(world.getServer());
        registry.forgetUntil(today, world.getTime());
        WorkRegistry work = WorkRegistry.get(world.getServer());
        List<UUID> residents = village.getResidentsUUIDs().toList();
        Random random = world.getRandom();

        if (residents.size() > 1) {
            for (UUID resident : residents) {
                if (world.getEntity(resident) != null) {
                    continue;
                }
                for (int i = 0; i < VIRTUAL_TALKS; i++) {
                    UUID other = residents.get(random.nextInt(residents.size()));
                    if (!other.equals(resident)) {
                        talk(world, resident, other, Mode.VIRTUAL);
                    }
                }
            }
        }

        List<UUID> workers = new ArrayList<>();
        for (UUID resident : residents) {
            WorkRegistry.Record record = work.get(resident);
            if (record != null && record.job != Job.NONE && record.job != Job.RETIRED && record.center != null) {
                workers.add(resident);
            }
        }
        for (int i = 0; i < workers.size(); i++) {
            WorkRegistry.Record a = work.get(workers.get(i));
            for (int j = i + 1; j < workers.size(); j++) {
                if (areColleagues(a, work.get(workers.get(j)))) {
                    change(world, workers.get(i), workers.get(j), COLLEAGUE_DAY, today, true);
                    change(world, workers.get(j), workers.get(i), COLLEAGUE_DAY, today, true);
                }
            }
        }
    }

    public static void theft(Village village, ServerWorld world, UUID thief) {
        long today = WorldCalendar.now(world).dayIndex();
        WorkRegistry work = WorkRegistry.get(world.getServer());
        for (UUID resident : village.getResidentsUUIDs().toList()) {
            if (resident.equals(thief)) {
                continue;
            }
            Job job = work.getJob(resident);
            boolean guard = job == Job.GUARD || job == Job.ARCHER
                    || world.getEntity(resident) instanceof VillagerEntityMCA v && v.isGuard();
            change(world, resident, thief, guard ? THEFT_GUARD : THEFT, today, false);
        }
        if (world.getEntity(thief) instanceof VillagerEntityMCA caught) {
            SocialConversation.witness(world, caught, "theft", caught.getName(), thief);
        }
    }

    public static void hit(ServerWorld world, UUID victim, UUID attacker) {
        long today = WorldCalendar.now(world).dayIndex();
        Link existing = SocialRegistry.get(world.getServer()).get(victim, attacker);
        if (existing != null && existing.hitDay == today) {
            return;
        }
        Link link = change(world, victim, attacker, HIT, today, false);
        if (link != null) {
            link.hitDay = today;
        }
        net.minecraft.entity.Entity victimEntity = world.getEntity(victim);
        net.minecraft.entity.Entity attackerEntity = world.getEntity(attacker);
        if (victimEntity != null && attackerEntity != null) {
            SocialConversation.witness(world, victimEntity, "hit", attackerEntity.getName(), victim, attacker);
        }
    }

    public static void died(ServerWorld world, UUID dead, boolean familyMourned) {
        if (world.getEntity(dead) instanceof VillagerEntityMCA deceased) {
            SocialConversation.witness(world, deceased, "death", deceased.getName(), dead);
        }
        SocialRegistry registry = SocialRegistry.get(world.getServer());
        Optional<FamilyTreeNode> node = FamilyTree.get(world).getOrEmpty(dead);
        for (UUID owner : registry.owners()) {
            if (score(registry, owner, dead) >= FRIEND && !(familyMourned && node.map(n -> alreadyMourns(n, owner)).orElse(false))) {
                WellbeingRegistry.get(world.getServer()).addHappiness(owner, LOST_FRIEND);
            }
        }
        registry.remove(dead);
        SocialChat.forget(dead);
    }

    private static boolean alreadyMourns(FamilyTreeNode dead, UUID owner) {
        return owner.equals(dead.partner()) || owner.equals(dead.father()) || owner.equals(dead.mother()) || dead.children().contains(owner);
    }

    public static boolean isFriendNear(ServerWorld world, VillagerEntityMCA villager) {
        Map<UUID, Link> links = SocialRegistry.get(world.getServer()).links(villager.getUuid());
        if (links.isEmpty()) {
            return false;
        }
        double max = FRIEND_DISTANCE * FRIEND_DISTANCE;
        for (Map.Entry<UUID, Link> entry : links.entrySet()) {
            if (entry.getValue().score >= FRIEND && world.getEntity(entry.getKey()) instanceof VillagerEntityMCA friend
                    && friend.isAlive() && friend.squaredDistanceTo(villager) <= max) {
                return true;
            }
        }
        return false;
    }

    public record House(Building building, Map<UUID, Long> homes) {
    }

    public static Optional<House> house(VillagerEntityMCA villager) {
        Optional<Village> village = villager.getResidency().getHomeVillage().filter(v -> v.hasResident(villager.getUuid()));
        if (village.isEmpty()) {
            return Optional.empty();
        }
        village.get().updateResident(villager);
        Map<UUID, Long> homes = ((VillageAccessor) village.get()).mcavie$getResidentHomes();
        Long own = homes.get(villager.getUuid());
        if (own == null) {
            return Optional.empty();
        }
        return village.get().getBuildingAt(BlockPos.fromLong(own)).map(building -> new House(building, homes));
    }

    public static boolean livesIn(ServerWorld world, House house, UUID uuid) {
        Optional<BlockPos> home = world.getEntity(uuid) instanceof VillagerEntityMCA other
                ? other.getResidency().getHome().filter(h -> h.getDimension() == world.getRegistryKey()).map(h -> h.getPos())
                : Optional.ofNullable(house.homes().get(uuid)).map(BlockPos::fromLong);
        return home.isPresent() && house.building().containsPos(home.get());
    }

    public static boolean hasEnemyAtHome(ServerWorld world, VillagerEntityMCA villager) {
        Optional<House> house = house(villager);
        if (house.isEmpty()) {
            return false;
        }
        for (Map.Entry<UUID, Link> entry : SocialRegistry.get(world.getServer()).links(villager.getUuid()).entrySet()) {
            if (entry.getValue().score <= ENEMY && livesIn(world, house.get(), entry.getKey())) {
                return true;
            }
        }
        return false;
    }

    public static String displayName(ServerWorld world, UUID uuid) {
        java.util.Optional<FamilyTreeNode> node = FamilyTree.get(world).getOrEmpty(uuid);
        String name = node.map(FamilyTreeNode::getName).orElse(uuid.toString().substring(0, 8));
        String surname = SurnameRegistry.get(world.getServer()).get(uuid);
        if (surname != null && node.isPresent()) {
            surname = SurnameText.display(surname, node.get().gender());
        }
        return SurnameText.withSurname(name, surname);
    }

    public static boolean areColleagues(WorkRegistry.Record a, WorkRegistry.Record b) {
        if (a == null || b == null || a.job != b.job || a.job == Job.NONE || a.job == Job.RETIRED || a.center == null || b.center == null) {
            return false;
        }
        long reach = (long) a.radius + b.radius;
        return a.center.getSquaredDistance(b.center) <= (double) reach * reach;
    }

    public static Optional<VillagerEntityMCA> chooseSpouse(ServerWorld world, VillagerEntityMCA suitor, List<VillagerEntityMCA> candidates) {
        SocialRegistry registry = SocialRegistry.get(world.getServer());
        UUID id = suitor.getUuid();
        VillagerEntityMCA best = null;
        int bestScore = MARRIAGE_MIN - 1;
        VillagerEntityMCA fallback = null;
        for (VillagerEntityMCA candidate : candidates) {
            if (!com.maitreyoshi.mcavie.life.Orientation.compatible(suitor, candidate)) {
                continue;
            }
            int mine = score(registry, id, candidate.getUuid());
            int theirs = score(registry, candidate.getUuid(), id);
            if (mine <= ENEMY || theirs <= ENEMY) {
                continue;
            }
            if (fallback == null) {
                fallback = candidate;
            }
            int mutual = Math.min(mine, theirs);
            if (mutual > bestScore) {
                best = candidate;
                bestScore = mutual;
            }
        }
        return Optional.ofNullable(best != null ? best : fallback);
    }
}
