package com.maitreyoshi.mcavie.village;

import net.mca.resources.Rank;
import net.mca.resources.Tasks;
import net.mca.server.world.data.Village;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class VillageRights {
    public static final Rank RIGHTS = Rank.MERCHANT;
    public static final Rank SET_TAXES = Rank.MERCHANT;
    public static final Rank RENAME = Rank.MERCHANT;
    public static final Rank SET_BIRTHS = Rank.NOBLE;
    public static final Rank SET_MARRIAGES = Rank.MAYOR;
    public static final Rank FORCE_TYPE = Rank.MAYOR;
    public static final Rank VIEW_TREASURY = Rank.MERCHANT;
    public static final Rank WITHDRAW = Rank.MAYOR;
    public static final Rank DEPOSIT = Rank.MERCHANT;
    public static final Rank JUSTICE = Rank.NOBLE;
    public static final Rank DECREES = Rank.NOBLE;
    public static final Rank MANAGE_WORK = Rank.MERCHANT;

    private VillageRights() {
    }

    private static final Map<UUID, Map<Integer, Rank>> RANKS = new HashMap<>();
    private static int ranksTick = -1;
    private static Object ranksServer;

    public static Rank personalRank(Village village, ServerPlayerEntity player) {
        int tick = player.getServer() == null ? -1 : player.getServer().getTicks();
        if (tick < 0) {
            return Tasks.getRank(village, player);
        }
        if (tick != ranksTick || player.getServer() != ranksServer) {
            RANKS.clear();
            ranksTick = tick;
            ranksServer = player.getServer();
        }
        return RANKS.computeIfAbsent(player.getUuid(), k -> new HashMap<>())
                .computeIfAbsent(village.getId(), k -> Tasks.getRank(village, player));
    }

    public static boolean hasRights(Village village, ServerPlayerEntity player) {
        return personalRank(village, player).isAtLeast(RIGHTS);
    }

    public static float maxTaxes(VillageRank rank) {
        return rank.rule().maxTaxRate / 100f;
    }

    public static float clampTaxes(Village village, float taxes) {
        float rounded = Math.round(taxes * 100) / 100f;
        return Math.max(0, Math.min(maxTaxes(VillageState.of(village).rank), rounded));
    }
}
