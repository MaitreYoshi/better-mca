package com.maitreyoshi.mcavie.village;

import net.minecraft.world.entity.EntityTypes;
import com.maitreyoshi.mcavie.compat.Nbt;
import com.maitreyoshi.mcavie.life.Wellbeing;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Le Marché « manuel » : étals des joueurs (tonneaux du Marché vendus en fin de marché),
 * rabais et offre rare des villageois le jour du marché, marchands de passage.
 */
public final class VillageMarketExtras {
    private static final Map<Villager, MerchantOffer> RARE = new WeakHashMap<>();
    private static final Map<Villager, Map<MerchantOffer, int[]>> DISCOUNTS = new WeakHashMap<>();

    private VillageMarketExtras() {
    }

    private static List<BlockPos> stalls(Village village) {
        return village.getBuildingsOfType("market").findFirst()
                .map(b -> b.getBlocks().values().stream().flatMap(List::stream).toList())
                .orElse(List.of());
    }

    // --- Marchands de passage ---

    public static boolean spawnMerchants(Village village, ServerLevel world) {
        List<BlockPos> stalls = stalls(village);
        VillageConfig config = VillageConfig.get();
        if (stalls.isEmpty() || config.marketMerchants <= 0) {
            return false;
        }
        int spawned = 0;
        for (int i = 0; i < config.marketMerchants; i++) {
            BlockPos pos = stalls.get(world.getRandom().nextInt(stalls.size())).above();
            if (!world.hasChunkAt(pos)) {
                continue;
            }
            WanderingTrader trader = EntityTypes.WANDERING_TRADER.create(world, net.minecraft.world.entity.EntitySpawnReason.EVENT);
            if (trader == null) {
                continue;
            }
            trader.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.getRandom().nextFloat() * 360, 0);
            trader.setDespawnDelay(config.marketMerchantTicks);
            trader.setWanderTarget(pos);
            if (world.addFreshEntity(trader)) {
                spawned++;
            }
        }
        if (spawned > 0) {
            VillageServer.notify(world, village, Component.translatable("msg.mca_vie.market.merchants", village.getName(), spawned).withStyle(ChatFormatting.GOLD));
        }
        return spawned > 0;
    }

    // --- Étals des joueurs ---

    public static void sellStalls(Village village, ServerLevel world, VillageState state) {
        VillageConfig config = VillageConfig.get();
        double budget = village.getPopulation() * config.marketSpendPerResident;
        double taxRate = Math.max(0, Math.min(100, VillageEconomy.appliedRate(village))) / 100.0;
        double spent = 0;
        long toSellers = 0;
        long toTreasury = 0;
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos pos : stalls(village)) {
            if (spent + 1e-9 >= budget) {
                break;
            }
            BlockPos key = VillageNature.canonical(world, pos);
            if (key == null || !seen.add(key)) {
                continue;
            }
            Container inventory = VillageNature.openContainer(world, pos);
            if (inventory == null) {
                continue;
            }
            double earned = 0;
            for (int slot = 0; slot < inventory.getContainerSize() && spent + 1e-9 < budget; slot++) {
                ItemStack stack = inventory.getItem(slot);
                int units = VillageNature.unitsPerEmerald(stack);
                if (units == 0) {
                    continue;
                }
                int sold = (int) Math.min(stack.getCount(), Math.ceil((budget - spent) * units / config.marketSalePrice - 1e-9));
                if (sold <= 0) {
                    break;
                }
                stack.shrink(sold);
                double value = sold * config.marketSalePrice / units;
                spent += value;
                earned += value;
            }
            long gross = (long) Math.floor(earned + 1e-9);
            if (gross > 0) {
                long tax = Math.round(gross * taxRate);
                toTreasury += tax;
                toSellers += gross - tax;
                give(world, pos, inventory, gross - tax);
            }
            inventory.setChanged();
        }
        state.treasury += toTreasury;
        state.lastMarketSales = toSellers;
        state.lastMarketOrders = toTreasury;
        if (spent > 0) {
            village.getResidentsUUIDs().toList().forEach(uuid -> Wellbeing.addHappiness(world, uuid, config.marketHappiness));
        }
        VillageServer.notify(world, village, Component.translatable("msg.mca_vie.market.stalls", village.getName(), toSellers, toTreasury)
                .withStyle(ChatFormatting.GOLD));
    }

    private static void give(ServerLevel world, BlockPos pos, Container inventory, long amount) {
        long left = amount;
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                int n = (int) Math.min(64, left);
                inventory.setItem(slot, new ItemStack(Items.EMERALD, n));
                left -= n;
            } else if (stack.is(Items.EMERALD) && !Nbt.hasTag(stack) && stack.getCount() < stack.getMaxStackSize()) {
                int n = (int) Math.min(stack.getMaxStackSize() - stack.getCount(), left);
                stack.grow(n);
                left -= n;
            }
        }
        while (left > 0) {
            int n = (int) Math.min(64, left);
            Containers.dropItemStack(world, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, new ItemStack(Items.EMERALD, n));
            left -= n;
        }
    }

    // --- Rabais et offre rare du jour de marché ---

    private static final Object[][] RARE_GOODS = {
            {Items.DIAMOND, 1, 8}, {Items.NAME_TAG, 1, 6}, {Items.SADDLE, 1, 6}, {Items.GOLDEN_APPLE, 1, 5},
            {Items.EXPERIENCE_BOTTLE, 4, 4}, {Items.ENDER_PEARL, 2, 4}, {Items.NAUTILUS_SHELL, 1, 8}, {Items.AMETHYST_SHARD, 4, 3}};

    public static void prepareOffers(VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel world)) {
            return;
        }
        MerchantOffers offers = villager.getOffers();
        clearRare(villager);
        clearDiscounts(villager);
        if (offers.isEmpty() || !VillageMarket.isMarketDayFor(villager, world)) {
            return;
        }
        int percent = VillageConfig.get().marketDiscountPercent;
        if (percent > 0) {
            Map<MerchantOffer, int[]> applied = new IdentityHashMap<>();
            for (MerchantOffer offer : offers) {
                int base = offer.getBaseCostA().getCount();
                int cut = Math.max(1, base * percent / 100);
                offer.addToSpecialPriceDiff(-cut);
                applied.put(offer, new int[]{cut, offer.getSpecialPriceDiff()});
            }
            DISCOUNTS.put(villager, applied);
        }
        Object[] good = RARE_GOODS[Math.floorMod(villager.getUUID().hashCode() + (int) VillageMarket.marketWeek(world), RARE_GOODS.length)];
        MerchantOffer rare = new MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, (Integer) good[2]),
                new ItemStack((net.minecraft.world.item.Item) good[0], (Integer) good[1]), 1, 5, 0.05F);
        offers.add(rare);
        RARE.put(villager, rare);
    }

    public static void clearRare(Villager villager) {
        MerchantOffer old = RARE.remove(villager);
        if (old != null) {
            villager.getOffers().remove(old);
        }
        clearDiscounts(villager);
    }

    public static void clearDiscounts(Villager villager) {
        Map<MerchantOffer, int[]> applied = DISCOUNTS.remove(villager);
        if (applied == null) {
            return;
        }
        applied.forEach((offer, d) -> {
            if (offer.getSpecialPriceDiff() == d[1]) {
                offer.addToSpecialPriceDiff(d[0]);
            }
        });
    }
}
