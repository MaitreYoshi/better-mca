package com.maitreyoshi.mcavie.work;

import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import org.jetbrains.annotations.Nullable;

// réparations du forgeron : chaque objet coûte autant de matériau qu'il en faut pour le fabriquer
public final class Repairs {
    private Repairs() {
    }

    public record Cost(Item material, int count) {
    }

    public static boolean isDamaged(ItemStack stack) {
        return !stack.isEmpty() && stack.isDamageableItem() && stack.getDamageValue() > 0;
    }

    public static boolean isHalfWorn(ItemStack stack) {
        return isDamaged(stack) && stack.getDamageValue() * 2 >= stack.getMaxDamage();
    }

    public static int count(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof ArmorItem armor) {
            if (isNetherite(stack)) {
                return 1;
            }
            return switch (armor.getEquipmentSlot()) {
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
        return stack.getItem().isValidRepairItem(stack, new ItemStack(Items.NETHERITE_INGOT));
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
    public static Cost costIn(ItemStack damaged, @Nullable Container forge) {
        if (forge == null || !isDamaged(damaged)) {
            return null;
        }
        int needed = count(damaged);
        Item fallback = fallback(damaged);
        java.util.Map<Item, Integer> available = new java.util.HashMap<>();
        for (int i = 0; i < forge.getContainerSize(); i++) {
            ItemStack stack = forge.getItem(i);
            if (stack.isEmpty() || stack.isDamageableItem() || stack.hasTag()) {
                continue;
            }
            if (stack.getItem() == fallback || damaged.getItem().isValidRepairItem(damaged, stack)) {
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

    public static void pay(Container forge, Cost cost) {
        int left = cost.count();
        for (int i = 0; i < forge.getContainerSize() && left > 0; i++) {
            ItemStack stack = forge.getItem(i);
            if (stack.is(cost.material()) && !stack.hasTag()) {
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
        forge.setChanged();
    }

    public static boolean isGear(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty() || !stack.isDamageableItem()) {
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
