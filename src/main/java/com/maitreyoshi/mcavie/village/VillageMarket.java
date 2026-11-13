package com.maitreyoshi.mcavie.village;

import com.maitreyoshi.mcavie.compat.Nbt;
import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.life.Wellbeing;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.MoveState;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import java.time.DayOfWeek;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class VillageMarket {
    private VillageMarket() {
    }

    public static boolean isMarketHours(Level world) {
        VillageConfig config = VillageConfig.get();
        WorldCalendar.CalendarDate date = WorldCalendar.now(world);
        return WorldCalendar.rhythmDay(date) == DayOfWeek.of(config.marketDayOfWeek) && date.hour() >= config.marketStartHour && date.hour() < config.marketEndHour;
    }

    public static long marketWeek(Level world) {
        VillageConfig config = VillageConfig.get();
        WorldCalendar.CalendarDate date = WorldCalendar.now(world);
        long offset = config.marketDayOfWeek - 1;
        long week = Math.floorDiv(date.dayIndex() - offset, 7);
        boolean ended = Math.floorMod(date.dayIndex() - offset, 7) > 0 || date.hour() >= config.marketEndHour;
        return ended ? week : week - 1;
    }

    public static boolean isMarketDayFor(VillagerEntityMCA villager, ServerLevel world) {
        return isMarketHours(world) && VillageDecrees.residentVillage(villager).map(v -> VillageState.of(v).marketActive).orElse(false);
    }

    /** Commandes et ventes automatiques depuis l'Entrepôt, désactivées : le Marché est un lieu de rassemblement. */
    public static final boolean AUTOMATIC_TRADE = false;

    public static boolean tick(Village village, ServerLevel world, VillageState state, Prestige.Result result) {
        boolean dirty = false;
        boolean active = Prestige.hasActive(result, village, "market") && state.economyActive;
        if (active != state.marketActive) {
            state.marketActive = active;
            dirty = true;
        }
        long week = marketWeek(world);
        if (!active) {
            if (refundPending(state)) {
                dirty = true;
            }
            if (state.lastMarketWeek != week) {
                state.lastMarketWeek = week;
                dirty = true;
            }
            return dirty;
        }
        if (!AUTOMATIC_TRADE) {
            // Marché manuel : les villageois se rassemblent le jour du marché, les joueurs commercent eux-mêmes.
            if (refundPending(state)) {
                dirty = true;
            }
            if (isMarketHours(world)) {
                gather(village, world);
                if (state.lastMerchantWeek != week && VillageMarketExtras.spawnMerchants(village, world)) {
                    state.lastMerchantWeek = week;
                    dirty = true;
                }
            }
            if (state.lastMarketWeek == VillageState.NONE || week < state.lastMarketWeek) {
                state.lastMarketWeek = week;
                return true;
            }
            if (week > state.lastMarketWeek) {
                state.lastMarketWeek = week;
                VillageMarketExtras.sellStalls(village, world, state);
                dirty = true;
            }
            return dirty;
        }
        boolean refresh = (world.getGameTime() / 100) % 12 == village.getId() % 12;
        boolean newWeek = state.lastMarketWeek != VillageState.NONE && week > state.lastMarketWeek;
        List<Container> storage = refresh || newWeek ? VillageNature.storage(village, world, result).inventories() : List.of();
        if (refresh && !storage.isEmpty() && refreshStock(state, storage)) {
            dirty = true;
        }
        if (isMarketHours(world)) {
            gather(village, world);
        }
        if (state.lastMarketWeek == VillageState.NONE || week < state.lastMarketWeek) {
            state.lastMarketWeek = week;
            return true;
        }
        if (week > state.lastMarketWeek && !storage.isEmpty()) {
            state.lastMarketWeek = week;
            hold(village, world, state, storage);
            refreshStock(state, storage);
            dirty = true;
        }
        return dirty;
    }

    private static boolean refundPending(VillageState state) {
        boolean changed = false;
        for (VillageState.Order order : state.orders) {
            if (!order.ready) {
                order.delivered = 0;
                order.refund = order.paid;
                order.ready = true;
                changed = true;
            }
        }
        return changed;
    }

    public static void devHold(Village village, ServerLevel world) {
        if (!AUTOMATIC_TRADE) { VillageMarketExtras.sellStalls(village, world, VillageState.of(village)); village.markDirty(); return; }
        VillageState state = VillageState.of(village);
        Prestige.Result result = Prestige.evaluate(village, state.rank);
        List<Container> storage = VillageNature.hasStorage(village, result) ? VillageNature.storage(village, world, result).inventories() : List.of();
        hold(village, world, state, storage);
        refreshStock(state, storage);
        village.markDirty();
    }

    private static void gather(Village village, ServerLevel world) {
        List<Building> markets = village.getBuildingsOfType("market").toList();
        if (markets.isEmpty()) {
            return;
        }
        Building market = markets.get(0);
        List<BlockPos> stalls = market.getBlocks().values().stream().flatMap(List::stream).toList();
        if (stalls.isEmpty()) {
            return;
        }
        for (java.util.UUID uuid : village.getResidentsUUIDs().toList()) {
            if (!(world.getEntity(uuid) instanceof VillagerEntityMCA villager) || villager.isGuard() || villager.isSleeping() || villager.isBaby()
                    || villager.isPassenger() || villager.getVillagerBrain().getMoveState() != MoveState.MOVE || villager.getVillagerBrain().isPanicking()
                    || villager.getVillagerBrain().getCurrentJob() != net.conczin.mca.entity.ai.Chore.NONE
                    || VillageJustice.isImprisoned(village, uuid)) {
                continue;
            }
            if (!market.containsPos(villager.blockPosition())) {
                villager.moveTowards(stalls.get(world.getRandom().nextInt(stalls.size())), 0.6f, 2);
            }
        }
    }

    private static boolean refreshStock(VillageState state, List<Container> storage) {
        Map<String, Integer> stock = new TreeMap<>();
        for (Container inventory : storage) {
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (VillageNature.unitsPerEmerald(stack) > 0) {
                    stock.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
                }
            }
        }
        if (stock.equals(state.stock)) {
            return false;
        }
        state.stock.clear();
        state.stock.putAll(stock);
        return true;
    }

    private static void hold(Village village, ServerLevel world, VillageState state, List<Container> storage) {
        VillageConfig config = VillageConfig.get();
        long orderIncome = 0;
        for (VillageState.Order order : state.orders) {
            if (order.ready) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(order.item));
            int units = order.units;
            int lots = 0;
            while (units > 0 && lots < order.lots && count(storage, item) >= units) {
                take(storage, item, units);
                lots++;
            }
            order.delivered = lots * units;
            int perLot = order.lots == 0 ? 0 : order.paid / order.lots;
            order.refund = order.paid - lots * perLot;
            order.ready = true;
            orderIncome += (long) lots * perLot;
        }
        state.treasury += orderIncome;

        double budget = village.getPopulation() * config.marketSpendPerResident;
        double sales = 0;
        for (Container inventory : storage) {
            for (int slot = 0; slot < inventory.getContainerSize() && sales + 1e-9 < budget; slot++) {
                ItemStack stack = inventory.getItem(slot);
                int units = VillageNature.unitsPerEmerald(stack);
                if (units == 0) {
                    continue;
                }
                int sold = (int) Math.min(stack.getCount(), Math.ceil((budget - sales) * units / config.marketSalePrice - 1e-9));
                if (sold <= 0) {
                    break;
                }
                stack.shrink(sold);
                inventory.setChanged();
                sales += sold * config.marketSalePrice / units;
            }
        }
        double raw = state.marketRemainder + sales;
        long income = (long) Math.floor(raw + 1e-9);
        state.marketRemainder = Math.max(0, raw - income);
        state.treasury += income;
        state.lastMarketSales = income;
        state.lastMarketOrders = orderIncome;

        if (income > 0 || orderIncome > 0) {
            village.getResidentsUUIDs().toList().forEach(uuid -> Wellbeing.addHappiness(world, uuid, config.marketHappiness));
        }
        VillageServer.notify(world, village, Component.translatable("msg.mca_vie.market.held", village.getName(), income, orderIncome, state.treasury)
                .withStyle(ChatFormatting.GOLD));
        for (ServerPlayer player : world.players()) {
            if (state.orders.stream().anyMatch(o -> o.ready && o.player.equals(player.getUUID()))) {
                player.sendSystemMessage(Component.translatable("msg.mca_vie.market.ready", village.getName()).withStyle(ChatFormatting.GREEN));
            }
        }
    }

    private static int count(List<Container> storage, Item item) {
        int total = 0;
        for (Container inventory : storage) {
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.is(item) && !Nbt.hasTag(stack)) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    private static void take(List<Container> storage, Item item, int amount) {
        for (Container inventory : storage) {
            for (int slot = 0; slot < inventory.getContainerSize() && amount > 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.is(item) && !Nbt.hasTag(stack)) {
                    int take = Math.min(amount, stack.getCount());
                    stack.shrink(take);
                    inventory.setChanged();
                    amount -= take;
                }
            }
        }
    }

    public static void order(ServerPlayer player, int villageId, String itemId, int lots) {
        Optional<Village> found = VillageManager.get(player.level()).getOrEmpty(villageId);
        if (found.isEmpty()) {
            return;
        }
        Village village = found.get();
        VillageState state = VillageState.of(village);
        VillageConfig config = VillageConfig.get();
        if (!state.marketActive) {
            player.sendOverlayMessage(Component.translatable("msg.mca_vie.market.inactive"));
            return;
        }
        if (config.unitsPerEmerald(itemId) == 0 || !village.isWithinBorder(player)) {
            player.sendOverlayMessage(Component.translatable("msg.mca_vie.market.unknown"));
            return;
        }
        long pending = state.orders.stream().filter(o -> o.player.equals(player.getUUID())).count();
        if (pending >= config.maxOrdersPerPlayer) {
            player.sendOverlayMessage(Component.translatable("msg.mca_vie.market.too_many", config.maxOrdersPerPlayer));
            return;
        }
        lots = Math.max(1, Math.min(config.maxLotsPerOrder, lots));
        int price = lots * config.orderLotPrice;
        if (!takeEmeralds(player, price)) {
            player.sendOverlayMessage(Component.translatable("msg.mca_vie.market.no_money", price));
            return;
        }
        VillageState.Order order = new VillageState.Order();
        order.player = player.getUUID();
        order.playerName = player.getName().getString();
        order.item = itemId;
        order.lots = lots;
        order.units = config.unitsPerEmerald(itemId);
        order.paid = price;
        state.orders.add(order);
        village.markDirty();
        player.sendOverlayMessage(Component.translatable("msg.mca_vie.market.ordered", lots * config.unitsPerEmerald(itemId),
                BuiltInRegistries.ITEM.getValue(Identifier.tryParse(itemId)).getName(new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.tryParse(itemId)))), price));
    }

    public static void collect(ServerPlayer player, int villageId) {
        VillageManager.get(player.level()).getOrEmpty(villageId).ifPresent(village -> {
            if (!village.isWithinBorder(player)) {
                return;
            }
            VillageState state = VillageState.of(village);
            int given = 0;
            for (Iterator<VillageState.Order> it = state.orders.iterator(); it.hasNext(); ) {
                VillageState.Order order = it.next();
                if (!order.ready || !order.player.equals(player.getUUID())) {
                    continue;
                }
                give(player, BuiltInRegistries.ITEM.getValue(Identifier.tryParse(order.item)), order.delivered);
                give(player, Items.EMERALD, order.refund);
                it.remove();
                given++;
            }
            if (given > 0) {
                village.markDirty();
                player.sendOverlayMessage(Component.translatable("msg.mca_vie.market.collected", given));
            }
        });
    }

    private static void give(ServerPlayer player, Item item, int count) {
        while (count > 0) {
            int stack = Math.min(count, item.getDefaultMaxStackSize());
            player.getInventory().placeItemBackInInventory(new ItemStack(item, stack));
            count -= stack;
        }
    }

    private static boolean takeEmeralds(ServerPlayer player, int amount) {
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        int available = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(Items.EMERALD) && !Nbt.hasTag(stack)) {
                available += stack.getCount();
            }
        }
        if (available < amount) {
            return false;
        }
        for (int slot = 0; slot < inventory.getContainerSize() && amount > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(Items.EMERALD) && !Nbt.hasTag(stack)) {
                int take = Math.min(amount, stack.getCount());
                stack.shrink(take);
                amount -= take;
            }
        }
        inventory.setChanged();
        return true;
    }
}
