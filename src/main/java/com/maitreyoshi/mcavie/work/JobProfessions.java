package com.maitreyoshi.mcavie.work;

import com.google.common.collect.ImmutableSet;
import com.maitreyoshi.mcavie.MCAVie;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.poi.PointOfInterestType;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class JobProfessions {
    private static final Map<Job, VillagerProfession> BY_JOB = new EnumMap<>(Job.class);
    private static final int[] TRADE_XP = {2, 10, 20, 30, 30};

    private JobProfessions() {
    }

    @Nullable
    public static VillagerProfession of(Job job) {
        return BY_JOB.get(job);
    }

    public static void register() {
        for (Job job : Job.values()) {
            if (!job.isCraft()) {
                continue;
            }
            VillagerProfession profession = new VillagerProfession(MCAVie.MOD_ID + "." + job.id(),
                    PointOfInterestType.NONE, PointOfInterestType.NONE, ImmutableSet.of(), ImmutableSet.of(), null);
            BY_JOB.put(job, Registry.register(Registries.VILLAGER_PROFESSION, MCAVie.id(job.id()), profession));
        }
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
                List.of(buy(Items.WHEAT, 20, 1), sell(Items.WHITE_WOOL, 3, 1)),
                List.of(buy(Items.CARROT, 22, 1), sell(Items.EGG, 8, 1)),
                List.of(sell(Items.LEATHER, 3, 1), sell(Items.SHEARS, 1, 2)),
                List.of(sell(Items.LEAD, 1, 2)),
                List.of(sell(Items.SADDLE, 1, 6)));
        trades(Job.DOCTOR,
                List.of(buy(Items.ROTTEN_FLESH, 32, 1), sell(potion(Potions.HEALING), 3)),
                List.of(buy(Items.GLASS_BOTTLE, 9, 1), sell(potion(Potions.REGENERATION), 5)),
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

    @SafeVarargs
    private static void trades(Job job, List<Factory>... levels) {
        VillagerProfession profession = BY_JOB.get(job);
        for (int i = 0; i < levels.length; i++) {
            int level = i + 1;
            List<Factory> offers = levels[i];
            TradeOfferHelper.registerVillagerOffers(profession, level, factories -> {
                for (Factory offer : offers) {
                    factories.add((entity, random) -> offer.create(TRADE_XP[level - 1]));
                }
            });
        }
    }

    private interface Factory {
        TradeOffer create(int xp);
    }

    private static Factory buy(Item item, int count, int emeralds) {
        return xp -> new TradeOffer(new ItemStack(item, count), new ItemStack(Items.EMERALD, emeralds), 16, xp, 0.05F);
    }

    private static Factory sell(Item item, int count, int emeralds) {
        return sell(new ItemStack(item, count), emeralds);
    }

    private static Factory sell(ItemStack stack, int emeralds) {
        return xp -> new TradeOffer(new ItemStack(Items.EMERALD, emeralds), stack.copy(), 12, xp, 0.05F);
    }

    private static ItemStack potion(net.minecraft.potion.Potion potion) {
        return PotionUtil.setPotion(new ItemStack(Items.POTION), potion);
    }
}
