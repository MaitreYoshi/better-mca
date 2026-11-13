package com.maitreyoshi.mcavie.village;

import net.mca.server.world.data.Building;
import net.mca.server.world.data.Village;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class VillageState {
    public static final String NBT_KEY = "mca_vie";
    public static final long NONE = Long.MIN_VALUE;
    private static final int HISTORY_SIZE = 8;
    private static final int JOURNAL_SIZE = 40;

    public VillageRank rank = VillageRank.HAMLET;
    public int descentDaysLeft = -1;
    public long lastDay = NONE;
    public boolean rightsHolderSeenToday;

    public boolean economyActive;
    public long treasury;
    public double taxRemainder;
    public int appliedRate = -1;
    public long lastLevyWeek = NONE;
    public int overdraftWeeks;
    public final Set<Long> decayed = new HashSet<>();
    public int cachedEquilibrium = -1;
    public double cachedApprenticeXp = 1;
    public double cachedIllness = 1;
    public long shareBase;
    public long withdrawnThisWeek;
    public long withdrawnToday;
    public long shareDay = NONE;
    public int natureShare;
    public long depositPool;
    public long depositedThisWeek;
    public final LinkedList<WeekRecord> history = new LinkedList<>();
    public final LinkedList<Withdrawal> journal = new LinkedList<>();

    public final Set<Decree> activeDecrees = EnumSet.noneOf(Decree.class);
    public final Map<Decree, Long> lastLaunch = new EnumMap<>(Decree.class);
    public final Map<Decree, Long> paidWeek = new EnumMap<>(Decree.class);
    public long decreeSpending;

    public double immigrationProgress;
    public int pendingFamilies;
    public long lastArrivalDay = NONE;

    public final Map<UUID, TaxEntry> taxRoll = new HashMap<>();
    /** Habitants morts, rafraîchi à chaque envoi du plan (affichage seul). */
    public final Set<UUID> deadResidents = new HashSet<>();

    public boolean marketActive;
    public long lastMarketWeek = NONE;
    public long lastMerchantWeek = NONE;
    public double marketRemainder;
    public long lastMarketSales;
    public long lastMarketOrders;
    public final Map<String, Integer> stock = new java.util.TreeMap<>();
    public final List<Order> orders = new java.util.ArrayList<>();
    public final LinkedList<CivilRegistry.Entry> registry = new LinkedList<>();
    public final List<Case> cases = new java.util.ArrayList<>();
    public boolean prisonActive;
    public final Set<UUID> pendingRelease = new HashSet<>();
    public final Map<UUID, Prisoner> prisoners = new java.util.LinkedHashMap<>();
    public final Map<UUID, net.mca.resources.Rank> forcedTitles = new HashMap<>();

    public static final class Case {
        public UUID suspect;
        public String name = "";
        public long day;
        public long amount;

        NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putUuid("suspect", suspect);
            nbt.putString("name", name);
            nbt.putLong("day", day);
            nbt.putLong("amount", amount);
            return nbt;
        }

        static Case fromNbt(NbtCompound nbt) {
            Case c = new Case();
            c.suspect = nbt.getUuid("suspect");
            c.name = nbt.getString("name");
            c.day = nbt.getLong("day");
            c.amount = nbt.getLong("amount");
            return c;
        }
    }

    public static final class Prisoner {
        public String name = "";
        public long releaseDay;
    }

    public static final class Order {
        public UUID player;
        public String playerName = "";
        public String item = "";
        public int lots;
        public int units;
        public int paid;
        public int delivered;
        public int refund;
        public boolean ready;

        NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putUuid("player", player);
            nbt.putString("name", playerName);
            nbt.putString("item", item);
            nbt.putInt("lots", lots);
            nbt.putInt("units", units);
            nbt.putInt("paid", paid);
            nbt.putInt("delivered", delivered);
            nbt.putInt("refund", refund);
            nbt.putBoolean("ready", ready);
            return nbt;
        }

        static Order fromNbt(NbtCompound nbt) {
            Order order = new Order();
            order.player = nbt.getUuid("player");
            order.playerName = nbt.getString("name");
            order.item = nbt.getString("item");
            order.lots = nbt.getInt("lots");
            order.units = nbt.getInt("units");
            order.paid = nbt.getInt("paid");
            order.delivered = nbt.getInt("delivered");
            order.refund = nbt.getInt("refund");
            order.ready = nbt.getBoolean("ready");
            return order;
        }
    }

    public record TaxEntry(int job, int level, double amount) {
    }

    public record WeekRecord(long week, long income, long upkeep, long decrees, long withdrawn, long balance, double nature, long deposited) {
        NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putLong("week", week);
            nbt.putLong("income", income);
            nbt.putLong("upkeep", upkeep);
            nbt.putLong("decrees", decrees);
            nbt.putLong("withdrawn", withdrawn);
            nbt.putLong("balance", balance);
            nbt.putDouble("nature", nature);
            nbt.putLong("deposited", deposited);
            return nbt;
        }

        static WeekRecord fromNbt(NbtCompound nbt) {
            return new WeekRecord(nbt.getLong("week"), nbt.getLong("income"), nbt.getLong("upkeep"), nbt.getLong("decrees"), nbt.getLong("withdrawn"), nbt.getLong("balance"),
                    nbt.getDouble("nature"), nbt.getLong("deposited"));
        }
    }

    public record Withdrawal(long day, String player, long amount, boolean deposit) {
        NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putLong("day", day);
            nbt.putString("player", player);
            nbt.putLong("amount", amount);
            nbt.putBoolean("deposit", deposit);
            return nbt;
        }

        static Withdrawal fromNbt(NbtCompound nbt) {
            return new Withdrawal(nbt.getLong("day"), nbt.getString("player"), nbt.getLong("amount"), nbt.getBoolean("deposit"));
        }
    }

    public static VillageState of(Village village) {
        return ((Holder) village).mcavie$getState();
    }

    public Set<Integer> decayedIds(Village village) {
        Set<Integer> ids = new HashSet<>();
        for (long packed : decayed) {
            owner(village, BlockPos.fromLong(packed)).ifPresent(b -> ids.add(b.getId()));
        }
        return ids;
    }

    public boolean isDecayed(Village village, Building building) {
        for (long packed : decayed) {
            BlockPos pos = BlockPos.fromLong(packed);
            if (building.containsPos(pos) && owner(village, pos).map(b -> b.getId() == building.getId()).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    public boolean clearDecay(Village village, Building building) {
        return decayed.removeIf(packed -> owner(village, BlockPos.fromLong(packed)).map(b -> b.getId() == building.getId()).orElse(false));
    }

    private static java.util.Optional<Building> owner(Village village, BlockPos pos) {
        return village.getBuildings().values().stream()
                .filter(b -> b.containsPos(pos))
                .min(java.util.Comparator.comparingLong(VillageState::volume).thenComparingInt(Building::getId));
    }

    private static long volume(Building building) {
        BlockPos p0 = building.getPos0();
        BlockPos p1 = building.getPos1();
        return (long) (Math.abs(p1.getX() - p0.getX()) + 1) * (Math.abs(p1.getY() - p0.getY()) + 1) * (Math.abs(p1.getZ() - p0.getZ()) + 1);
    }

    public boolean hasLastDay() {
        return lastDay != NONE;
    }

    public void addHistory(WeekRecord record) {
        history.addFirst(record);
        while (history.size() > HISTORY_SIZE) {
            history.removeLast();
        }
    }

    public void addWithdrawal(Withdrawal withdrawal) {
        journal.addFirst(withdrawal);
        while (journal.size() > JOURNAL_SIZE) {
            journal.removeLast();
        }
    }

    public void addRegistry(CivilRegistry.Entry entry) {
        registry.addFirst(entry);
        trimRegistry();
    }

    private void trimRegistry() {
        int max = Math.max(10, Math.min(1000, VillageConfig.get().registrySize));
        while (registry.size() > max) {
            registry.removeLast();
        }
    }

    public void merge(VillageState other) {
        treasury += other.treasury;
        taxRemainder += other.taxRemainder;
        depositPool += other.depositPool;
        marketRemainder += other.marketRemainder;
        orders.addAll(other.orders);
        registry.addAll(other.registry);
        cases.addAll(other.cases);
        prisoners.putAll(other.prisoners);
        pendingRelease.addAll(other.pendingRelease);
        other.forcedTitles.forEach(forcedTitles::putIfAbsent);
        registry.sort(java.util.Comparator.comparingLong(CivilRegistry.Entry::day).reversed());
        trimRegistry();
        economyActive |= other.economyActive;
        decayed.addAll(other.decayed);
        pendingFamilies = Math.min(VillageImmigration.MAX_PENDING, pendingFamilies + other.pendingFamilies);
        overdraftWeeks = Math.max(overdraftWeeks, other.overdraftWeeks);
        if (other.rank.ordinal() > rank.ordinal()) {
            rank = other.rank;
            descentDaysLeft = other.descentDaysLeft;
        }
        activeDecrees.addAll(other.activeDecrees);
        other.lastLaunch.forEach((decree, day) -> lastLaunch.merge(decree, day, Math::max));
        other.paidWeek.forEach((decree, week) -> paidWeek.merge(decree, week, Math::max));
        other.stock.forEach((item, count) -> stock.merge(item, count, Integer::sum));
        Map<Long, WeekRecord> weeks = new java.util.TreeMap<>(java.util.Comparator.reverseOrder());
        for (WeekRecord record : history) {
            weeks.put(record.week(), record);
        }
        for (WeekRecord record : other.history) {
            weeks.merge(record.week(), record, (a, b) -> new WeekRecord(a.week(), a.income() + b.income(), a.upkeep() + b.upkeep(),
                    a.decrees() + b.decrees(), a.withdrawn() + b.withdrawn(), a.balance() + b.balance(), a.nature() + b.nature(), a.deposited() + b.deposited()));
        }
        history.clear();
        weeks.values().forEach(history::addLast);
        while (history.size() > HISTORY_SIZE) {
            history.removeLast();
        }
        journal.addAll(other.journal);
        journal.sort(java.util.Comparator.comparingLong(Withdrawal::day).reversed());
        while (journal.size() > JOURNAL_SIZE) {
            journal.removeLast();
        }
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("rank", rank.ordinal());
        nbt.putInt("descentDaysLeft", descentDaysLeft);
        if (hasLastDay()) {
            nbt.putLong("lastDay", lastDay);
        }
        nbt.putBoolean("rightsSeen", rightsHolderSeenToday);

        nbt.putBoolean("economyActive", economyActive);
        nbt.putLong("treasury", treasury);
        nbt.putDouble("taxRemainder", taxRemainder);
        nbt.putInt("appliedRate", appliedRate);
        if (lastLevyWeek != NONE) {
            nbt.putLong("lastLevyWeek", lastLevyWeek);
        }
        nbt.putInt("overdraftWeeks", overdraftWeeks);
        nbt.putLongArray("decayedPos", decayed.stream().mapToLong(Long::longValue).toArray());
        nbt.putLong("shareBase", shareBase);
        nbt.putLong("withdrawnThisWeek", withdrawnThisWeek);
        nbt.putLong("withdrawnToday", withdrawnToday);
        nbt.putLong("shareDay", shareDay);
        nbt.putInt("natureShare", natureShare);
        nbt.putLong("depositPool", depositPool);
        nbt.putLong("depositedThisWeek", depositedThisWeek);
        NbtList historyList = new NbtList();
        history.forEach(r -> historyList.add(r.toNbt()));
        nbt.put("history", historyList);
        NbtList journalList = new NbtList();
        journal.forEach(w -> journalList.add(w.toNbt()));
        nbt.put("journal", journalList);

        nbt.putIntArray("activeDecrees", activeDecrees.stream().mapToInt(Enum::ordinal).toArray());
        NbtCompound launches = new NbtCompound();
        lastLaunch.forEach((decree, period) -> launches.putLong(decree.id(), period));
        nbt.put("lastLaunch", launches);
        NbtCompound paid = new NbtCompound();
        paidWeek.forEach((decree, week) -> paid.putLong(decree.id(), week));
        nbt.put("paidWeek", paid);
        nbt.putLong("decreeSpending", decreeSpending);
        nbt.putDouble("immigrationProgress", immigrationProgress);
        nbt.putInt("pendingFamilies", pendingFamilies);
        nbt.putLong("lastArrivalDay", lastArrivalDay);
        NbtCompound roll = new NbtCompound();
        taxRoll.forEach((uuid, entry) -> {
            NbtCompound e = new NbtCompound();
            e.putInt("job", entry.job());
            e.putInt("level", entry.level());
            e.putDouble("amount", entry.amount());
            roll.put(uuid.toString(), e);
        });
        nbt.put("taxRoll", roll);
        NbtList dead = new NbtList();
        deadResidents.forEach(uuid -> dead.add(net.minecraft.nbt.NbtString.of(uuid.toString())));
        nbt.put("deadResidents", dead);

        nbt.putBoolean("marketActive", marketActive);
        if (lastMarketWeek != NONE) {
            nbt.putLong("lastMarketWeek", lastMarketWeek);
            nbt.putLong("lastMerchantWeek", lastMerchantWeek);
        }
        nbt.putDouble("marketRemainder", marketRemainder);
        nbt.putLong("lastMarketSales", lastMarketSales);
        nbt.putLong("lastMarketOrders", lastMarketOrders);
        NbtCompound stockNbt = new NbtCompound();
        stock.forEach(stockNbt::putInt);
        nbt.put("stock", stockNbt);
        NbtList orderList = new NbtList();
        orders.forEach(o -> orderList.add(o.toNbt()));
        nbt.put("orders", orderList);
        NbtList registryList = new NbtList();
        registry.forEach(e -> registryList.add(e.toNbt()));
        nbt.put("registry", registryList);
        NbtList caseList = new NbtList();
        cases.forEach(c -> caseList.add(c.toNbt()));
        nbt.put("cases", caseList);
        nbt.putBoolean("prisonActive", prisonActive);
        NbtList releaseList = new NbtList();
        pendingRelease.forEach(uuid -> releaseList.add(net.minecraft.nbt.NbtHelper.fromUuid(uuid)));
        nbt.put("pendingRelease", releaseList);
        NbtCompound prisonerNbt = new NbtCompound();
        prisoners.forEach((uuid, p) -> {
            NbtCompound e = new NbtCompound();
            e.putString("name", p.name);
            e.putLong("release", p.releaseDay);
            prisonerNbt.put(uuid.toString(), e);
        });
        nbt.put("prisoners", prisonerNbt);
        NbtCompound titleNbt = new NbtCompound();
        forcedTitles.forEach((uuid, title) -> titleNbt.putString(uuid.toString(), title.name()));
        nbt.put("forcedTitles", titleNbt);
        return nbt;
    }

    public void readNbt(NbtCompound nbt) {
        rank = VillageRank.byIndex(nbt.getInt("rank"));
        descentDaysLeft = nbt.contains("descentDaysLeft") ? nbt.getInt("descentDaysLeft") : -1;
        lastDay = nbt.contains("lastDay") ? nbt.getLong("lastDay") : NONE;
        rightsHolderSeenToday = nbt.getBoolean("rightsSeen");

        economyActive = nbt.getBoolean("economyActive");
        treasury = nbt.getLong("treasury");
        taxRemainder = nbt.getDouble("taxRemainder");
        appliedRate = nbt.contains("appliedRate") ? nbt.getInt("appliedRate") : -1;
        lastLevyWeek = nbt.contains("lastLevyWeek") ? nbt.getLong("lastLevyWeek") : NONE;
        overdraftWeeks = nbt.getInt("overdraftWeeks");
        decayed.clear();
        for (long pos : nbt.getLongArray("decayedPos")) {
            decayed.add(pos);
        }
        shareBase = nbt.getLong("shareBase");
        withdrawnThisWeek = nbt.getLong("withdrawnThisWeek");
        withdrawnToday = nbt.getLong("withdrawnToday");
        shareDay = nbt.contains("shareDay") ? nbt.getLong("shareDay") : NONE;
        natureShare = Math.max(0, Math.min(100, nbt.getInt("natureShare")));
        depositPool = nbt.getLong("depositPool");
        depositedThisWeek = nbt.getLong("depositedThisWeek");
        history.clear();
        nbt.getList("history", NbtElement.COMPOUND_TYPE).forEach(e -> history.add(WeekRecord.fromNbt((NbtCompound) e)));
        journal.clear();
        nbt.getList("journal", NbtElement.COMPOUND_TYPE).forEach(e -> journal.add(Withdrawal.fromNbt((NbtCompound) e)));

        activeDecrees.clear();
        for (int index : nbt.getIntArray("activeDecrees")) {
            Decree decree = Decree.byIndex(index);
            if (decree != null) {
                activeDecrees.add(decree);
            }
        }
        lastLaunch.clear();
        paidWeek.clear();
        NbtCompound launches = nbt.getCompound("lastLaunch");
        NbtCompound paid = nbt.getCompound("paidWeek");
        for (Decree decree : Decree.values()) {
            if (launches.contains(decree.id())) {
                lastLaunch.put(decree, launches.getLong(decree.id()));
            }
            if (paid.contains(decree.id())) {
                paidWeek.put(decree, paid.getLong(decree.id()));
            }
        }
        decreeSpending = nbt.getLong("decreeSpending");
        immigrationProgress = nbt.getDouble("immigrationProgress");
        pendingFamilies = nbt.getInt("pendingFamilies");
        lastArrivalDay = nbt.contains("lastArrivalDay") ? nbt.getLong("lastArrivalDay") : NONE;
        taxRoll.clear();
        NbtCompound roll = nbt.getCompound("taxRoll");
        for (String key : roll.getKeys()) {
            try {
                NbtCompound e = roll.getCompound(key);
                taxRoll.put(UUID.fromString(key), new TaxEntry(e.getInt("job"), e.getInt("level"), e.getDouble("amount")));
            } catch (IllegalArgumentException ignored) {
            }
        }
        deadResidents.clear();
        for (NbtElement e : nbt.getList("deadResidents", NbtElement.STRING_TYPE)) {
            try {
                deadResidents.add(UUID.fromString(e.asString()));
            } catch (IllegalArgumentException ignored) {
            }
        }

        marketActive = nbt.getBoolean("marketActive");
        lastMarketWeek = nbt.contains("lastMarketWeek") ? nbt.getLong("lastMarketWeek") : NONE;
        lastMerchantWeek = nbt.contains("lastMerchantWeek") ? nbt.getLong("lastMerchantWeek") : NONE;
        marketRemainder = nbt.getDouble("marketRemainder");
        lastMarketSales = nbt.getLong("lastMarketSales");
        lastMarketOrders = nbt.getLong("lastMarketOrders");
        stock.clear();
        NbtCompound stockNbt = nbt.getCompound("stock");
        for (String key : stockNbt.getKeys()) {
            stock.put(key, stockNbt.getInt(key));
        }
        orders.clear();
        nbt.getList("orders", NbtElement.COMPOUND_TYPE).forEach(e -> {
            NbtCompound o = (NbtCompound) e;
            if (o.containsUuid("player")) {
                orders.add(Order.fromNbt(o));
            }
        });
        registry.clear();
        nbt.getList("registry", NbtElement.COMPOUND_TYPE).forEach(e -> registry.add(CivilRegistry.Entry.fromNbt((NbtCompound) e)));
        trimRegistry();
        prisonActive = nbt.getBoolean("prisonActive");
        forcedTitles.clear();
        NbtCompound titleNbt = nbt.getCompound("forcedTitles");
        for (String key : titleNbt.getKeys()) {
            try {
                forcedTitles.put(UUID.fromString(key), net.mca.resources.Rank.valueOf(titleNbt.getString(key)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        pendingRelease.clear();
        nbt.getList("pendingRelease", NbtElement.INT_ARRAY_TYPE).forEach(e -> pendingRelease.add(net.minecraft.nbt.NbtHelper.toUuid(e)));
        cases.clear();
        nbt.getList("cases", NbtElement.COMPOUND_TYPE).forEach(e -> {
            NbtCompound c = (NbtCompound) e;
            if (c.containsUuid("suspect")) {
                cases.add(Case.fromNbt(c));
            }
        });
        prisoners.clear();
        NbtCompound prisonerNbt = nbt.getCompound("prisoners");
        for (String key : prisonerNbt.getKeys()) {
            try {
                Prisoner p = new Prisoner();
                p.name = prisonerNbt.getCompound(key).getString("name");
                p.releaseDay = prisonerNbt.getCompound(key).getLong("release");
                prisoners.put(UUID.fromString(key), p);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public interface Holder {
        VillageState mcavie$getState();
    }
}
