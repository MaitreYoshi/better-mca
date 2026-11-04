package com.maitreyoshi.mcavie.work;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

// ordinal sauvegardé : ne jamais réordonner, seulement ajouter à la fin
public enum Job {
    NONE("none", Items.BARRIER, Items.AIR),
    FARMER("farmer", Items.WHEAT, Items.STONE_HOE),
    LUMBERJACK("lumberjack", Items.OAK_LOG, Items.STONE_AXE),
    FISHER("fisher", Items.COD, Items.FISHING_ROD),
    BREEDER("breeder", Items.WHITE_WOOL, Items.SHEARS),
    DOCTOR("doctor", Items.GOLDEN_APPLE, Items.GLASS_BOTTLE),
    GUARD("guard", Items.IRON_SWORD, Items.AIR),
    ARCHER("archer", Items.BOW, Items.AIR),
    BLACKSMITH("blacksmith", Items.ANVIL, Items.IRON_PICKAXE),
    BAKER("baker", Items.BREAD, Items.WHEAT),
    TEACHER("teacher", Items.BOOK, Items.BOOK),
    RETIRED("retired", Items.CLOCK, Items.AIR),
    MINER("miner", Items.IRON_PICKAXE, Items.IRON_PICKAXE),
    COOK("cook", Items.MUSHROOM_STEW, Items.BOWL);

    private static final Job[] VALUES = values();

    private final String id;
    private final Item icon;
    private final Item tool;

    Job(String id, Item icon, Item tool) {
        this.id = id;
        this.icon = icon;
        this.tool = tool;
    }

    public String id() {
        return id;
    }

    public ItemStack icon() {
        return new ItemStack(icon);
    }

    public ItemStack tool() {
        return new ItemStack(tool);
    }

    public Text getName() {
        return Text.translatable("job.mca_vie." + id);
    }

    public boolean isSelectable() {
        return this != RETIRED;
    }

    public boolean isMcaCombat() {
        return this == GUARD || this == ARCHER;
    }

    public boolean isCraft() {
        return this != NONE && this != RETIRED && !isMcaCombat();
    }

    public static Job byId(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : NONE;
    }
}
