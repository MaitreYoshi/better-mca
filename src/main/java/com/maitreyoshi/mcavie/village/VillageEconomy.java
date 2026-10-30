package com.maitreyoshi.mcavie.village;

import com.maitreyoshi.mcavie.life.LifeCycle;
import com.maitreyoshi.mcavie.life.LifeRegistry;
import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkRegistry;
import net.mca.entity.VillagerEntityMCA;
import net.mca.server.world.data.Building;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class VillageEconomy {
    private VillageEconomy() {
    }

    public static void applyMarketDiscount(VillagerEntityMCA villager, net.minecraft.entity.player.PlayerEntity player) {
        if (villager.getWorld().isClient || (villager.getCustomer() != null && villager.getCustomer() != player)) {
            return;
        }
        villager.getResidency().getHomeVillage().ifPresent(village -> {
            Prestige.Result result = Prestige.evaluate(village, VillageState.of(village).rank);
            if (!Prestige.hasActive(result, village, "market")) {
                return;
            }
            double discount = VillageConfig.get().marketDiscount;
            for (net.minecraft.village.TradeOffer offer : villager.getOffers()) {
                int count = offer.getOriginalFirstBuyItem().getCount();
                int off = count >= 2 ? Math.max(1, (int) Math.round(count * discount)) : 0;
                if (off > 0) {
                    offer.increaseSpecialPrice(-off);
                }
            }
        });
    }

    public static long weeklyUpkeep(Village village, Prestige.Result result) {
        int base = 0;
        for (Building b : village.getBuildings().values()) {
            Prestige.Score score = result.score(b);
            if (score != null && score.isActive() && score.rule() != null) {
                base += score.rule().upkeep;
            }
        }
        return (long) Math.floor(base * result.rank().rule().taxMultiplier + 1e-9);
    }

    public static int appliedRate(Village village) {
        VillageState state = VillageState.of(village);
        int rate = state.appliedRate >= 0 ? state.appliedRate : Math.round(village.getTaxes() * 100);
        return Math.min(rate, Math.round(VillageRights.maxTaxes(state.rank) * 100));
    }

    public static int equilibrium(Village village, Prestige.Result result, int ratePercent) {
        VillageConfig config = VillageConfig.get();
        int value = config.equilibriumFor(ratePercent);
        if (Prestige.hasActive(result, village, "chapel")) {
            value += config.chapelHappinessBonus;
        }
        if (Prestige.hasActive(result, village, "theater")) {
            value += config.theaterHappinessBonus;
        }
        if (result.rank() == VillageRank.METROPOLIS) {
            value += config.capitalHappinessBonus;
        }
        return value;
    }

    public static long availableShare(VillageState state) {
        long cap = state.shareBase * VillageConfig.get().playerSharePercent / 100;
        return Math.max(0, Math.min(state.treasury - state.depositPool, cap - state.withdrawnThisWeek));
    }

    public static int equilibriumFor(VillagerEntityMCA villager) {
        if (!(villager.getWorld() instanceof ServerWorld world)) {
            return 50;
        }
        return VillageManager.get(world).findNearestVillage(villager)
                .filter(v -> v.hasResident(villager.getUuid()))
                .filter(v -> VillageState.of(v).economyActive)
                .map(v -> {
                    VillageState state = VillageState.of(v);
                    if (state.cachedEquilibrium < 0) {
                        state.cachedEquilibrium = equilibrium(v, Prestige.evaluate(v, state.rank), appliedRate(v));
                    }
                    return state.cachedEquilibrium;
                })
                .orElse(50);
    }

    public static boolean tick(Village village, ServerWorld world, long today) {
        VillageState state = VillageState.of(village);
        if (!state.economyActive) {
            return false;
        }
        long latest = Math.floorDiv(today - 6, 7);
        if (state.lastLevyWeek == VillageState.NONE) {
            state.lastLevyWeek = Math.floorDiv(today, 7) - 1;
            state.appliedRate = Math.round(village.getTaxes() * 100);
            return true;
        }
        if (latest < state.lastLevyWeek - 1) {
            state.lastLevyWeek = latest;
            return true;
        }
        if (latest <= state.lastLevyWeek) {
            return false;
        }
        state.lastLevyWeek = Math.max(state.lastLevyWeek, latest - VillageConfig.get().taxCatchUpWeeks);
        while (state.lastLevyWeek < latest) {
            state.lastLevyWeek++;
            levy(village, world, state, state.lastLevyWeek);
        }
        return true;
    }

    public static void devLevy(Village village, ServerWorld world, long today) {
        VillageState state = VillageState.of(village);
        state.economyActive = true;
        long week = Math.floorDiv(today, 7);
        levy(village, world, state, week);
        state.lastLevyWeek = state.lastLevyWeek == VillageState.NONE ? week : Math.max(state.lastLevyWeek, week);
        village.markDirty();
    }

    private static double grossTaxes(Village village, ServerWorld world, VillageState state, Prestige.Result result, int rate, long week,
                                     Map<UUID, VillageState.TaxEntry> roll) {
        VillageConfig config = VillageConfig.get();
        double multiplier = state.rank.rule().taxMultiplier * rate / 100.0;
        if (Prestige.hasActive(result, village, "library")) {
            multiplier *= 1 + config.libraryTaxBonus;
        }
        double total = 0;
        WorkRegistry work = WorkRegistry.get(world.getServer());
        LifeRegistry life = LifeRegistry.get(world.getServer());
        long sunday = 7 * week + 6;
        for (UUID uuid : village.getResidentsUUIDs().toList()) {
            WorkRegistry.Record record = work.get(uuid);
            if (record == null || record.job == Job.NONE || record.job == Job.RETIRED || state.prisoners.containsKey(uuid)) {
                continue;
            }
            Long birth = life.getBirthDay(uuid);
            if (birth != null && LifeCycle.ageInYears(birth, sunday) < config.taxAdultAge) {
                continue;
            }
            double tax = config.taxBase * multiplier * (1 + (Math.max(1, record.level) - 1) * config.taxLevelBonus);
            total += tax;
            if (roll != null) {
                roll.put(uuid, new VillageState.TaxEntry(record.job.ordinal(), record.level, tax));
            }
        }
        return total;
    }

    private static void levy(Village village, ServerWorld world, VillageState state, long week) {
        VillageConfig config = VillageConfig.get();
        Prestige.Result result = Prestige.evaluate(village, state.rank);
        int rate = appliedRate(village);

        state.taxRoll.clear();
        double gross = grossTaxes(village, world, state, result, rate, week, state.taxRoll);
        double nature = Math.min(gross, collectNature(village, world, state, result));
        double raw = state.taxRemainder + gross - nature;
        long income = (long) Math.floor(raw + 1e-9);
        state.taxRemainder = Math.max(0, raw - income);
        state.treasury += income;

        long upkeep = weeklyUpkeep(village, result);
        state.treasury -= upkeep;

        VillageDecrees.weeklyCharges(village, world, state, week);
        VillageImmigration.weekly(village, state, result);

        if (state.treasury < 0) {
            state.overdraftWeeks++;
            if (state.overdraftWeeks > config.overdraftGraceWeeks) {
                decay(village, world, state, rate, week);
                state.overdraftWeeks = 0;
            }
        } else {
            state.overdraftWeeks = 0;
        }

        state.addHistory(new VillageState.WeekRecord(week, income, upkeep, state.decreeSpending, state.withdrawnThisWeek, state.treasury,
                nature, state.depositedThisWeek));
        state.decreeSpending = 0;
        state.depositPool = Math.max(0, Math.min(state.depositPool, state.treasury));
        state.shareBase = Math.max(0, state.treasury - state.depositPool);
        state.withdrawnThisWeek = 0;
        state.depositedThisWeek = 0;
        state.appliedRate = Math.round(village.getTaxes() * 100);

        VillageServer.notify(world, village, Text.translatable("msg.mca_vie.taxes.levied", village.getName(), income, upkeep, state.treasury)
                .formatted(state.treasury < 0 ? Formatting.RED : Formatting.GOLD));
    }

    private static double collectNature(Village village, ServerWorld world, VillageState state, Prestige.Result result) {
        if (state.natureShare <= 0 || !VillageNature.hasStorage(village, result)) {
            return 0;
        }
        VillageNature.Storage storage = VillageNature.storage(village, world, result);
        if (storage.isEmpty()) {
            return 0;
        }
        WorkRegistry work = WorkRegistry.get(world.getServer());
        double total = 0;
        for (Map.Entry<UUID, VillageState.TaxEntry> entry : state.taxRoll.entrySet()) {
            WorkRegistry.Record record = work.get(entry.getKey());
            if (record == null || record.depot == null) {
                continue;
            }
            net.minecraft.util.math.BlockPos key = VillageNature.canonical(world, record.depot);
            if (key == null || storage.positions().contains(key)) {
                continue;
            }
            net.minecraft.inventory.Inventory depot = VillageNature.openContainer(world, record.depot);
            if (depot != null) {
                total += VillageNature.collect(depot, storage.inventories(), entry.getValue().amount() * state.natureShare / 100.0);
            }
        }
        return total;
    }

    private static void decay(Village village, ServerWorld world, VillageState state, int rate, long week) {
        Prestige.Result result = Prestige.evaluate(village, state.rank);
        List<Building> candidates = village.getBuildings().values().stream()
                .filter(b -> {
                    Prestige.Score score = result.score(b);
                    return score != null && score.isActive() && score.rule().upkeep > 0 && !score.protectedKey();
                })
                .sorted(Comparator.comparingInt((Building b) -> result.score(b).rule().upkeep).reversed())
                .toList();
        Prestige.Result current = result;
        for (Building b : candidates) {
            double cash = grossTaxes(village, world, state, current, rate, week, null) * (100 - state.natureShare) / 100.0;
            if (weeklyUpkeep(village, current) <= Math.floor(cash + 1e-9)) {
                break;
            }
            state.decayed.add(b.getCenter().asLong());
            current = Prestige.evaluate(village, state.rank);
            VillageServer.notify(world, village, Text.translatable("msg.mca_vie.taxes.decayed", village.getName(),
                    Text.translatable("buildingType." + b.getType())).formatted(Formatting.RED));
        }
    }

    public static void withdraw(ServerPlayerEntity player, int villageId, int amount) {
        VillageManager.get(player.getServerWorld()).getOrEmpty(villageId).ifPresent(village -> {
            if (!VillageRights.personalRank(village, player).isAtLeast(VillageRights.WITHDRAW)) {
                player.sendMessage(Text.translatable("msg.mca_vie.rights.denied", Text.translatable(VillageRights.WITHDRAW.getTranslationKey())), true);
                return;
            }
            VillageState state = VillageState.of(village);
            long taken = Math.min(Math.max(0, amount), availableShare(state));
            if (taken <= 0) {
                player.sendMessage(Text.translatable("msg.mca_vie.treasury.nothing"), true);
                return;
            }
            state.treasury -= taken;
            state.withdrawnThisWeek += taken;
            state.addWithdrawal(new VillageState.Withdrawal(state.lastDay, player.getName().getString(), taken, false));
            long left = taken;
            while (left > 0) {
                int stack = (int) Math.min(64, left);
                player.getInventory().offerOrDrop(new ItemStack(Items.EMERALD, stack));
                left -= stack;
            }
            village.markDirty();
            player.sendMessage(Text.translatable("msg.mca_vie.treasury.withdrawn", taken, village.getName()), true);
        });
    }

    public static void deposit(ServerPlayerEntity player, int villageId, int amount) {
        VillageManager.get(player.getServerWorld()).getOrEmpty(villageId).ifPresent(village -> {
            if (!VillageRights.personalRank(village, player).isAtLeast(VillageRights.DEPOSIT)) {
                player.sendMessage(Text.translatable("msg.mca_vie.rights.denied", Text.translatable(VillageRights.DEPOSIT.getTranslationKey())), true);
                return;
            }
            net.minecraft.entity.player.PlayerInventory inventory = player.getInventory();
            int wanted = Math.min(Math.max(0, amount), inventory.count(Items.EMERALD));
            int left = wanted;
            for (int slot = 0; slot < inventory.size() && left > 0; slot++) {
                ItemStack stack = inventory.getStack(slot);
                if (stack.isOf(Items.EMERALD) && !stack.hasNbt()) {
                    int take = Math.min(left, stack.getCount());
                    stack.decrement(take);
                    left -= take;
                }
            }
            long given = wanted - left;
            if (given <= 0) {
                player.sendMessage(Text.translatable("msg.mca_vie.treasury.no_emerald"), true);
                return;
            }
            inventory.markDirty();
            VillageState state = VillageState.of(village);
            state.treasury += given;
            state.depositPool += given;
            state.depositedThisWeek += given;
            state.addWithdrawal(new VillageState.Withdrawal(state.lastDay, player.getName().getString(), given, true));
            village.markDirty();
            player.sendMessage(Text.translatable("msg.mca_vie.treasury.deposited", given, village.getName()), true);
        });
    }

    public static void changeNature(ServerPlayerEntity player, int villageId, int direction) {
        VillageManager.get(player.getServerWorld()).getOrEmpty(villageId).ifPresent(village -> {
            if (!VillageRights.personalRank(village, player).isAtLeast(VillageRights.SET_TAXES)) {
                player.sendMessage(Text.translatable("msg.mca_vie.rights.denied", Text.translatable(VillageRights.SET_TAXES.getTranslationKey())), true);
                return;
            }
            VillageState state = VillageState.of(village);
            int step = VillageConfig.get().natureStep;
            state.natureShare = Math.max(0, Math.min(100, state.natureShare + Integer.signum(direction) * step));
            village.markDirty();
        });
    }
}
