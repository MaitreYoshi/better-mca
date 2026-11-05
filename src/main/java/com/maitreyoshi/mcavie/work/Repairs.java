package com.maitreyoshi.mcavie.work;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShieldItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import org.jetbrains.annotations.Nullable;

// réparations du forgeron : chaque objet coûte autant de matériau qu'il en faut pour le fabriquer
public final class Repairs {
    private Repairs() {
    }

    public record Cost(Item material, int count) {
    }

    public static boolean isDamaged(ItemStack stack) {
        return !stack.isEmpty() && stack.isDamageable() && stack.getDamage() > 0;
    }

    public static boolean isHalfWorn(ItemStack stack) {
        return isDamaged(stack) && stack.getDamage() * 2 >= stack.getMaxDamage();
    }

    public static int count(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof ArmorItem armor) {
            if (isNetherite(stack)) {
                return 1;
            }
            return switch (armor.getSlotType()) {
                case HEAD -> 5;
                case CHEST -> 8;
                case LEGS -> 7;
                default -> 4;
            };
        }
        if (isNetherite(stack)) {
            return 1;
        }
        if (item instanceof PickaxeItem || item instanceof AxeItem) {
            return 3;
        }
        if (item instanceof SwordItem || item instanceof HoeItem || item instanceof ElytraItem
                || item instanceof BowItem || item instanceof CrossbowItem || item instanceof FishingRodItem
                || item instanceof TridentItem) {
            return 2;
        }
        return 1;
    }

    private static boolean isNetherite(ItemStack stack) {
        return stack.getItem().canRepair(stack, new ItemStack(Items.NETHERITE_INGOT));
    }

    // matériau de réparation vanilla (lingot, diamant, planches…) ; à défaut une matière proche
    @Nullable
    private static Item fallback(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof BowItem || item instanceof CrossbowItem || item instanceof FishingRodItem) {
            return Items.STRING;
        }
        if (item instanceof TridentItem) {
            return Items.PRISMARINE_SHARD;
        }
        if (item == Items.SHEARS || item == Items.FLINT_AND_STEEL) {
            return Items.IRON_INGOT;
        }
        if (item instanceof ShieldItem) {
            return null;
        }
        return null;
    }

    /** Ce que coûte la réparation avec ce qu'il y a dans la forge, ou null si la forge n'a pas de quoi. */
    @Nullable
    public static Cost costIn(ItemStack damaged, @Nullable Inventory forge) {
        if (forge == null || !isDamaged(damaged)) {
            return null;
        }
        int needed = count(damaged);
        Item fallback = fallback(damaged);
        java.util.Map<Item, Integer> available = new java.util.HashMap<>();
        for (int i = 0; i < forge.size(); i++) {
            ItemStack stack = forge.getStack(i);
            if (stack.isEmpty() || stack.isDamageable() || stack.hasNbt()) {
                continue;
            }
            if (stack.getItem() == fallback || damaged.getItem().canRepair(damaged, stack)) {
                available.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        for (java.util.Map.Entry<Item, Integer> entry : available.entrySet()) {
            if (entry.getValue() >= needed) {
                return new Cost(entry.getKey(), needed);
            }
        }
        return null;
    }

    public static void pay(Inventory forge, Cost cost) {
        int left = cost.count();
        for (int i = 0; i < forge.size() && left > 0; i++) {
            ItemStack stack = forge.getStack(i);
            if (stack.isOf(cost.material()) && !stack.hasNbt()) {
                int take = Math.min(left, stack.getCount());
                stack.decrement(take);
                left -= take;
            }
        }
        forge.markDirty();
    }

    public static boolean isGear(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty() || !stack.isDamageable()) {
            return false;
        }
        if (slot.getType() == EquipmentSlot.Type.ARMOR) {
            return true;
        }
        Item item = stack.getItem();
        return item instanceof SwordItem || item instanceof AxeItem || item instanceof BowItem || item instanceof CrossbowItem
                || item instanceof TridentItem || item instanceof ShieldItem;
    }
}
