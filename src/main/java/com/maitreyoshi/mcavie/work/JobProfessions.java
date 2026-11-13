package com.maitreyoshi.mcavie.work;

import com.google.common.collect.ImmutableSet;
import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.compat.Registration;
import com.maitreyoshi.mcavie.mixin.VillagerEntityInvoker;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import java.util.ArrayList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.trading.MerchantOffer;

public final class JobProfessions {
    private static final Map<Job, VillagerProfession> BY_JOB = new EnumMap<>(Job.class);
    private static final int[] TRADE_XP = {2, 10, 20, 30, 30};
    private static final Map<Job, List<List<Factory>>> TRADES = new EnumMap<>(Job.class);

    private JobProfessions() {
    }

    @Nullable
    public static VillagerProfession of(Job job) {
        return BY_JOB.get(job);
    }

    public static void register() {
        int expected = 0;
        for (Job job : Job.values()) {
            if (job.isCraft()) {
                expected++;
            }
        }
        int total = expected;
        for (Job job : Job.values()) {
            if (!job.isCraft()) {
                continue;
            }
            Registration.register(Registries.VILLAGER_PROFESSION, job.id(), () -> new VillagerProfession(
                    Component.translatable("entity.minecraft.villager." + MCAVie.MOD_ID + "." + job.id()),
                    holder -> false, holder -> false, ImmutableSet.of(), ImmutableSet.of(), null, Int2ObjectMaps.emptyMap()), profession -> {
                BY_JOB.put(job, profession);
                if (BY_JOB.size() == total) {
                    registerTrades();
                }
            });
        }
    }

    private static void registerTrades() {
        trades(Job.FARMER,
                List.of(buy(Items.WHEAT, 20, 1), sell(Items.BREAD, 6, 1)),
                List.of(buy(Items.POTATO, 26, 1), buy(Items.CARROT, 22, 1)),
                List.of(buy(Items.PUMPKIN, 6, 1), sell(Items.PUMPKIN_PIE, 4, 1)),
                List.of(buy(Items.MELON, 4, 1), sell(Items.CAKE, 1, 1)),
                List.of(sell(Items.GOLDEN_CARROT, 3, 3)));
        trades(Job.LUMBERJACK,
                List.of(buy(Items.STICK, 32, 1), sell(Items.OAK_LOG, 6, 1)),
                List.of(sell(Items.SPRUCE_LOG, 6, 1), sell(Items.CHARCOAL, 4, 1)),
                List.of(sell(Items.BIRCH_LOG, 6, 1), sell(Items.APPLE, 4, 1)),
                List.of(sell(Items.DARK_OAK_LOG, 6, 1)),
                List.of(sell(Items.IRON_AXE, 1, 6)));
        trades(Job.FISHER,
                List.of(buy(Items.STRING, 20, 1), sell(Items.COOKED_COD, 6, 1)),
                List.of(buy(Items.COD, 15, 1), sell(Items.COOKED_SALMON, 6, 1)),
                List.of(buy(Items.SALMON, 13, 1), sell(Items.FISHING_ROD, 1, 3)),
                List.of(buy(Items.TROPICAL_FISH, 6, 1)),
                List.of(sell(Items.COD_BUCKET, 1, 5)));
        trades(Job.BREEDER,
                List.of(buy(Items.WHEAT, 20, 1), sell(Items.WOOL.white(), 3, 1)),
                List.of(buy(Items.CARROT, 22, 1), sell(Items.EGG, 8, 1)),
                List.of(sell(Items.LEATHER, 3, 1), sell(Items.SHEARS, 1, 2)),
                List.of(sell(Items.LEAD, 1, 2)),
                List.of(sell(Items.SADDLE, 1, 6)));
        trades(Job.DOCTOR,
                List.of(buy(Items.ROTTEN_FLESH, 32, 1), sell(() -> potion(Potions.HEALING), 3)),
                List.of(buy(Items.GLASS_BOTTLE, 9, 1), sell(() -> potion(Potions.REGENERATION), 5)),
                List.of(buy(Items.SUGAR, 20, 1), sell(Items.GOLDEN_APPLE, 1, 8)),
                List.of(sell(Items.MILK_BUCKET, 1, 2)),
                List.of(sell(Items.GLISTERING_MELON_SLICE, 3, 4)));
        trades(Job.BLACKSMITH,
                List.of(buy(Items.COAL, 15, 1), sell(Items.STONE_PICKAXE, 1, 1)),
                List.of(buy(Items.IRON_INGOT, 4, 1), sell(Items.IRON_PICKAXE, 1, 3)),
                List.of(sell(Items.IRON_AXE, 1, 3), sell(Items.IRON_SHOVEL, 1, 2)),
                List.of(sell(Items.IRON_SWORD, 1, 4)),
                List.of(sell(Items.DIAMOND_AXE, 1, 17)));
        trades(Job.BAKER,
                List.of(buy(Items.WHEAT, 20, 1), sell(Items.BREAD, 6, 1)),
                List.of(buy(Items.EGG, 16, 1), sell(Items.COOKIE, 18, 3)),
                List.of(buy(Items.SUGAR_CANE, 20, 1), sell(Items.PUMPKIN_PIE, 4, 1)),
                List.of(sell(Items.CAKE, 1, 1)),
                List.of(sell(Items.HONEY_BOTTLE, 2, 2)));
        trades(Job.TEACHER,
                List.of(buy(Items.PAPER, 24, 1), sell(Items.WRITABLE_BOOK, 1, 2)),
                List.of(buy(Items.FEATHER, 16, 1), sell(Items.MAP, 1, 2)),
                List.of(sell(Items.COMPASS, 1, 4), sell(Items.CLOCK, 1, 5)),
                List.of(sell(Items.BOOKSHELF, 1, 9)),
                List.of(sell(Items.NAME_TAG, 1, 15)));
        trades(Job.MINER,
                List.of(buy(Items.TORCH, 24, 1), sell(Items.COBBLESTONE, 32, 1)),
                List.of(buy(Items.STONE_PICKAXE, 1, 1), sell(Items.COAL, 8, 1)),
                List.of(sell(Items.RAW_IRON, 4, 2), sell(Items.RAW_COPPER, 8, 1)),
                List.of(sell(Items.LAPIS_LAZULI, 6, 2)),
                List.of(sell(Items.DIAMOND, 1, 12)));
        trades(Job.COOK,
                List.of(buy(Items.POTATO, 26, 1), sell(Items.BAKED_POTATO, 8, 1)),
                List.of(buy(Items.BEEF, 10, 1), sell(Items.COOKED_BEEF, 5, 1)),
                List.of(buy(Items.BOWL, 12, 1), sell(Items.MUSHROOM_STEW, 1, 1)),
                List.of(sell(Items.RABBIT_STEW, 1, 2)),
                List.of(sell(Items.GOLDEN_CARROT, 4, 3)));
    }

