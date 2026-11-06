package com.maitreyoshi.mcavie.life;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.Surnames;
import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.social.Social;
import com.maitreyoshi.mcavie.social.SocialRegistry;
import com.maitreyoshi.mcavie.tree.server.mixin.FamilyTreeAccessor;
import com.maitreyoshi.mcavie.village.CivilRegistry;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.VillagerFactory;
import net.mca.entity.ai.relationship.Gender;
import net.mca.entity.ai.relationship.RelationshipState;
import net.mca.resources.Names;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageManager;
import net.mca.util.WorldUtils;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class History {
    public static final long MAX_DAYS = 36525;
    public static final int MARRIAGE_AGE = 18;
    public static final int MAX_AGE_GAP = 15;
    public static final int FERTILE_FROM = 18;
    public static final double MARRY_PER_DAY = 1.0 / (5 * 365);
    public static final double BIRTH_PER_DAY = 1.0 / (2.5 * 365);
    public static final int MIN_BIRTH_GAP = 400;
    public static final int MAX_POPULATION = 150;
    private static final int SPAWNS_PER_TICK = 4;
    private static final double STEP_DAYS = 30;
    private static final int NO_VILLAGE = Integer.MIN_VALUE;

    private static final class Person {
        final UUID uuid;
        final FamilyTreeNode node;
        final long birth;
        final float happiness;
        final int village;
        final boolean bornInSim;
        final boolean playerSpouse;
        long start;
        boolean alive = true;
        long lastChild;
        Orientation orientation;

        Person(UUID uuid, FamilyTreeNode node, long birth, float happiness, int village, boolean bornInSim, boolean playerSpouse, long lastChild) {
            this.uuid = uuid;
            this.node = node;
            this.birth = birth;
            this.happiness = happiness;
            this.village = village;
            this.bornInSim = bornInSim;
            this.playerSpouse = playerSpouse;
            this.lastChild = lastChild;
        }

        double age(long day) {
            return WorldCalendar.yearsBetween(birth, day);
        }

        boolean female() {
            return node.gender() == Gender.FEMALE;
        }

        Orientation orientation(ServerWorld world, Random random) {
            if (orientation == null) {
                orientation = Orientation.known(world, uuid, random);
            }
            return orientation;
        }
    }

    private static final class Counts {
        int births;
        int marriages;
        int deaths;
        int alive;
    }

    private History() {
    }

    public static void simulate(ServerWorld world, long fromDay, long toDay) {
        if (toDay - fromDay < 2) {
            return;
        }
        long first = fromDay + 1;
        long last = Math.min(toDay - 1, fromDay + maxGameDays(fromDay));

        Map<Integer, Village> villages = new HashMap<>();
        Map<UUID, Integer> villageOf = new HashMap<>();
        for (Village village : VillageManager.get(world)) {
            villages.put(village.getId(), village);
            village.getResidentsUUIDs().forEach(uuid -> villageOf.put(uuid, village.getId()));
        }
        HistoryRegistry history = HistoryRegistry.get(world.getServer());
        Map<UUID, FamilyTreeNode> entries = ((FamilyTreeAccessor) FamilyTree.get(world)).mcavie$getEntries();
        history.purgePendingDeaths(uuid -> {
            FamilyTreeNode node = entries.get(uuid);
            return node == null || !node.isDeceased();
        });
        history.origins().forEach((uuid, id) -> {
            if (villages.containsKey(id)) {
                villageOf.putIfAbsent(uuid, id);
            }
        });

        Map<UUID, Person> people = collect(world, villageOf, first);
        Map<Integer, Counts> counts = new LinkedHashMap<>();
        for (Person p : people.values()) {
            if (p.village != NO_VILLAGE) {
                counts.computeIfAbsent(p.village, k -> new Counts()).alive++;
            }
        }
        Random random = world.getRandom();

        long earliest = first;
        for (Person p : people.values()) {
            p.start = Math.max(Math.max(p.start, p.birth), WorldCalendar.gameDayOf(WorldCalendar.calendarDay(toDay - 1) - 3 * MAX_DAYS));
            earliest = Math.min(earliest, p.start);
        }

        List<Person> living = new ArrayList<>();
        for (Person p : people.values()) {
            if (p.alive) {
                living.add(p);
            }
        }
        long day = earliest;
        while (day <= last) {
            long limit = day < first ? first : last + 1;
            long end = Math.min(limit, day + Math.max(1, (long) (STEP_DAYS / WorldCalendar.calendarDaysPerGameDay(day))));
            int length = (int) (end - day);
            for (Person p : living) {
                if (end <= p.start) {
                    continue;
                }
                long from = Math.max(day, p.start);
                long when = from + random.nextInt((int) (end - from));
                if (p.age(end) >= Wellbeing.MAX_AGE || random.nextDouble() < Wellbeing.overDays(Wellbeing.oldAgeDeathChance(p.age(from), p.happiness), WorldCalendar.calendarDaysBetween(from, end))) {
                    die(world, villages.get(p.village), p, when);
                    if (p.village != NO_VILLAGE) {
                        Counts c = counts.get(p.village);
                        c.deaths++;
                        c.alive--;
                    }
                }
            }
            living.removeIf(p -> !p.alive);
            if (day < first) {
                day = end;
                continue;
            }
            double days = WorldCalendar.calendarDaysBetween(day, end);
            List<Person> present = new ArrayList<>(living);
            for (Person single : present) {
                long when = day + random.nextInt(length);
                if (!single.alive || single.village == NO_VILLAGE || single.playerSpouse || !available(single, when)
                        || random.nextDouble() >= Wellbeing.overDays(MARRY_PER_DAY, days) || single.orientation(world, random) == Orientation.ASEXUAL) {
                    continue;
                }
                Person partner = choosePartner(world, single, living, when, random);
                if (partner == null) {
                    partner = outsider(world, villages.get(single.village), single, when, random);
                    people.put(partner.uuid, partner);
                    living.add(partner);
                    counts.get(single.village).alive++;
                }
                Person husband = single.female() ? partner : single;
                Person wife = husband == single ? partner : single;
                husband.node.updatePartner(wife.node);
                wife.node.updatePartner(husband.node);
                CivilRegistry.record(villages.get(single.village), CivilRegistry.MARRIAGE, when, husband.node.getName(), wife.node.getName(), 0);
                counts.get(single.village).marriages++;
            }
            for (Person mother : present) {
                long when = day + random.nextInt(length);
                if (!mother.alive || !mother.female() || mother.village == NO_VILLAGE || WorldCalendar.calendarDaysBetween(mother.lastChild, when) < MIN_BIRTH_GAP
                        || mother.node.getRelationshipState() != RelationshipState.MARRIED_TO_VILLAGER) {
                    continue;
                }
                double age = mother.age(when);
                if (age < FERTILE_FROM || age >= LifeCycle.MENOPAUSE_AGE) {
                    continue;
                }
                Counts c = counts.get(mother.village);
                if (c.alive >= MAX_POPULATION || random.nextDouble() >= Wellbeing.overDays(BIRTH_PER_DAY / (mother.node.children().size() + 1), days)) {
                    continue;
                }
                Person father = people.get(mother.node.partner());
                if (father == null || !father.alive || father.female()) {
                    continue;
                }
                Person child = birth(world, villages.get(mother.village), mother, father, when, random);
                people.put(child.uuid, child);
                living.add(child);
                mother.lastChild = when;
                c.births++;
                c.alive++;
            }
            day = end;
        }

        WellbeingRegistry wellbeing = WellbeingRegistry.get(world.getServer());
        for (Person p : people.values()) {
            WellbeingRegistry.Record record = wellbeing.get(p.uuid);
            if (p.alive && record != null) {
                record.knownRelationshipState = p.node.getRelationshipState().ordinal();
                record.knownChildren = p.node.children().size();
                record.lastProcessedDay = Math.max(record.lastProcessedDay, toDay - 1);
            }
        }
        wellbeing.markDirty();
        FamilyTree.get(world).markDirty();

        int years = (int) Math.round(WorldCalendar.yearsBetween(fromDay, toDay));
        counts.forEach((id, c) -> {
            Village village = villages.get(id);
            if (village == null || c.births + c.marriages + c.deaths == 0) {
                return;
            }
            village.markDirty();
            Text text = Text.translatable("msg.mca_vie.history.summary", village.getName(), years, c.births, c.marriages, c.deaths)
                    .formatted(Formatting.GOLD);
            world.getServer().getPlayerManager().getPlayerList().forEach(player -> player.sendMessage(text, false));
            MCAVie.LOGGER.info("Histoire de {} : {} naissances, {} mariages, {} décès", village.getName(), c.births, c.marriages, c.deaths);
        });
    }

    public static long maxGameDays(long fromDay) {
        return WorldCalendar.gameDaysFor(fromDay, MAX_DAYS);
    }

    private static Map<UUID, Person> collect(ServerWorld world, Map<UUID, Integer> villageOf, long firstDay) {
        LifeRegistry life = LifeRegistry.get(world.getServer());
        WellbeingRegistry wellbeing = WellbeingRegistry.get(world.getServer());
        HistoryRegistry history = HistoryRegistry.get(world.getServer());
        Set<UUID> pendingSpawns = new HashSet<>();
        history.pendingSpawns().forEach(s -> pendingSpawns.add(s.uuid()));

        Map<UUID, Person> people = new HashMap<>();
        Map<UUID, FamilyTreeNode> entries = ((FamilyTreeAccessor) FamilyTree.get(world)).mcavie$getEntries();
        for (FamilyTreeNode node : new ArrayList<>(entries.values())) {
            UUID uuid = node.id();
            Long birth = life.getBodyBirthDay(uuid);
            if (birth == null || node.isDeceased() || node.isPlayer() || life.getDeathDay(uuid) != null || history.pendingDeath(uuid) != null
                    || life.isZombie(uuid)) {
                continue;
            }
            WellbeingRegistry.Record record = wellbeing.get(uuid);
            long lastChild = node.children().stream().map(life::getBirthDay).filter(Objects::nonNull).max(Long::compare).orElse(Long.MIN_VALUE / 2);
            Person person = new Person(uuid, node, birth, record == null ? 60 : record.happiness,
                    villageOf.getOrDefault(uuid, NO_VILLAGE), pendingSpawns.contains(uuid),
                    node.getRelationshipState() == RelationshipState.MARRIED_TO_PLAYER, lastChild);
            person.start = record == null ? firstDay : Math.min(firstDay, record.lastProcessedDay + 1);
            people.put(uuid, person);
        }
        return people;
    }

    private static boolean available(Person p, long day) {
        RelationshipState state = p.node.getRelationshipState();
        return (state == RelationshipState.SINGLE || state == RelationshipState.WIDOW) && p.age(day) >= MARRIAGE_AGE;
    }

    private static Person choosePartner(ServerWorld world, Person man, List<Person> living, long day, Random random) {
        SocialRegistry social = SocialRegistry.get(world.getServer());
        FamilyTree tree = FamilyTree.get(world);
        Set<UUID> manLine = lineage(tree, man.node);
        List<Person> candidates = new ArrayList<>();
        Person best = null;
        int bestScore = Social.MARRIAGE_MIN - 1;
        for (Person woman : living) {
            if (!woman.alive || woman == man || woman.village != man.village || woman.playerSpouse || !available(woman, day)
                    || Math.abs(woman.age(day) - man.age(day)) > MAX_AGE_GAP
                    || !Orientation.compatible(man.orientation(world, random), man.node.gender(), woman.orientation(world, random), woman.node.gender())) {
                continue;
            }
            Set<UUID> womanLine = lineage(tree, woman.node);
            womanLine.retainAll(manLine);
            if (!womanLine.isEmpty()) {
                continue;
            }
            int mine = Social.score(social, man.uuid, woman.uuid);
            int theirs = Social.score(social, woman.uuid, man.uuid);
            if (mine <= Social.ENEMY || theirs <= Social.ENEMY) {
                continue;
            }
            candidates.add(woman);
            if (Math.min(mine, theirs) > bestScore) {
                best = woman;
                bestScore = Math.min(mine, theirs);
            }
        }
        if (best != null) {
            return best;
        }
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    private static Person outsider(ServerWorld world, Village village, Person spouse, long day, Random random) {
        Orientation wanted = spouse.orientation(world, random);
        boolean sameSex = wanted == Orientation.GAY || wanted == Orientation.BISEXUAL && random.nextBoolean();
        Gender gender = spouse.female() != sameSex ? Gender.MALE : Gender.FEMALE;
        double age = Math.max(MARRIAGE_AGE, spouse.age(day) + random.nextInt(11) - 5);
        long birth = WorldCalendar.gameDayOf(WorldCalendar.calendarDay(day) - (long) (age * LifeCycle.DAYS_PER_YEAR) - random.nextInt(365));
        UUID uuid = UUID.randomUUID();
        String firstName = Names.pickCitizenName(gender);
        FamilyTreeNode node = FamilyTree.get(world).getOrCreate(uuid, firstName, gender);
        Surnames.assign(world, node);
        LifeRegistry.get(world.getServer()).setBirthDay(uuid, birth);
        HistoryRegistry history = HistoryRegistry.get(world.getServer());
        history.addSpawn(new HistoryRegistry.Spawn(uuid, village.getId(), firstName, gender));
        history.setOrigin(uuid, village.getId());
        Person person = new Person(uuid, node, birth, 60, village.getId(), true, false, Long.MIN_VALUE / 2);
        person.start = day;
        person.orientation = sameSex ? wanted : Orientation.STRAIGHT;
        Orientation.assign(world, uuid, person.orientation);
        return person;
    }

    private static Set<UUID> lineage(FamilyTree tree, FamilyTreeNode node) {
        Set<UUID> line = new HashSet<>();
        line.add(node.id());
        for (UUID parent : new UUID[]{node.father(), node.mother()}) {
            if (!FamilyTreeNode.isValid(parent)) {
                continue;
            }
            line.add(parent);
            tree.getOrEmpty(parent).ifPresent(p -> {
                if (FamilyTreeNode.isValid(p.father())) {
                    line.add(p.father());
                }
                if (FamilyTreeNode.isValid(p.mother())) {
                    line.add(p.mother());
                }
            });
        }
        return line;
    }

    private static void die(ServerWorld world, Village village, Person p, long day) {
        p.alive = false;
        p.node.setDeceased(true);
        if (FamilyTreeNode.isValid(p.node.partner())) {
            FamilyTree.get(world).getOrEmpty(p.node.partner()).ifPresent(partner -> {
                partner.updatePartner(null, RelationshipState.WIDOW);
                p.node.setRelationshipState(RelationshipState.WIDOW);
            });
        }
        LifeRegistry.get(world.getServer()).setDeathDay(p.uuid, day);
        if (village != null) {
            CivilRegistry.record(village, CivilRegistry.DEATH, day, p.node.getName(), "", LifeCycle.ageInYears(p.birth, day));
        }
        SocialRegistry.get(world.getServer()).remove(p.uuid);
        LifeCycle.forgetDead(world, p.uuid);
        HistoryRegistry history = HistoryRegistry.get(world.getServer());
        history.removeOrigin(p.uuid);
        if (p.bornInSim) {
            history.removeSpawn(p.uuid);
        } else {
            history.addPendingDeath(p.uuid, day);
        }
    }

    private static Person birth(ServerWorld world, Village village, Person mother, Person father, long day, Random random) {
        FamilyTree tree = FamilyTree.get(world);
        Gender gender = random.nextBoolean() ? Gender.MALE : Gender.FEMALE;
        UUID uuid = UUID.randomUUID();
        String firstName = Names.pickCitizenName(gender);
        FamilyTreeNode node = tree.getOrCreate(uuid, firstName, gender);
        node.setFather(father.node);
        node.setMother(mother.node);
        Surnames.assign(world, node);
        LifeRegistry.get(world.getServer()).setBirthDay(uuid, day);
        CivilRegistry.record(village, CivilRegistry.BIRTH, day, node.getName(), father.node.getName() + " & " + mother.node.getName(), 0);
        HistoryRegistry history = HistoryRegistry.get(world.getServer());
        history.addSpawn(new HistoryRegistry.Spawn(uuid, village.getId(), firstName, gender));
        history.setOrigin(uuid, village.getId());
        return new Person(uuid, node, day, 60, village.getId(), true, false, Long.MIN_VALUE / 2);
    }

    public static boolean applyPendingDeath(VillagerEntityMCA villager, ServerWorld world) {
        if (HistoryRegistry.get(world.getServer()).pendingDeath(villager.getUuid()) == null) {
            return false;
        }
        villager.kill();
        return true;
    }

    public static void spawnPending(Village village, ServerWorld world) {
        HistoryRegistry history = HistoryRegistry.get(world.getServer());
        List<HistoryRegistry.Spawn> mine = new ArrayList<>();
        for (HistoryRegistry.Spawn spawn : history.pendingSpawns()) {
            if (spawn.village() == village.getId()) {
                mine.add(spawn);
                if (mine.size() >= SPAWNS_PER_TICK) {
                    break;
                }
            }
        }
        if (mine.isEmpty()) {
            return;
        }
        BlockPos base = new BlockPos(village.getCenter());
        if (!world.isChunkLoaded(base) || !world.shouldTickEntity(base)) {
            return;
        }
        BlockPos center = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, base);
        for (HistoryRegistry.Spawn spawn : mine) {
            if (world.getEntity(spawn.uuid()) != null || LifeRegistry.get(world.getServer()).getDeathDay(spawn.uuid()) != null) {
                history.removeSpawn(spawn.uuid());
                continue;
            }
            VillagerEntityMCA child = VillagerFactory.newVillager(world).withGender(spawn.gender()).withName(spawn.name()).withAge(0).withPosition(center).build();
            UUID temporary = child.getUuid();
            child.setUuid(spawn.uuid());
            FamilyTree.get(world).remove(temporary);
            WorldUtils.spawnEntity(world, child, SpawnReason.BREEDING);
            FamilyTreeNode node = child.getRelationships().getFamilyEntry();
            if (world.getEntity(node.mother()) instanceof VillagerEntityMCA mother && world.getEntity(node.father()) instanceof VillagerEntityMCA father) {
                child.getGenetics().combine(mother.getGenetics(), father.getGenetics());
            } else {
                child.getGenetics().randomize();
            }
            child.getTraits().randomize();
            if (world.getEntity(spawn.uuid()) == child) {
                history.removeSpawn(spawn.uuid());
            }
        }
    }
}
