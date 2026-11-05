package com.maitreyoshi.mcavie.book;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.life.Wellbeing;
import com.maitreyoshi.mcavie.social.Social;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.mca.client.book.Book;
import net.mca.client.book.pages.TextPage;
import net.mca.client.book.pages.TitlePage;
import net.mca.item.ExtendedWrittenBookItem;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/** Livre illustré du bonheur, donné une fois dans le 2e emplacement de la barre rapide, en même temps que le livre de MCA (succès mca:root). */
public final class HappinessBook {
    private static final String TAG = "mca_vie.happiness_book_given";
    private static final String P = "mca_vie.books.happiness.";
    private static final int HOTBAR_SLOT = 1;
    private static final Identifier MCA_ROOT = new Identifier("mca", "root");
    private static final Set<UUID> PENDING = new HashSet<>();

    public static Item HAPPINESS_BOOK;

    private HappinessBook() {
    }

    public static void register() {
        HAPPINESS_BOOK = Registry.register(Registries.ITEM, MCAVie.id("happiness_book"),
                new ExtendedWrittenBookItem(new Item.Settings().maxCount(1), createBook()));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!handler.player.getCommandTags().contains(TAG)) {
                PENDING.add(handler.player.getUuid());
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PENDING.remove(handler.player.getUuid()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (PENDING.isEmpty()) {
                return;
            }
            Advancement mcaRoot = server.getAdvancementLoader().get(MCA_ROOT);
            Iterator<UUID> it = PENDING.iterator();
            while (it.hasNext()) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(it.next());
                if (player == null) {
                    it.remove();
                } else if (player.isAlive() && (mcaRoot == null || player.getAdvancementTracker().getProgress(mcaRoot).isDone())) {
                    it.remove();
                    giveOnce(player);
                }
            }
        });
    }

    private static Book createBook() {
        return new Book("happiness", Text.translatable(P + "author").formatted(Formatting.GRAY))
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

    private static void giveOnce(ServerPlayerEntity player) {
        if (!player.addCommandTag(TAG)) {
            return;
        }
        ItemStack book = new ItemStack(HAPPINESS_BOOK);
        PlayerInventory inventory = player.getInventory();
        if (inventory.getStack(HOTBAR_SLOT).isEmpty()) {
            inventory.setStack(HOTBAR_SLOT, book);
        } else if (!inventory.insertStack(book)) {
            player.dropItem(book, false);
        }
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS,
                0.2F, ((player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F);
        player.currentScreenHandler.sendContentUpdates();
        Advancement advancement = player.server.getAdvancementLoader().get(MCAVie.id("happiness_book"));
        if (advancement != null) {
            AdvancementProgress progress = player.getAdvancementTracker().getProgress(advancement);
            for (String criterion : progress.getUnobtainedCriteria()) {
                player.getAdvancementTracker().grantCriterion(advancement, criterion);
            }
        }
    }
}
