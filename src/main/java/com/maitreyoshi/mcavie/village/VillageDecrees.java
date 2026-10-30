package com.maitreyoshi.mcavie.village;

import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.life.LifeCycle;
import com.maitreyoshi.mcavie.life.LifeRegistry;
import com.maitreyoshi.mcavie.life.Wellbeing;
import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkManager;
import com.maitreyoshi.mcavie.work.WorkRegistry;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.relationship.AgeState;
import net.mca.server.world.data.Building;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public final class VillageDecrees {
    private VillageDecrees() {
    }

    public static long period(Decree decree, long dayIndex) {
        LocalDate date = WorldCalendar.dateOfDay(dayIndex);
        if (decree.kind == Decree.Kind.SEASONAL) {
            return date.getYear() * 4L + (date.getMonthValue() - 1) / 3;
        }
        return date.getYear() * 12L + date.getMonthValue() - 1;
    }

    public static long launchCost(Village village, Decree decree) {
        VillageConfig config = VillageConfig.get();
        int population = village.getPopulation();
        return switch (decree) {
            case FEAST -> (long) Math.ceil(population * config.feastCostPerResident);
            case SETTLERS_CALL -> (long) Math.ceil(population * config.settlersCallCostPerResident);
            case GRAND_FESTIVAL -> (long) Math.ceil(population * config.festivalCostPerResident);
            case RECRUIT_GUARD -> config.guardCost;
            default -> 0;
        };
    }

    public static long renovationCost(Village village, Building building) {
        VillageConfig.BuildingRule rule = VillageConfig.get().building(building.getType());
        if (rule == null) {
            return 0;
        }
        VillageRank rank = VillageState.of(village).rank;
        return (long) Math.ceil(rule.upkeep * rank.rule().taxMultiplier * VillageConfig.get().renovationWeeks);
    }

    public static MutableText unavailable(Village village, Decree decree, long today) {
        VillageState state = VillageState.of(village);
        if (decree.kind == Decree.Kind.TOGGLE && state.activeDecrees.contains(decree)) {
            return null;
        }
        if (!state.rank.isAtLeast(decree.rank)) {
            return Text.translatable("gui.mca_vie.decree.need_rank", decree.rank.getName());
        }
        if (decree.kind == Decree.Kind.TOGGLE) {
            long first = weeklyCost(village, decree, null, 0);
            return first > state.treasury ? Text.translatable("gui.mca_vie.decree.no_money", first) : null;
        }
        if (decree == Decree.SETTLERS_CALL && !VillageImmigration.hasRoom(village, state)) {
            return Text.translatable("gui.mca_vie.decree.no_room");
        }
        Long last = state.lastLaunch.get(decree);
        if (last != null && last == period(decree, today)) {
            return Text.translatable(decree.kind == Decree.Kind.SEASONAL ? "gui.mca_vie.decree.done_season" : "gui.mca_vie.decree.done_month");
        }
        long cost = launchCost(village, decree);
        if (cost > state.treasury) {
            return Text.translatable("gui.mca_vie.decree.no_money", cost);
        }
        return null;
    }

    public static Optional<Village> residentVillage(VillagerEntityMCA villager) {
        if (!(villager.getWorld() instanceof ServerWorld world)) {
            return Optional.empty();
        }
        return villager.getResidency().getHomeVillage().filter(v -> v.hasResident(villager.getUuid()));
    }

    public static void launch(ServerPlayerEntity player, int villageId, Decree decree) {
        ServerWorld world = player.getServerWorld();
        VillageManager.get(world).getOrEmpty(villageId).ifPresent(village -> {
            if (!VillageRights.personalRank(village, player).isAtLeast(VillageRights.DECREES)) {
                player.sendMessage(Text.translatable("msg.mca_vie.rights.denied", Text.translatable(VillageRights.DECREES.getTranslationKey())), true);
                return;
            }
            VillageState state = VillageState.of(village);
            long today = WorldCalendar.now(world).dayIndex();
            MutableText reason = unavailable(village, decree, today);
            if (reason != null) {
                player.sendMessage(reason.formatted(Formatting.RED), true);
                return;
            }

            if (decree.kind == Decree.Kind.TOGGLE) {
                boolean on = !state.activeDecrees.remove(decree);
                if (on) {
                    long first = weeklyCost(village, decree, world, today);
                    if (first > state.treasury) {
                        state.activeDecrees.remove(decree);
                        player.sendMessage(Text.translatable("gui.mca_vie.decree.no_money", first).formatted(Formatting.RED), true);
                        return;
                    }
                    state.treasury -= first;
                    state.decreeSpending += first;
                    state.activeDecrees.add(decree);
                    state.paidWeek.put(decree, Math.floorDiv(today, 7));
                }
                VillageServer.notify(world, village, Text.translatable(on ? "msg.mca_vie.decree.on" : "msg.mca_vie.decree.off",
                        village.getName(), decree.getName()).formatted(Formatting.GOLD));
                village.markDirty();
                return;
            }

            long cost;
            switch (decree) {
                case RENOVATION -> {
                    Optional<Building> building = village.getBuildings().values().stream()
                            .filter(b -> b.containsPos(player.getBlockPos()))
                            .filter(b -> state.isDecayed(village, b))
                            .findAny();
                    if (building.isEmpty()) {
                        player.sendMessage(Text.translatable("msg.mca_vie.decree.no_decayed").formatted(Formatting.RED), true);
                        return;
                    }
                    cost = renovationCost(village, building.get());
                    if (cost > state.treasury) {
                        player.sendMessage(Text.translatable("gui.mca_vie.decree.no_money", cost).formatted(Formatting.RED), true);
                        return;
                    }
                    state.clearDecay(village, building.get());
                }
                case RECRUIT_GUARD -> {
                    cost = launchCost(village, decree);
                    if (!recruitGuard(village, world, state)) {
                        player.sendMessage(Text.translatable("msg.mca_vie.decree.no_guard").formatted(Formatting.RED), true);
                        return;
                    }
                }
                default -> {
                    cost = launchCost(village, decree);
                    state.lastLaunch.put(decree, period(decree, today));
                    applyEffect(village, world, state, decree);
                }
            }
            state.treasury -= cost;
            state.decreeSpending += cost;
            VillageServer.notify(world, village, Text.translatable("msg.mca_vie.decree.launched",
                    village.getName(), decree.getName(), cost).formatted(Formatting.GOLD));
            village.markDirty();
        });
    }

    private static void applyEffect(Village village, ServerWorld world, VillageState state, Decree decree) {
        VillageConfig config = VillageConfig.get();
        switch (decree) {
            case FEAST -> village.getResidentsUUIDs().toList().forEach(uuid -> Wellbeing.addHappiness(world, uuid, config.feastHappiness));
            case SETTLERS_CALL -> VillageImmigration.addFamily(state);
            case GRAND_FESTIVAL -> {
                village.getResidentsUUIDs().toList().forEach(uuid -> Wellbeing.addHappiness(world, uuid, config.festivalHappiness));
                VillageImmigration.addFamily(state);
            }
            default -> {
            }
        }
    }

    private static boolean recruitGuard(Village village, ServerWorld world, VillageState state) {
        Prestige.Result result = Prestige.evaluate(village, state.rank);
        long barracks = village.getBuildingsOfType("armory").filter(b -> {
            Prestige.Score score = result.score(b);
            return score != null && score.isActive();
        }).count();
        WorkRegistry work = WorkRegistry.get(world.getServer());
        long guards = village.getResidentsUUIDs().filter(uuid -> work.getJob(uuid) == Job.GUARD || work.getJob(uuid) == Job.ARCHER).count();
        if (guards >= barracks * VillageConfig.get().guardsPerBarracks) {
            return false;
        }
        for (UUID uuid : village.getResidentsUUIDs().toList()) {
            if (world.getEntity(uuid) instanceof VillagerEntityMCA villager && villager.isAlive()
                    && work.getJob(uuid) == Job.NONE
                    && AgeState.byCurrentAge(villager.getBreedingAge()) == AgeState.ADULT) {
                WorkManager.setJob(villager, Job.GUARD);
                return true;
            }
        }
        return false;
    }

    public static long weeklyCharges(Village village, ServerWorld world, VillageState state, long week) {
        long total = 0;
        for (Decree decree : Decree.values()) {
            if (!state.activeDecrees.contains(decree)) {
                continue;
            }
            Long paid = state.paidWeek.get(decree);
            if (paid != null && paid >= week) {
                if (paid == week) {
                    state.paidWeek.remove(decree);
                }
                continue;
            }
            state.paidWeek.remove(decree);
            long cost = weeklyCost(village, decree, world, 7 * week + 6);
            if (cost > state.treasury || !state.rank.isAtLeast(decree.rank)) {
                state.activeDecrees.remove(decree);
                VillageServer.notify(world, village, Text.translatable("msg.mca_vie.decree.stopped", village.getName(), decree.getName()).formatted(Formatting.RED));
                continue;
            }
            state.treasury -= cost;
            state.decreeSpending += cost;
            total += cost;
        }
        return total;
    }

    public static long weeklyCost(Village village, Decree decree, ServerWorld world, long day) {
        VillageConfig config = VillageConfig.get();
        return switch (decree) {
            case SCHOLARSHIP -> world == null ? 0 : (long) Math.ceil(countApprentices(village, world, day) * config.scholarshipCostPerApprentice);
            case FREE_CARE -> (long) Math.ceil(village.getPopulation() * config.freeCareCostPerResident);
            default -> 0;
        };
    }

    private static int countApprentices(Village village, ServerWorld world, long day) {
        VillageConfig config = VillageConfig.get();
        WorkRegistry work = WorkRegistry.get(world.getServer());
        LifeRegistry life = LifeRegistry.get(world.getServer());
        int count = 0;
        for (UUID uuid : village.getResidentsUUIDs().toList()) {
            Job job = work.getJob(uuid);
            Long birth = life.getBirthDay(uuid);
            if (job != Job.NONE && job != Job.RETIRED && birth != null) {
                int age = LifeCycle.ageInYears(birth, day);
                if (age >= config.apprenticeMinAge && age < config.taxAdultAge) {
                    count++;
                }
            }
        }
        return count;
    }

    public static void onBirth(VillagerEntityMCA baby) {
        if (!(baby.getWorld() instanceof ServerWorld world)) {
            return;
        }
        VillageManager.get(world).findNearestVillage(baby).ifPresent(village -> {
            VillageState state = VillageState.of(village);
            if (!state.activeDecrees.contains(Decree.BIRTH_BONUS)) {
                return;
            }
            long cost = VillageConfig.get().birthBonusCostPerBirth;
            if (cost > state.treasury || !state.rank.isAtLeast(Decree.BIRTH_BONUS.rank)) {
                state.activeDecrees.remove(Decree.BIRTH_BONUS);
                VillageServer.notify(world, village, Text.translatable("msg.mca_vie.decree.stopped", village.getName(), Decree.BIRTH_BONUS.getName()).formatted(Formatting.RED));
            } else {
                state.treasury -= cost;
                state.decreeSpending += cost;
            }
            village.markDirty();
        });
    }

    public static boolean birthBonus(Village village) {
        VillageState state = VillageState.of(village);
        return state.activeDecrees.contains(Decree.BIRTH_BONUS) && state.rank.isAtLeast(Decree.BIRTH_BONUS.rank);
    }

    public static void refreshCache(Village village, VillageState state, Prestige.Result result) {
        VillageConfig config = VillageConfig.get();
        double xp = 1;
        if (Prestige.hasActive(result, village, "school")) {
            xp *= config.schoolXpMultiplier;
        }
        if (state.activeDecrees.contains(Decree.SCHOLARSHIP) && state.rank.isAtLeast(Decree.SCHOLARSHIP.rank)) {
            xp *= config.scholarshipXpMultiplier;
        }
        state.cachedApprenticeXp = xp;
        double illness = 1;
        if (Prestige.hasActive(result, village, "infirmary")) {
            illness *= config.infirmaryIllnessMultiplier;
        }
        if (state.activeDecrees.contains(Decree.FREE_CARE) && state.rank.isAtLeast(Decree.FREE_CARE.rank)) {
            illness *= config.freeCareIllnessMultiplier;
        }
        state.cachedIllness = illness;
    }

    public static int apprenticeXp(VillagerEntityMCA villager, int amount) {
        if (!(villager.getWorld() instanceof ServerWorld world)) {
            return amount;
        }
        Long birth = LifeRegistry.get(world.getServer()).getBirthDay(villager.getUuid());
        if (birth == null) {
            return amount;
        }
        int age = LifeCycle.ageInYears(birth, WorldCalendar.now(world).dayIndex());
        if (age >= VillageConfig.get().taxAdultAge) {
            return amount;
        }
        double factor = residentVillage(villager).map(v -> VillageState.of(v).cachedApprenticeXp).orElse(1.0);
        double scaled = amount * factor;
        int whole = (int) Math.floor(scaled);
        return whole + (world.getRandom().nextDouble() < scaled - whole ? 1 : 0);
    }

    public static double illnessMultiplier(VillagerEntityMCA villager) {
        return residentVillage(villager).map(v -> VillageState.of(v).cachedIllness).orElse(1.0);
    }
}
