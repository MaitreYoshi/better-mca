package com.maitreyoshi.mcavie.village;

import com.maitreyoshi.mcavie.life.Wellbeing;
import net.mca.entity.VillagerEntityMCA;
import net.mca.server.world.data.Building;
import net.mca.server.world.data.Village;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;

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
    private static final Map<VillagerEntity, TradeOffer> RARE = new WeakHashMap<>();
    private static final Map<VillagerEntity, Map<TradeOffer, int[]>> DISCOUNTS = new WeakHashMap<>();

    private VillageMarketExtras() {
    }

    private static List<BlockPos> stalls(Village village) {
        return village.getBuildingsOfType("market").findFirst()
                .map(b -> b.getBlocks().values().stream().flatMap(List::stream).toList())
                .orElse(List.of());
    }

    // --- Marchands de passage ---

    public static boolean spawnMerchants(Village village, ServerWorld world) {
        List<BlockPos> stalls = stalls(village);
        VillageConfig config = VillageConfig.get();
        if (stalls.isEmpty() || config.marketMerchants <= 0) {
            return false;
        }
        int spawned = 0;
        for (int i = 0; i < config.marketMerchants; i++) {
            BlockPos pos = stalls.get(world.random.nextInt(stalls.size())).up();
            if (!world.isChunkLoaded(pos)) {
                continue;
            }
            WanderingTraderEntity trader = EntityType.WANDERING_TRADER.create(world);
            if (trader == null) {
                continue;
            }
            trader.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.random.nextFloat() * 360, 0);
            trader.setDespawnDelay(config.marketMerchantTicks);
            trader.setWanderTarget(pos);
            if (world.spawnEntity(trader)) {
                spawned++;
            }
        }
        if (spawned > 0) {
            VillageServer.notify(world, village, Text.translatable("msg.mca_vie.market.merchants", village.getName(), spawned).formatted(Formatting.GOLD));
        }
        return spawned > 0;
    }

    // --- Étals des joueurs ---

    public static void sellStalls(Village village, ServerWorld world, VillageState state) {
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
            Inventory inventory = VillageNature.openContainer(world, pos);
            if (inventory == null) {
                continue;
            }
            double earned = 0;
            for (int slot = 0; slot < inventory.size() && spent + 1e-9 < budget; slot++) {
                ItemStack stack = inventory.getStack(slot);
                int units = VillageNature.unitsPerEmerald(stack);
                if (units == 0) {
                    continue;
                }
                int sold = (int) Math.min(stack.getCount(), Math.ceil((budget - spent) * units / config.marketSalePrice - 1e-9));
                if (sold <= 0) {
                    break;
                }
                stack.decrement(sold);
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
            inventory.markDirty();
        }
        state.treasury += toTreasury;
        state.lastMarketSales = toSellers;
        state.lastMarketOrders = toTreasury;
        if (spent > 0) {
            village.getResidentsUUIDs().toList().forEach(uuid -> Wellbeing.addHappiness(world, uuid, config.marketHappiness));
        }
        VillageServer.notify(world, village, Text.translatable("msg.mca_vie.market.stalls", village.getName(), toSellers, toTreasury)
                .formatted(Formatting.GOLD));
    }

    private static void give(ServerWorld world, BlockPos pos, Inventory inventory, long amount) {
        long left = amount;
        for (int slot = 0; slot < inventory.size() && left > 0; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) {
                int n = (int) Math.min(64, left);
                inventory.setStack(slot, new ItemStack(Items.EMERALD, n));
                left -= n;
            } else if (stack.isOf(Items.EMERALD) && !stack.hasNbt() && stack.getCount() < stack.getMaxCount()) {
                int n = (int) Math.min(stack.getMaxCount() - stack.getCount(), left);
                stack.increment(n);
                left -= n;
            }
        }
        while (left > 0) {
            int n = (int) Math.min(64, left);
            ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, new ItemStack(Items.EMERALD, n));
            left -= n;
        }
    }

    // --- Rabais et offre rare du jour de marché ---

    private static final Object[][] RARE_GOODS = {
            {Items.DIAMOND, 1, 8}, {Items.NAME_TAG, 1, 6}, {Items.SADDLE, 1, 6}, {Items.GOLDEN_APPLE, 1, 5},
            {Items.EXPERIENCE_BOTTLE, 4, 4}, {Items.ENDER_PEARL, 2, 4}, {Items.NAUTILUS_SHELL, 1, 8}, {Items.AMETHYST_SHARD, 4, 3}};

    public static void prepareOffers(VillagerEntityMCA villager) {
        if (!(villager.getWorld() instanceof ServerWorld world)) {
            return;
        }
        TradeOfferList offers = villager.getOffers();
        clearRare(villager);
        clearDiscounts(villager);
        if (offers.isEmpty() || !VillageMarket.isMarketDayFor(villager, world)) {
            return;
        }
        int percent = VillageConfig.get().marketDiscountPercent;
        if (percent > 0) {
            Map<TradeOffer, int[]> applied = new IdentityHashMap<>();
            for (TradeOffer offer : offers) {
                int base = offer.getOriginalFirstBuyItem().getCount();
                int cut = Math.max(1, base * percent / 100);
                offer.increaseSpecialPrice(-cut);
                applied.put(offer, new int[]{cut, offer.getSpecialPrice()});
            }
            DISCOUNTS.put(villager, applied);
        }
        Object[] good = RARE_GOODS[Math.floorMod(villager.getUuid().hashCode() + (int) VillageMarket.marketWeek(world), RARE_GOODS.length)];
        TradeOffer rare = new TradeOffer(new ItemStack(Items.EMERALD, (Integer) good[2]),
                new ItemStack((net.minecraft.item.Item) good[0], (Integer) good[1]), 1, 5, 0.05F);
        offers.add(rare);
        RARE.put(villager, rare);
    }

    public static void clearRare(VillagerEntity villager) {
        TradeOffer old = RARE.remove(villager);
        if (old != null) {
            villager.getOffers().remove(old);
        }
        clearDiscounts(villager);
    }

    public static void clearDiscounts(VillagerEntity villager) {
        Map<TradeOffer, int[]> applied = DISCOUNTS.remove(villager);
        if (applied == null) {
            return;
        }
        applied.forEach((offer, d) -> {
            if (offer.getSpecialPrice() == d[1]) {
                offer.increaseSpecialPrice(d[0]);
            }
        });
    }
}
