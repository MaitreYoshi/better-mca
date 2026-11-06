package com.maitreyoshi.mcavie.village;

import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.life.Wellbeing;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.MoveState;
import net.mca.server.world.data.Building;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageManager;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.time.DayOfWeek;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class VillageMarket {
    private VillageMarket() {
    }

    public static boolean isMarketHours(World world) {
        VillageConfig config = VillageConfig.get();
        WorldCalendar.CalendarDate date = WorldCalendar.now(world);
        return WorldCalendar.rhythmDay(date) == DayOfWeek.of(config.marketDayOfWeek) && date.hour() >= config.marketStartHour && date.hour() < config.marketEndHour;
    }

    public static long marketWeek(World world) {
        VillageConfig config = VillageConfig.get();
        WorldCalendar.CalendarDate date = WorldCalendar.now(world);
        long offset = config.marketDayOfWeek - 1;
        long week = Math.floorDiv(date.dayIndex() - offset, 7);
        boolean ended = Math.floorMod(date.dayIndex() - offset, 7) > 0 || date.hour() >= config.marketEndHour;
        return ended ? week : week - 1;
    }

    public static boolean isMarketDayFor(VillagerEntityMCA villager, ServerWorld world) {
        return isMarketHours(world) && VillageDecrees.residentVillage(villager).map(v -> VillageState.of(v).marketActive).orElse(false);
    }

    /** Commandes et ventes automatiques depuis l'Entrepôt, désactivées : le Marché est un lieu de rassemblement. */
    public static final boolean AUTOMATIC_TRADE = false;

    public static boolean tick(Village village, ServerWorld world, VillageState state, Prestige.Result result) {
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
        boolean refresh = (world.getTime() / 100) % 12 == village.getId() % 12;
        boolean newWeek = state.lastMarketWeek != VillageState.NONE && week > state.lastMarketWeek;
        List<Inventory> storage = refresh || newWeek ? VillageNature.storage(village, world, result).inventories() : List.of();
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

    public static void devHold(Village village, ServerWorld world) {
        if (!AUTOMATIC_TRADE) { VillageMarketExtras.sellStalls(village, world, VillageState.of(village)); village.markDirty(); return; }
        VillageState state = VillageState.of(village);
        Prestige.Result result = Prestige.evaluate(village, state.rank);
        List<Inventory> storage = VillageNature.hasStorage(village, result) ? VillageNature.storage(village, world, result).inventories() : List.of();
        hold(village, world, state, storage);
        refreshStock(state, storage);
        village.markDirty();
    }

    private static void gather(Village village, ServerWorld world) {
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
                    || villager.hasVehicle() || villager.getVillagerBrain().getMoveState() != MoveState.MOVE || villager.getVillagerBrain().isPanicking()
                    || villager.getVillagerBrain().getCurrentJob() != net.mca.entity.ai.Chore.NONE
                    || VillageJustice.isImprisoned(village, uuid)) {
                continue;
            }
            if (!market.containsPos(villager.getBlockPos())) {
                villager.moveTowards(stalls.get(world.random.nextInt(stalls.size())), 0.6f, 2);
            }
        }
    }

    private static boolean refreshStock(VillageState state, List<Inventory> storage) {
        Map<String, Integer> stock = new TreeMap<>();
        for (Inventory inventory : storage) {
            for (int slot = 0; slot < inventory.size(); slot++) {
                ItemStack stack = inventory.getStack(slot);
                if (VillageNature.unitsPerEmerald(stack) > 0) {
                    stock.merge(Registries.ITEM.getId(stack.getItem()).toString(), stack.getCount(), Integer::sum);
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

    private static void hold(Village village, ServerWorld world, VillageState state, List<Inventory> storage) {
        VillageConfig config = VillageConfig.get();
        long orderIncome = 0;
        for (VillageState.Order order : state.orders) {
            if (order.ready) {
                continue;
            }
            Item item = Registries.ITEM.get(Identifier.tryParse(order.item));
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
        for (Inventory inventory : storage) {
            for (int slot = 0; slot < inventory.size() && sales + 1e-9 < budget; slot++) {
                ItemStack stack = inventory.getStack(slot);
                int units = VillageNature.unitsPerEmerald(stack);
                if (units == 0) {
                    continue;
                }
                int sold = (int) Math.min(stack.getCount(), Math.ceil((budget - sales) * units / config.marketSalePrice - 1e-9));
                if (sold <= 0) {
                    break;
                }
                stack.decrement(sold);
                inventory.markDirty();
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
        VillageServer.notify(world, village, Text.translatable("msg.mca_vie.market.held", village.getName(), income, orderIncome, state.treasury)
                .formatted(Formatting.GOLD));
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (state.orders.stream().anyMatch(o -> o.ready && o.player.equals(player.getUuid()))) {
                player.sendMessage(Text.translatable("msg.mca_vie.market.ready", village.getName()).formatted(Formatting.GREEN), false);
            }
        }
    }

    private static int count(List<Inventory> storage, Item item) {
        int total = 0;
        for (Inventory inventory : storage) {
            for (int slot = 0; slot < inventory.size(); slot++) {
                ItemStack stack = inventory.getStack(slot);
                if (stack.isOf(item) && !stack.hasNbt()) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    private static void take(List<Inventory> storage, Item item, int amount) {
        for (Inventory inventory : storage) {
            for (int slot = 0; slot < inventory.size() && amount > 0; slot++) {
                ItemStack stack = inventory.getStack(slot);
                if (stack.isOf(item) && !stack.hasNbt()) {
                    int take = Math.min(amount, stack.getCount());
                    stack.decrement(take);
                    inventory.markDirty();
                    amount -= take;
                }
            }
        }
    }

    public static void order(ServerPlayerEntity player, int villageId, String itemId, int lots) {
        Optional<Village> found = VillageManager.get(player.getServerWorld()).getOrEmpty(villageId);
        if (found.isEmpty()) {
            return;
        }
        Village village = found.get();
        VillageState state = VillageState.of(village);
        VillageConfig config = VillageConfig.get();
        if (!state.marketActive) {
            player.sendMessage(Text.translatable("msg.mca_vie.market.inactive"), true);
            return;
        }
        if (config.unitsPerEmerald(itemId) == 0 || !village.isWithinBorder(player)) {
            player.sendMessage(Text.translatable("msg.mca_vie.market.unknown"), true);
            return;
        }
        long pending = state.orders.stream().filter(o -> o.player.equals(player.getUuid())).count();
        if (pending >= config.maxOrdersPerPlayer) {
            player.sendMessage(Text.translatable("msg.mca_vie.market.too_many", config.maxOrdersPerPlayer), true);
            return;
        }
        lots = Math.max(1, Math.min(config.maxLotsPerOrder, lots));
        int price = lots * config.orderLotPrice;
        if (!takeEmeralds(player, price)) {
            player.sendMessage(Text.translatable("msg.mca_vie.market.no_money", price), true);
            return;
        }
        VillageState.Order order = new VillageState.Order();
        order.player = player.getUuid();
        order.playerName = player.getName().getString();
        order.item = itemId;
        order.lots = lots;
        order.units = config.unitsPerEmerald(itemId);
        order.paid = price;
        state.orders.add(order);
        village.markDirty();
        player.sendMessage(Text.translatable("msg.mca_vie.market.ordered", lots * config.unitsPerEmerald(itemId),
                Registries.ITEM.get(Identifier.tryParse(itemId)).getName(), price), true);
    }

    public static void collect(ServerPlayerEntity player, int villageId) {
        VillageManager.get(player.getServerWorld()).getOrEmpty(villageId).ifPresent(village -> {
            if (!village.isWithinBorder(player)) {
                return;
            }
            VillageState state = VillageState.of(village);
            int given = 0;
            for (Iterator<VillageState.Order> it = state.orders.iterator(); it.hasNext(); ) {
                VillageState.Order order = it.next();
                if (!order.ready || !order.player.equals(player.getUuid())) {
                    continue;
                }
                give(player, Registries.ITEM.get(Identifier.tryParse(order.item)), order.delivered);
                give(player, Items.EMERALD, order.refund);
                it.remove();
                given++;
            }
            if (given > 0) {
                village.markDirty();
                player.sendMessage(Text.translatable("msg.mca_vie.market.collected", given), true);
            }
        });
    }

    private static void give(ServerPlayerEntity player, Item item, int count) {
        while (count > 0) {
            int stack = Math.min(count, item.getMaxCount());
            player.getInventory().offerOrDrop(new ItemStack(item, stack));
            count -= stack;
        }
    }

    private static boolean takeEmeralds(ServerPlayerEntity player, int amount) {
        net.minecraft.entity.player.PlayerInventory inventory = player.getInventory();
        int available = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isOf(Items.EMERALD) && !stack.hasNbt()) {
                available += stack.getCount();
            }
        }
        if (available < amount) {
            return false;
        }
        for (int slot = 0; slot < inventory.size() && amount > 0; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isOf(Items.EMERALD) && !stack.hasNbt()) {
                int take = Math.min(amount, stack.getCount());
                stack.decrement(take);
                amount -= take;
            }
        }
        inventory.markDirty();
        return true;
    }
}
