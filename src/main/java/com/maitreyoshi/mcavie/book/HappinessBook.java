package com.maitreyoshi.mcavie.book;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.life.Wellbeing;
import com.maitreyoshi.mcavie.social.Social;
import com.maitreyoshi.mcavie.compat.ServerTickEvents;
import com.maitreyoshi.mcavie.compat.ServerPlayConnectionEvents;
import net.mca.client.book.Book;
import net.mca.client.book.pages.TextPage;
import net.mca.client.book.pages.TitlePage;
import net.mca.item.ExtendedWrittenBookItem;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/** Livre illustré du bonheur, donné une fois dans le 2e emplacement de la barre rapide, en même temps que le livre de MCA (succès mca:root). */
public final class HappinessBook {
    private static final String TAG = "mca_vie.happiness_book_given";
    private static final String P = "mca_vie.books.happiness.";
    private static final int HOTBAR_SLOT = 1;
    private static final ResourceLocation MCA_ROOT = new ResourceLocation("mca", "root");
    private static final Set<UUID> PENDING = new HashSet<>();

    public static Item HAPPINESS_BOOK;

    private HappinessBook() {
    }

    public static void register() {
        com.maitreyoshi.mcavie.compat.Registration.register(net.minecraft.core.registries.Registries.ITEM, "happiness_book",
                () -> new ExtendedWrittenBookItem(new Item.Properties().stacksTo(1), createBook()), item -> HAPPINESS_BOOK = item);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!handler.player.getTags().contains(TAG)) {
                PENDING.add(handler.player.getUUID());
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PENDING.remove(handler.player.getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (PENDING.isEmpty()) {
                return;
            }
            Advancement mcaRoot = server.getAdvancements().getAdvancement(MCA_ROOT);
            Iterator<UUID> it = PENDING.iterator();
            while (it.hasNext()) {
                ServerPlayer player = server.getPlayerList().getPlayer(it.next());
                if (player == null) {
                    it.remove();
                } else if (player.isAlive() && (mcaRoot == null || player.getAdvancements().getOrStartProgress(mcaRoot).isDone())) {
                    it.remove();
                    giveOnce(player);
                }
            }
        });
    }

    private static Book createBook() {
        return new Book("happiness", Component.translatable(P + "author").withStyle(ChatFormatting.GRAY))
                .setBackground(MCAVie.id("textures/gui/books/happiness.png"))
                .addPage(new TitlePage("item.mca_vie.happiness_book", P + "author"))
                .addPage(new TextPage("{\"translate\":\"" + P + "intro\"}"))
                .addPage(new IllustratedPage(P + "joys")
                        .gain("mca:wedding_ring", P + "marriage", (int) Wellbeing.MARRIAGE)
                        .gain("mca:baby_boy", P + "child", (int) Wellbeing.NEW_CHILD)
                        .gain("minecraft:poppy", P + "gift", (int) Wellbeing.LIKED_GIFT)
                        .gain("minecraft:red_bed", P + "sleep", (int) Wellbeing.SLEPT)
                        .gain("minecraft:book", P + "lesson", 2))
                .addPage(new IllustratedPage(P + "daily_good")
                        .gain("minecraft:oak_door", P + "home", (int) Wellbeing.HOME_DAY)
                        .gain("minecraft:iron_pickaxe", P + "job", (int) Wellbeing.EMPLOYED_DAY)
                        .gain("minecraft:bell", P + "company", (int) Social.FRIEND_NEAR_DAY)
                        .note(P + "daily_good.note"))
                .addPage(new IllustratedPage(P + "village")
                        .gain("minecraft:cake", P + "feast", 10)
                        .gain("minecraft:firework_rocket", P + "festival", 20)
                        .gain("minecraft:emerald", P + "market", 3)
                        .note(P + "village.note"))
                .addPage(new IllustratedPage(P + "blows")
                        .loss("minecraft:iron_sword", P + "hit_player", (int) Wellbeing.HIT_BY_PLAYER)
                        .loss("minecraft:arrow", P + "hit", (int) Wellbeing.HIT)
                        .loss("mca:divorce_papers", P + "divorce", (int) Wellbeing.DIVORCE)
                        .loss("minecraft:iron_bars", P + "prison", 10))
                .addPage(new IllustratedPage(P + "grief")
                        .loss("mca_vie:marble_tomb", P + "lost_child", (int) Wellbeing.LOST_CHILD)
                        .loss("mca_vie:stele_tomb", P + "lost_spouse", (int) Wellbeing.LOST_SPOUSE)
                        .loss("mca_vie:wooden_cross_tomb", P + "lost_parent", (int) Wellbeing.LOST_PARENT)
                        .loss("minecraft:wither_rose", P + "lost_friend", (int) Social.LOST_FRIEND))
                .addPage(new IllustratedPage(P + "daily_bad")
                        .loss("minecraft:clock", P + "no_sleep", (int) Wellbeing.NO_SLEEP_DAY)
                        .loss("minecraft:compass", P + "isolated", (int) Wellbeing.ISOLATED_DAY)
                        .loss("minecraft:dead_bush", P + "homeless", (int) Wellbeing.HOMELESS_DAY)
                        .loss("minecraft:stick", P + "unemployed", (int) Wellbeing.UNEMPLOYED_DAY)
                        .loss("minecraft:skeleton_skull", P + "enemy", (int) Social.ENEMY_AT_HOME_DAY))
                .addPage(new IllustratedPage(P + "gifts")
                        .grid("minecraft:poppy", "minecraft:cake", "minecraft:cookie", "minecraft:cooked_beef", "minecraft:baked_potato",
                                "minecraft:diamond", "minecraft:emerald", "minecraft:gold_ingot", "minecraft:writable_book", "minecraft:clock",
                                "minecraft:bell", "minecraft:painting")
                        .note(P + "gifts.note"))
                .addPage(new IllustratedPage(P + "base")
                        .intro(P + "base.intro")
                        .gain("minecraft:candle", P + "chapel", 2)
                        .gain("minecraft:note_block", P + "theater", 2)
                        .gain("minecraft:beacon", P + "capital", 5))
                .addPage(new IllustratedPage(P + "taxes")
                        .intro(P + "taxes.intro")
                        .line(P + "tax_0", "55")
                        .line(P + "tax_10", "50")
                        .line(P + "tax_15", "47")
                        .line(P + "tax_20", "44")
                        .line(P + "tax_25", "40")
                        .line(P + "tax_more", "35"))
                .addPage(new IllustratedPage(P + "cure")
                        .info("minecraft:golden_apple", P + "cure_apple", "\u2714")
                        .info("minecraft:potion@minecraft:healing", P + "cure_potion", "\u2714")
                        .note(P + "cure.note"));
    }

    private static void giveOnce(ServerPlayer player) {
        if (!player.addTag(TAG)) {
            return;
        }
        ItemStack book = new ItemStack(HAPPINESS_BOOK);
        Inventory inventory = player.getInventory();
        if (inventory.getItem(HOTBAR_SLOT).isEmpty()) {
            inventory.setItem(HOTBAR_SLOT, book);
        } else if (!inventory.add(book)) {
            player.drop(book, false);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
                0.2F, ((player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F);
        player.containerMenu.broadcastChanges();
        Advancement advancement = player.server.getAdvancements().getAdvancement(MCAVie.id("happiness_book"));
        if (advancement != null) {
            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
            for (String criterion : progress.getRemainingCriteria()) {
                player.getAdvancements().award(advancement, criterion);
            }
        }
    }
}
