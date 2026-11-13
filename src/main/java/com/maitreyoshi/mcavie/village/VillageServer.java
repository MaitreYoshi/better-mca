package com.maitreyoshi.mcavie.village;

import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.life.History;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class VillageServer {
    private static final int UPDATE_INTERVAL = 100;

    private VillageServer() {
    }

    public static void tick(Village village, ServerLevel world, long time) {
        if ((time + village.getId()) % UPDATE_INTERVAL != 0) {
            return;
        }
        VillageState state = VillageState.of(village);
        boolean dirty = false;

        boolean rightsHolderHere = world.players().stream()
                .anyMatch(p -> village.isWithinBorder(p) && VillageRights.hasRights(village, p));
        if (rightsHolderHere && !state.rightsHolderSeenToday) {
            state.rightsHolderSeenToday = true;
            dirty = true;
        }

        long today = WorldCalendar.now(world).dayIndex();
        boolean newDay = state.hasLastDay() && today > state.lastDay;
        boolean countDay = newDay && state.rightsHolderSeenToday;
        if (!state.hasLastDay() || newDay || today < state.lastDay) {
            state.lastDay = today;
            state.rightsHolderSeenToday = rightsHolderHere;
            dirty = true;
        }

        if (rightsHolderHere && !state.economyActive) {
            state.economyActive = true;
            dirty = true;
        }
        if (VillageEconomy.tick(village, world, today)) {
            dirty = true;
        }

        VillageRank before = state.rank;
        Prestige.Result result = Prestige.climb(village, before);
        if (result.rank() != before) {
            state.rank = result.rank();
            state.descentDaysLeft = -1;
            notify(world, village, Component.translatable("msg.mca_vie.village.promoted", village.getName(), state.rank.getName()).withStyle(ChatFormatting.GOLD));
            dirty = true;
        } else if (before != VillageRank.HAMLET && !Prestige.meets(village, before, result)) {
            if (state.descentDaysLeft < 0) {
                state.descentDaysLeft = VillageConfig.get().descentDays;
                state.rightsHolderSeenToday = rightsHolderHere;
                notify(world, village, Component.translatable("msg.mca_vie.village.threat", village.getName(), before.getName(), state.descentDaysLeft).withStyle(ChatFormatting.RED));
                dirty = true;
            } else if (countDay) {
                state.descentDaysLeft--;
                if (state.descentDaysLeft <= 0) {
                    state.rank = Prestige.fallback(village, before, result);
                    state.descentDaysLeft = -1;
                    notify(world, village, Component.translatable("msg.mca_vie.village.demoted", village.getName(), state.rank.getName()).withStyle(ChatFormatting.RED));
                } else {
                    notify(world, village, Component.translatable("msg.mca_vie.village.days", village.getName(), state.descentDaysLeft).withStyle(ChatFormatting.RED));
                }
                dirty = true;
            }
        } else if (state.descentDaysLeft >= 0) {
            state.descentDaysLeft = -1;
            notify(world, village, Component.translatable("msg.mca_vie.village.saved", village.getName(), before.getName()).withStyle(ChatFormatting.GREEN));
            dirty = true;
        }

        Prestige.Result current = result.rank() == state.rank ? result : Prestige.evaluate(village, state.rank);
        boolean cleared = false;
        for (net.conczin.mca.server.world.data.Building b : village.getBuildings().values()) {
            Prestige.Score score = current.score(b);
            if (score != null && score.protectedKey() && state.clearDecay(village, b)) {
                cleared = true;
            }
        }
        if (cleared) {
            dirty = true;
            current = Prestige.evaluate(village, state.rank);
        }
        state.cachedEquilibrium = VillageEconomy.equilibrium(village, current, VillageEconomy.appliedRate(village));
        VillageDecrees.refreshCache(village, state, current);
        if (VillageMarket.tick(village, world, state, current)) {
            dirty = true;
        }
        if (newDay) {
            VillageJustice.daily(village, world, state, current, today);
            com.maitreyoshi.mcavie.social.Social.daily(village, world, today);
            dirty = true;
        }
        VillageJustice.tick(village, world, state, current);
        History.spawnPending(village, world);

        if (state.economyActive && VillageImmigration.trySpawn(village, world, state, current)) {
            dirty = true;
        }

        float taxes = VillageRights.clampTaxes(village, village.getTaxes());
        if (taxes != village.getTaxes()) {
            village.setTaxes(taxes);
            dirty = true;
        }

        if (dirty) {
            village.markDirty();
        }
    }

    public static void notify(ServerLevel world, Village village, Component message) {
        for (ServerPlayer player : world.players()) {
            if (VillageRights.hasRights(village, player)) {
                player.sendSystemMessage(message);
            }
        }
    }
}
