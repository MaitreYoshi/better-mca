package com.maitreyoshi.mcavie.work.tasks;

import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkGesture;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
    protected boolean canWork(ServerLevel world, VillagerEntityMCA villager) {
        long now = world.getGameTime();
        if (now < nextScan) {
            return lastScan;
        }
        nextScan = now + SCAN_INTERVAL;
        lastScan = pot(world, villager) != null;
        return lastScan;
    }

    @Override
    protected boolean keepWorking(ServerLevel world, VillagerEntityMCA villager) {
        if (dishes >= MAX_DISHES) {
            return false;
        }
        return cooking != null || pot(world, villager) != null;
    }

    @Nullable
    private Container pot(ServerLevel world, VillagerEntityMCA villager) {
        BlockPos depot = depotPos(villager);
        Container pot = depot == null || reach.isBlacklisted(world, depot) ? null : depotInventory(world, villager);
        return pot != null && findDish(world, pot) != null ? pot : null;
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        cookTicks = 0;
        dishes = 0;
        cooking = null;
        reach.reset();
        holdTool(villager);
    }

    @Override
    protected void tick(ServerLevel world, VillagerEntityMCA villager, long time) {
        BlockPos depot = depotPos(villager);
        if (depot == null) {
            return;
        }
        if (villager.distanceToSqr(Vec3.atCenterOf(depot)) > REACH_SQ) {
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
            Container pot = depotInventory(world, villager);
            Dish dish = pot == null ? null : findDish(world, pot);
            if (dish == null || !take(pot, dish.inputs())) {
                return;
            }
            cooking = dish;
            cookTicks = 0;
        }
        cookTicks++;
        if (cookTicks % 10 == 1) {
            villager.swing(InteractionHand.MAIN_HAND);
            world.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, depot.getX() + 0.5, depot.getY() + 1.0, depot.getZ() + 0.5, 1, 0.1, 0.05, 0.1, 0.01);
            world.sendParticles(ParticleTypes.BUBBLE_POP, depot.getX() + 0.5, depot.getY() + 0.9, depot.getZ() + 0.5, 3, 0.2, 0.02, 0.2, 0.01);
        }
        if (cookTicks % 30 == 1) {
            world.playSound(null, depot, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS, 0.6F, 0.8F);
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
    protected void stop(ServerLevel world, VillagerEntityMCA villager, long time) {
        super.stop(world, villager, time);
        if (cooking != null) {
            Container pot = depotInventory(world, villager);
            for (ItemStack input : cooking.inputs()) {
                ItemStack back = input.copy();
                if (pot != null) {
                    back = DepositTask.insert(pot, back);
                    pot.setChanged();
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
    private static Dish findDish(ServerLevel world, Container pot) {
        for (Dish soup : SOUPS) {
            if (has(pot, soup.inputs())) {
                return soup;
            }
        }
        for (int i = 0; i < pot.getContainerSize(); i++) {
            ItemStack stack = pot.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            SimpleContainer single = new SimpleContainer(stack.copyWithCount(1));
            ItemStack result = world.getRecipeManager().getRecipeFor(RecipeType.SMOKING, single, world)
                    .map(recipe -> recipe.getResultItem(world.registryAccess()).copy())
                    .orElse(ItemStack.EMPTY);
            if (!result.isEmpty()) {
                return new Dish(List.of(stack.copyWithCount(1)), result);
            }
        }
        return null;
    }

    private static int count(Container pot, Item item) {
        int total = 0;
        for (int i = 0; i < pot.getContainerSize(); i++) {
            if (pot.getItem(i).is(item)) {
                total += pot.getItem(i).getCount();
            }
        }
        return total;
    }

    private static boolean has(Container pot, List<ItemStack> inputs) {
        for (ItemStack input : inputs) {
            if (count(pot, input.getItem()) < input.getCount()) {
                return false;
            }
        }
        return true;
    }

    private static boolean take(Container pot, List<ItemStack> inputs) {
        if (!has(pot, inputs)) {
            return false;
        }
        for (ItemStack input : inputs) {
            int left = input.getCount();
            for (int i = 0; i < pot.getContainerSize() && left > 0; i++) {
                ItemStack stack = pot.getItem(i);
                if (stack.is(input.getItem())) {
                    int take = Math.min(left, stack.getCount());
                    stack.shrink(take);
                    left -= take;
                }
            }
        }
        pot.setChanged();
        return true;
    }
}
