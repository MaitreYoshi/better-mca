package com.maitreyoshi.mcavie.work.tasks;

import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkGesture;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.recipe.RecipeType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// cuisine à la marmite : soupes et ragoûts d'abord, puis tout ce qui se cuit au fumoir, avec les ingrédients rangés dans la marmite
public class CookTask extends AbstractJobTask {
    private static final int SCAN_INTERVAL = 200;
    private static final int COOK_TICKS = 80;
    private static final int MAX_DISHES = 8;
    private static final double REACH_SQ = 6.25;
    private static final int REACH_TICKS = 200;
    private static final long BLACKLIST_TICKS = 2400;

    private record Dish(List<ItemStack> inputs, ItemStack output) {
    }

    private static final List<Dish> SOUPS = List.of(
            new Dish(List.of(new ItemStack(Items.BEETROOT, 6), new ItemStack(Items.BOWL)), new ItemStack(Items.BEETROOT_SOUP)),
            new Dish(List.of(new ItemStack(Items.RED_MUSHROOM), new ItemStack(Items.BROWN_MUSHROOM), new ItemStack(Items.BOWL)), new ItemStack(Items.MUSHROOM_STEW)),
            new Dish(List.of(new ItemStack(Items.COOKED_RABBIT), new ItemStack(Items.CARROT), new ItemStack(Items.BAKED_POTATO),
                    new ItemStack(Items.BROWN_MUSHROOM), new ItemStack(Items.BOWL)), new ItemStack(Items.RABBIT_STEW)));

    private long nextScan;
    private boolean lastScan;
    private int cookTicks;
    private int dishes;
    @Nullable
    private Dish cooking;
    private final Reach<BlockPos> reach = new Reach<>(REACH_TICKS, BLACKLIST_TICKS);

    public CookTask() {
        super(Job.COOK);
    }

    @Override
    protected boolean canWork(ServerWorld world, VillagerEntityMCA villager) {
        long now = world.getTime();
        if (now < nextScan) {
            return lastScan;
        }
        nextScan = now + SCAN_INTERVAL;
        lastScan = pot(world, villager) != null;
        return lastScan;
    }

    @Override
    protected boolean keepWorking(ServerWorld world, VillagerEntityMCA villager) {
        if (dishes >= MAX_DISHES) {
            return false;
        }
        return cooking != null || pot(world, villager) != null;
    }

    // la marmite liée, seulement si elle contient de quoi cuisiner
    @Nullable
    private Inventory pot(ServerWorld world, VillagerEntityMCA villager) {
        BlockPos depot = depotPos(villager);
        Inventory pot = depot == null || reach.isBlacklisted(world, depot) ? null : depotInventory(world, villager);
        return pot != null && findDish(world, pot) != null ? pot : null;
    }

    @Override
    protected void run(ServerWorld world, VillagerEntityMCA villager, long time) {
        cookTicks = 0;
        dishes = 0;
        cooking = null;
        reach.reset();
        holdTool(villager);
    }

    @Override
    protected void keepRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        BlockPos depot = depotPos(villager);
        if (depot == null) {
            return;
        }
        if (villager.squaredDistanceTo(Vec3d.ofCenter(depot)) > REACH_SQ) {
            walkTo(villager, depot);
            if (reach.giveUp(world, depot)) {
                lastScan = false;
            }
            return;
        }
        reach.reset();
        arrive(villager, WorkGesture.STIR);
        villager.lookAt(depot);
        holdTool(villager);
        if (cooking == null) {
            Inventory pot = depotInventory(world, villager);
            Dish dish = pot == null ? null : findDish(world, pot);
            if (dish == null || !take(pot, dish.inputs())) {
                return;
            }
            cooking = dish;
            cookTicks = 0;
        }
        cookTicks++;
        if (cookTicks % 10 == 1) {
            villager.swingHand(Hand.MAIN_HAND);
            world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, depot.getX() + 0.5, depot.getY() + 1.0, depot.getZ() + 0.5, 1, 0.1, 0.05, 0.1, 0.01);
            world.spawnParticles(ParticleTypes.BUBBLE_POP, depot.getX() + 0.5, depot.getY() + 0.9, depot.getZ() + 0.5, 3, 0.2, 0.02, 0.2, 0.01);
        }
        if (cookTicks % 30 == 1) {
            world.playSound(null, depot, SoundEvents.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, SoundCategory.BLOCKS, 0.6F, 0.8F);
        }
        if (cookTicks < COOK_TICKS) {
            return;
        }
        store(villager, cooking.output().copy());
        gainExperience(villager, 1);
        dishes++;
        cooking = null;
        cookTicks = 0;
    }

    @Override
    protected void finishRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        super.finishRunning(world, villager, time);
        if (cooking != null) {
            Inventory pot = depotInventory(world, villager);
            for (ItemStack input : cooking.inputs()) {
                ItemStack back = input.copy();
                if (pot != null) {
                    back = DepositTask.insert(pot, back);
                    pot.markDirty();
                }
                if (!back.isEmpty()) {
                    store(villager, back, false);
                }
            }
        }
        cooking = null;
        cookTicks = 0;
        dishes = 0;
        nextScan = 0;
    }

    @Nullable
    private static Dish findDish(ServerWorld world, Inventory pot) {
        for (Dish soup : SOUPS) {
            if (has(pot, soup.inputs())) {
                return soup;
            }
        }
        for (int i = 0; i < pot.size(); i++) {
            ItemStack stack = pot.getStack(i);
            if (stack.isEmpty()) {
                continue;
            }
            SimpleInventory single = new SimpleInventory(stack.copyWithCount(1));
            ItemStack result = world.getRecipeManager().getFirstMatch(RecipeType.SMOKING, single, world)
                    .map(recipe -> recipe.getOutput(world.getRegistryManager()).copy())
                    .orElse(ItemStack.EMPTY);
            if (!result.isEmpty()) {
                return new Dish(List.of(stack.copyWithCount(1)), result);
            }
        }
        return null;
    }

    private static int count(Inventory pot, Item item) {
        int total = 0;
        for (int i = 0; i < pot.size(); i++) {
            if (pot.getStack(i).isOf(item)) {
                total += pot.getStack(i).getCount();
            }
        }
        return total;
    }

    private static boolean has(Inventory pot, List<ItemStack> inputs) {
        for (ItemStack input : inputs) {
            if (count(pot, input.getItem()) < input.getCount()) {
                return false;
            }
        }
        return true;
    }

    private static boolean take(Inventory pot, List<ItemStack> inputs) {
        if (!has(pot, inputs)) {
            return false;
        }
        for (ItemStack input : inputs) {
            int left = input.getCount();
            for (int i = 0; i < pot.size() && left > 0; i++) {
                ItemStack stack = pot.getStack(i);
                if (stack.isOf(input.getItem())) {
                    int take = Math.min(left, stack.getCount());
                    stack.decrement(take);
                    left -= take;
                }
            }
        }
        pot.markDirty();
        return true;
    }
}
