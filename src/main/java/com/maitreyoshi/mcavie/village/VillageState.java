package com.maitreyoshi.mcavie.village;

import com.maitreyoshi.mcavie.compat.Nbt;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
    public final Map<UUID, net.conczin.mca.resources.Rank> forcedTitles = new HashMap<>();

    public static final class Case {
        public UUID suspect;
        public String name = "";
        public long day;
        public long amount;

        CompoundTag toNbt() {
            CompoundTag nbt = new CompoundTag();
            Nbt.putUUID(nbt, "suspect", suspect);
            nbt.putString("name", name);
            nbt.putLong("day", day);
            nbt.putLong("amount", amount);
            return nbt;
        }

        static Case fromNbt(CompoundTag nbt) {
            Case c = new Case();
            c.suspect = Nbt.getUUID(nbt, "suspect");
            c.name = nbt.getStringOr("name", "");
            c.day = nbt.getLongOr("day", 0L);
            c.amount = nbt.getLongOr("amount", 0L);
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

        CompoundTag toNbt() {
            CompoundTag nbt = new CompoundTag();
            Nbt.putUUID(nbt, "player", player);
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

        static Order fromNbt(CompoundTag nbt) {
            Order order = new Order();
            order.player = Nbt.getUUID(nbt, "player");
            order.playerName = nbt.getStringOr("name", "");
            order.item = nbt.getStringOr("item", "");
            order.lots = nbt.getIntOr("lots", 0);
            order.units = nbt.getIntOr("units", 0);
            order.paid = nbt.getIntOr("paid", 0);
            order.delivered = nbt.getIntOr("delivered", 0);
            order.refund = nbt.getIntOr("refund", 0);
            order.ready = nbt.getBooleanOr("ready", false);
            return order;
        }
    }

    public record TaxEntry(int job, int level, double amount) {
    }

    public record WeekRecord(long week, long income, long upkeep, long decrees, long withdrawn, long balance, double nature, long deposited) {
        CompoundTag toNbt() {
            CompoundTag nbt = new CompoundTag();
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

        static WeekRecord fromNbt(CompoundTag nbt) {
            return new WeekRecord(nbt.getLongOr("week", 0L), nbt.getLongOr("income", 0L), nbt.getLongOr("upkeep", 0L), nbt.getLongOr("decrees", 0L), nbt.getLongOr("withdrawn", 0L), nbt.getLongOr("balance", 0L),
                    nbt.getDoubleOr("nature", 0d), nbt.getLongOr("deposited", 0L));
        }
    }

    public record Withdrawal(long day, String player, long amount, boolean deposit) {
        CompoundTag toNbt() {
            CompoundTag nbt = new CompoundTag();
            nbt.putLong("day", day);
            nbt.putString("player", player);
            nbt.putLong("amount", amount);
            nbt.putBoolean("deposit", deposit);
            return nbt;
        }

        static Withdrawal fromNbt(CompoundTag nbt) {
            return new Withdrawal(nbt.getLongOr("day", 0L), nbt.getStringOr("player", ""), nbt.getLongOr("amount", 0L), nbt.getBooleanOr("deposit", false));
        }
    }

    public static VillageState of(Village village) {
        return ((Holder) village).mcavie$getState();
    }

    public Set<Integer> decayedIds(Village village) {
        Set<Integer> ids = new HashSet<>();
        for (long packed : decayed) {
            owner(village, BlockPos.of(packed)).ifPresent(b -> ids.add(b.getId()));
        }
        return ids;
    }

    public boolean isDecayed(Village village, Building building) {
        for (long packed : decayed) {
            BlockPos pos = BlockPos.of(packed);
            if (building.containsPos(pos) && owner(village, pos).map(b -> b.getId() == building.getId()).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    public boolean clearDecay(Village village, Building building) {
        return decayed.removeIf(packed -> owner(village, BlockPos.of(packed)).map(b -> b.getId() == building.getId()).orElse(false));
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

    public CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();
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
        ListTag historyList = new ListTag();
        history.forEach(r -> historyList.add(r.toNbt()));
        nbt.put("history", historyList);
        ListTag journalList = new ListTag();
        journal.forEach(w -> journalList.add(w.toNbt()));
        nbt.put("journal", journalList);

        nbt.putIntArray("activeDecrees", activeDecrees.stream().mapToInt(Enum::ordinal).toArray());
        CompoundTag launches = new CompoundTag();
        lastLaunch.forEach((decree, period) -> launches.putLong(decree.id(), period));
        nbt.put("lastLaunch", launches);
        CompoundTag paid = new CompoundTag();
        paidWeek.forEach((decree, week) -> paid.putLong(decree.id(), week));
        nbt.put("paidWeek", paid);
        nbt.putLong("decreeSpending", decreeSpending);
        nbt.putDouble("immigrationProgress", immigrationProgress);
        nbt.putInt("pendingFamilies", pendingFamilies);
        nbt.putLong("lastArrivalDay", lastArrivalDay);
        CompoundTag roll = new CompoundTag();
        taxRoll.forEach((uuid, entry) -> {
            CompoundTag e = new CompoundTag();
            e.putInt("job", entry.job());
            e.putInt("level", entry.level());
            e.putDouble("amount", entry.amount());
            roll.put(uuid.toString(), e);
        });
        nbt.put("taxRoll", roll);
        ListTag dead = new ListTag();
        deadResidents.forEach(uuid -> dead.add(net.minecraft.nbt.StringTag.valueOf(uuid.toString())));
        nbt.put("deadResidents", dead);

        nbt.putBoolean("marketActive", marketActive);
        if (lastMarketWeek != NONE) {
            nbt.putLong("lastMarketWeek", lastMarketWeek);
            nbt.putLong("lastMerchantWeek", lastMerchantWeek);
        }
        nbt.putDouble("marketRemainder", marketRemainder);
        nbt.putLong("lastMarketSales", lastMarketSales);
        nbt.putLong("lastMarketOrders", lastMarketOrders);
        CompoundTag stockNbt = new CompoundTag();
        stock.forEach(stockNbt::putInt);
        nbt.put("stock", stockNbt);
        ListTag orderList = new ListTag();
        orders.forEach(o -> orderList.add(o.toNbt()));
        nbt.put("orders", orderList);
        ListTag registryList = new ListTag();
        registry.forEach(e -> registryList.add(e.toNbt()));
        nbt.put("registry", registryList);
        ListTag caseList = new ListTag();
        cases.forEach(c -> caseList.add(c.toNbt()));
        nbt.put("cases", caseList);
        nbt.putBoolean("prisonActive", prisonActive);
        ListTag releaseList = new ListTag();
        pendingRelease.forEach(uuid -> releaseList.add(net.minecraft.nbt.NbtUtils.createUUID(uuid)));
        nbt.put("pendingRelease", releaseList);
        CompoundTag prisonerNbt = new CompoundTag();
        prisoners.forEach((uuid, p) -> {
            CompoundTag e = new CompoundTag();
            e.putString("name", p.name);
            e.putLong("release", p.releaseDay);
            prisonerNbt.put(uuid.toString(), e);
        });
        nbt.put("prisoners", prisonerNbt);
        CompoundTag titleNbt = new CompoundTag();
        forcedTitles.forEach((uuid, title) -> titleNbt.putString(uuid.toString(), title.name()));
        nbt.put("forcedTitles", titleNbt);
        return nbt;
    }

    public void readNbt(CompoundTag nbt) {
        rank = VillageRank.byIndex(nbt.getIntOr("rank", 0));
        descentDaysLeft = nbt.contains("descentDaysLeft") ? nbt.getIntOr("descentDaysLeft", 0) : -1;
        lastDay = nbt.contains("lastDay") ? nbt.getLongOr("lastDay", 0L) : NONE;
        rightsHolderSeenToday = nbt.getBooleanOr("rightsSeen", false);

        economyActive = nbt.getBooleanOr("economyActive", false);
        treasury = nbt.getLongOr("treasury", 0L);
        taxRemainder = nbt.getDoubleOr("taxRemainder", 0d);
        appliedRate = nbt.contains("appliedRate") ? nbt.getIntOr("appliedRate", 0) : -1;
        lastLevyWeek = nbt.contains("lastLevyWeek") ? nbt.getLongOr("lastLevyWeek", 0L) : NONE;
        overdraftWeeks = nbt.getIntOr("overdraftWeeks", 0);
        decayed.clear();
        for (long pos : nbt.getLongArray("decayedPos").orElse(new long[0])) {
            decayed.add(pos);
        }
        shareBase = nbt.getLongOr("shareBase", 0L);
        withdrawnThisWeek = nbt.getLongOr("withdrawnThisWeek", 0L);
        withdrawnToday = nbt.getLongOr("withdrawnToday", 0L);
        shareDay = nbt.contains("shareDay") ? nbt.getLongOr("shareDay", 0L) : NONE;
        natureShare = Math.max(0, Math.min(100, nbt.getIntOr("natureShare", 0)));
        depositPool = nbt.getLongOr("depositPool", 0L);
        depositedThisWeek = nbt.getLongOr("depositedThisWeek", 0L);
        history.clear();
        nbt.getListOrEmpty("history").forEach(e -> history.add(WeekRecord.fromNbt((CompoundTag) e)));
        journal.clear();
        nbt.getListOrEmpty("journal").forEach(e -> journal.add(Withdrawal.fromNbt((CompoundTag) e)));

        activeDecrees.clear();
        for (int index : nbt.getIntArray("activeDecrees").orElse(new int[0])) {
            Decree decree = Decree.byIndex(index);
            if (decree != null) {
                activeDecrees.add(decree);
            }
        }
        lastLaunch.clear();
        paidWeek.clear();
        CompoundTag launches = nbt.getCompoundOrEmpty("lastLaunch");
        CompoundTag paid = nbt.getCompoundOrEmpty("paidWeek");
        for (Decree decree : Decree.values()) {
            if (launches.contains(decree.id())) {
                lastLaunch.put(decree, launches.getLongOr(decree.id(), 0L));
            }
            if (paid.contains(decree.id())) {
                paidWeek.put(decree, paid.getLongOr(decree.id(), 0L));
            }
        }
        decreeSpending = nbt.getLongOr("decreeSpending", 0L);
        immigrationProgress = nbt.getDoubleOr("immigrationProgress", 0d);
        pendingFamilies = nbt.getIntOr("pendingFamilies", 0);
        lastArrivalDay = nbt.contains("lastArrivalDay") ? nbt.getLongOr("lastArrivalDay", 0L) : NONE;
        taxRoll.clear();
        CompoundTag roll = nbt.getCompoundOrEmpty("taxRoll");
        for (String key : roll.keySet()) {
            try {
                CompoundTag e = roll.getCompoundOrEmpty(key);
                taxRoll.put(UUID.fromString(key), new TaxEntry(e.getIntOr("job", 0), e.getIntOr("level", 0), e.getDoubleOr("amount", 0d)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        deadResidents.clear();
        for (Tag e : nbt.getListOrEmpty("deadResidents")) {
            try {
                deadResidents.add(UUID.fromString(e.getAsString()));
            } catch (IllegalArgumentException ignored) {
            }
        }

        marketActive = nbt.getBooleanOr("marketActive", false);
        lastMarketWeek = nbt.contains("lastMarketWeek") ? nbt.getLongOr("lastMarketWeek", 0L) : NONE;
        lastMerchantWeek = nbt.contains("lastMerchantWeek") ? nbt.getLongOr("lastMerchantWeek", 0L) : NONE;
        marketRemainder = nbt.getDoubleOr("marketRemainder", 0d);
        lastMarketSales = nbt.getLongOr("lastMarketSales", 0L);
        lastMarketOrders = nbt.getLongOr("lastMarketOrders", 0L);
        stock.clear();
        CompoundTag stockNbt = nbt.getCompoundOrEmpty("stock");
        for (String key : stockNbt.keySet()) {
            stock.put(key, stockNbt.getIntOr(key, 0));
        }
        orders.clear();
        nbt.getListOrEmpty("orders").forEach(e -> {
            CompoundTag o = (CompoundTag) e;
            if (Nbt.hasUUID(o, "player")) {
                orders.add(Order.fromNbt(o));
            }
        });
        registry.clear();
        nbt.getListOrEmpty("registry").forEach(e -> registry.add(CivilRegistry.Entry.fromNbt((CompoundTag) e)));
        trimRegistry();
        prisonActive = nbt.getBooleanOr("prisonActive", false);
        forcedTitles.clear();
        CompoundTag titleNbt = nbt.getCompoundOrEmpty("forcedTitles");
        for (String key : titleNbt.keySet()) {
            try {
                forcedTitles.put(UUID.fromString(key), net.conczin.mca.resources.Rank.valueOf(titleNbt.getStringOr(key, "")));
            } catch (IllegalArgumentException ignored) {
            }
        }
        pendingRelease.clear();
        nbt.getListOrEmpty("pendingRelease").forEach(e -> pendingRelease.add(net.minecraft.nbt.NbtUtils.loadUUID(e)));
        cases.clear();
        nbt.getListOrEmpty("cases").forEach(e -> {
            CompoundTag c = (CompoundTag) e;
            if (Nbt.hasUUID(c, "suspect")) {
                cases.add(Case.fromNbt(c));
            }
        });
        prisoners.clear();
        CompoundTag prisonerNbt = nbt.getCompoundOrEmpty("prisoners");
        for (String key : prisonerNbt.keySet()) {
            try {
                Prisoner p = new Prisoner();
                p.name = prisonerNbt.getCompoundOrEmpty(key).getStringOr("name", "");
                p.releaseDay = prisonerNbt.getCompoundOrEmpty(key).getLongOr("release", 0L);
                prisoners.put(UUID.fromString(key), p);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public interface Holder {
        VillageState mcavie$getState();
    }
}
