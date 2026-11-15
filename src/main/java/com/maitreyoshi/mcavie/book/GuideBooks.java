package com.maitreyoshi.mcavie.book;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.compat.Registration;
import dev.architectury.registry.level.entity.trade.TradeRegistry;
import net.mca.client.book.Book;
import net.mca.client.book.pages.TextPage;
import net.mca.client.book.pages.TitlePage;
import net.mca.item.ExtendedWrittenBookItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.VillagerProfession;

import java.util.function.Supplier;

public final class GuideBooks {
    private static final String M = "mca_vie.books.memory.";
    private static final String T = "mca_vie.books.tombs.";

    public static Item MEMORY_BOOK;
    public static Item TOMBS_BOOK;

    private GuideBooks() {
    }

    public static void register() {
        Registration.register(RegistryKeys.ITEM, "memory_book",
                () -> new ExtendedWrittenBookItem(new Item.Settings().maxCount(1), memoryBook()), item -> MEMORY_BOOK = item);
        Registration.register(RegistryKeys.ITEM, "tombs_book",
                () -> new ExtendedWrittenBookItem(new Item.Settings().maxCount(1), tombsBook()), item -> TOMBS_BOOK = item);
        TradeOffers.Factory memory = sell(() -> MEMORY_BOOK);
        TradeOffers.Factory tombs = sell(() -> TOMBS_BOOK);
        TradeRegistry.registerVillagerTrade(VillagerProfession.CLERIC, 1, memory, tombs);
        TradeRegistry.registerVillagerTrade(VillagerProfession.LIBRARIAN, 1, memory, tombs);
    }

    private static TradeOffers.Factory sell(Supplier<Item> book) {
        return (entity, random) -> new TradeOffer(new ItemStack(Items.EMERALD, 2), new ItemStack(book.get()), 12, 2, 0.05F);
    }

    private static Book memoryBook() {
        return new Book("memory", Text.translatable(M + "author").formatted(Formatting.GRAY))
                .setBackground(MCAVie.id("textures/gui/books/happiness.png"))
                .addPage(new TitlePage("item.mca_vie.memory_book", M + "author"))
                .addPage(new TextPage("{\"translate\":\"" + M + "intro\"}"))
                .addPage(new IllustratedPage(M + "build")
                        .recipe("mca_vie:ghost_altar",
                                "minecraft:candle", "minecraft:gold_nugget", "minecraft:candle",
                                "minecraft:dark_oak_planks", "minecraft:item_frame", "minecraft:dark_oak_planks",
                                "", "", "")
                        .note(M + "wall"))
                .addPage(new IllustratedPage(M + "photo")
                        .info("mca_vie:phone", M + "take_photo", "")
                        .info("minecraft:oak_sapling", M + "set_portrait", "")
                        .note(M + "photo.note"))
                .addPage(new IllustratedPage(M + "choose")
                        .info("minecraft:leather_boots", M + "sneak", "")
                        .info("mca_vie:ghost_altar", M + "click", "")
                        .info("minecraft:writable_book", M + "pick", "")
                        .note(M + "choose.note"))
                .addPage(new IllustratedPage(M + "night")
                        .info("minecraft:black_candle", M + "appears", "")
                        .info("minecraft:glass_pane", M + "ghostly", "")
                        .info("minecraft:sunflower", M + "dawn", "")
                        .note(M + "night.note"));
    }

    private static Book tombsBook() {
        return new Book("tombs", Text.translatable(T + "author").formatted(Formatting.GRAY))
                .setBackground(MCAVie.id("textures/gui/books/happiness.png"))
                .addPage(new TitlePage("item.mca_vie.tombs_book", T + "author"))
                .addPage(new TextPage("{\"translate\":\"" + T + "intro\"}"))
                .addPage(new IllustratedPage(T + "kinds")
                        .info("mca_vie:wooden_cross_tomb", T + "cross", "")
                        .info("mca_vie:stele_tomb", T + "stele", "")
                        .info("mca_vie:marble_tomb", T + "marble", "")
                        .info("mca:upright_headstone", T + "mca", ""))
                .addPage(new IllustratedPage(T + "reserve")
                        .info("minecraft:leather_boots", T + "sneak", "")
                        .info("mca_vie:stele_tomb", T + "click", "")
                        .info("minecraft:name_tag", T + "pick", "")
                        .note(T + "reserve.note"))
                .addPage(new IllustratedPage(T + "vault")
                        .big("mca_vie:family_vault")
                        .line(T + "vault_size", "3×2×2")
                        .line(T + "vault_places", "4")
                        .note(T + "vault.note"))
                .addPage(new IllustratedPage(T + "vault_craft")
                        .recipe("mca_vie:family_vault",
                                "minecraft:quartz_block", "minecraft:polished_blackstone", "minecraft:quartz_block",
                                "minecraft:quartz_block", "minecraft:gold_ingot", "minecraft:quartz_block",
                                "minecraft:smooth_stone", "minecraft:smooth_stone", "minecraft:smooth_stone"))
                .addPage(new IllustratedPage(T + "vault_name")
                        .intro(T + "vault_name.intro"))
                .addPage(new MemorialModelPage())
                .addPage(new IllustratedPage(T + "memorial")
                        .recipe("mca_vie:war_memorial",
                                "minecraft:chiseled_quartz_block", "minecraft:gold_ingot", "minecraft:chiseled_quartz_block",
                                "minecraft:copper_block", "minecraft:nether_star", "minecraft:copper_block",
                                "minecraft:chiseled_quartz_block", "minecraft:gold_ingot", "minecraft:chiseled_quartz_block")
                        .line(T + "memorial_size", "3×3×8")
                        .line(T + "memorial_names", "20")
                        .note(T + "memorial.note"))
                .addPage(new IllustratedPage(T + "memorial_use")
                        .info("minecraft:iron_sword", T + "memorial_killed", "")
                        .info("minecraft:name_tag", T + "memorial_no_tomb", "")
                        .info("minecraft:writable_book", T + "memorial_list", "")
                        .info("minecraft:leather_boots", T + "memorial_edit", ""))
                .addPage(new IllustratedPage(T + "break")
                        .good("minecraft:iron_pickaxe", T + "break_empty", "\u2714")
                        .bad("minecraft:iron_pickaxe", T + "break_full", "\u2718")
                        .good("minecraft:enchanted_book", T + "break_silk", "\u2714")
                        .note(T + "break.note"));
    }
}