    /**
     * Aligne le niveau d'échange vanilla sur le niveau de métier (Apprenti 1 ... Maître 5) :
     * chaque niveau gagné ajoute les offres de ce niveau, comme une montée de niveau vanilla.
     */
    public static void syncTradeLevel(Villager villager, Job job, int jobLevel) {
        VillagerProfession profession = BY_JOB.get(job);
        if (profession == null || villager.level().isClientSide() || villager.getVillagerData().profession().value() != profession) {
            return;
        }
        int target = Math.max(1, Math.min(5, jobLevel));
        int current = villager.getVillagerData().level();
        if (current >= target) {
            return;
        }
        villager.getOffers();
        while (current < target) {
            current++;
            villager.setVillagerData(villager.getVillagerData().withLevel(current));
            ((VillagerEntityInvoker) villager).mcavie$fillRecipes((ServerLevel) villager.level());
        }
        villager.setVillagerXp(Math.max(villager.getVillagerXp(), VillagerData.getMinXpPerLevel(target)));
    }

    @SafeVarargs
    private static void trades(Job job, List<Factory>... levels) {
        TRADES.put(job, List.of(levels));
    }

    /**
     * Offres de nos métiers (les échanges vanilla 26.x sont en données, on garde les nôtres en code) :
     * appelé par le mixin de Villager.updateTrades ; renvoie false si ce n'est pas un de nos métiers.
     */
    public static boolean addTrades(Villager villager, RandomSource random) {
        VillagerProfession profession = villager.getVillagerData().profession().value();
        for (Map.Entry<Job, VillagerProfession> entry : BY_JOB.entrySet()) {
            if (entry.getValue() != profession) {
                continue;
            }
            List<List<Factory>> levels = TRADES.get(entry.getKey());
            int level = villager.getVillagerData().level();
            if (levels == null || level < 1 || level > levels.size()) {
                return true;
            }
            List<Factory> pool = new ArrayList<>(levels.get(level - 1));
            MerchantOffers offers = villager.getOffers();
            for (int i = 0; i < 2 && !pool.isEmpty(); i++) {
                offers.add(pool.remove(random.nextInt(pool.size())).create(TRADE_XP[level - 1]));
            }
            return true;
        }
        return false;
    }

    private interface Factory {
        MerchantOffer create(int xp);
    }

    private static Factory buy(Item item, int count, int emeralds) {
        return xp -> new MerchantOffer(new ItemCost(item, count), new ItemStack(Items.EMERALD, emeralds), 16, xp, 0.05F);
    }

    private static Factory sell(Item item, int count, int emeralds) {
        return sell(() -> new ItemStack(item, count), emeralds);
    }

    private static Factory sell(java.util.function.Supplier<ItemStack> stack, int emeralds) {
        return xp -> new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), stack.get(), 12, xp, 0.05F);
    }

    private static ItemStack potion(Holder<Potion> potion) {
        return PotionContents.createItemStack(Items.POTION, potion);
    }
}
