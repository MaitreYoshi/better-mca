package com.maitreyoshi.mcavie.work.tasks;

import com.google.common.collect.ImmutableMap;
import com.maitreyoshi.mcavie.work.WorkManager;
import com.maitreyoshi.mcavie.work.WorkRegistry;
import net.mca.entity.VillagerEntityMCA;
import com.maitreyoshi.mcavie.work.WorkStatus;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.MultiTickTask;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DepositTask extends MultiTickTask<VillagerEntityMCA> {
    private static final int KEEP_FOR_PLANTING = 16;
    private static final int REACH_TICKS = 300;
    private static final long BLACKLIST_TICKS = 2400;
    private static final long FULL_RECHECK_TICKS = 200;
    private static final double NOTIFY_DISTANCE_SQ = 48 * 48;
    private static final Map<UUID, Long> UNREACHABLE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> FULL = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> NOTIFIED = new ConcurrentHashMap<>();

    private final AbstractJobTask.Reach<UUID> reach = new AbstractJobTask.Reach<>(REACH_TICKS, BLACKLIST_TICKS, UNREACHABLE);

    public DepositTask() {
        super(ImmutableMap.of(), 600);
    }

    public static boolean needsDeposit(VillagerEntityMCA villager) {
        WorkRegistry.Record record = WorkManager.findRecord(villager);
        if (record == null || record.depot == null) {
            return false;
        }
        // tout ce qui est récolté part au bloc métier aussitôt, sans attendre d'en avoir beaucoup
        SimpleInventory inventory = villager.getInventory();
        int[] movable = movable(villager);
        for (int i = 0; i < inventory.size(); i++) {
            if (movable[i] > 0) {
                return true;
            }
        }
        return false;
    }

    // l'objet que le PNJ tenait avant de prendre son outil reste dans son sac pour lui être rendu
    private static int[] movable(VillagerEntityMCA villager) {
        SimpleInventory inventory = villager.getInventory();
        int[] movable = movable(inventory);
        ItemStack held = WorkManager.heldOriginal(villager);
        for (int i = 0; i < inventory.size() && !held.isEmpty(); i++) {
            if (movable[i] > 0 && ItemStack.canCombine(inventory.getStack(i), held)) {
                movable[i] = Math.max(0, movable[i] - held.getCount());
                break;
            }
        }
        return movable;
    }

    private static int[] movable(SimpleInventory inventory) {
        int[] movable = new int[inventory.size()];
        Map<Item, Integer> kept = new HashMap<>();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty() || stack.isDamageable()) {
                continue;
            }
            int keep = 0;
            if (isPlantable(stack)) {
                int already = kept.getOrDefault(stack.getItem(), 0);
                keep = Math.min(stack.getCount(), KEEP_FOR_PLANTING - already);
                kept.put(stack.getItem(), already + keep);
            }
            movable[i] = stack.getCount() - keep;
        }
        return movable;
    }

    private static boolean isPlantable(ItemStack stack) {
        return stack.isIn(ItemTags.SAPLINGS)
                || stack.isOf(Items.WHEAT_SEEDS) || stack.isOf(Items.BEETROOT_SEEDS)
                || stack.isOf(Items.CARROT) || stack.isOf(Items.POTATO)
                || stack.isOf(Items.MELON_SEEDS) || stack.isOf(Items.PUMPKIN_SEEDS);
    }

    public static boolean shouldDeposit(ServerWorld world, VillagerEntityMCA villager) {
        if (!needsDeposit(villager) || isUnreachable(world, villager) || isChestFull(world, villager)) {
            return false;
        }
        Inventory chest = AbstractJobTask.depotInventory(world, villager);
        if (chest == null) {
            return false;
        }
        if (!hasRoom(villager, chest)) {
            FULL.put(villager.getUuid(), world.getTime() + FULL_RECHECK_TICKS);
            return false;
        }
        return true;
    }

    public static boolean isBlocked(ServerWorld world, VillagerEntityMCA villager) {
        SimpleInventory inventory = villager.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getStack(i).isEmpty()) {
                return false;
            }
        }
        if (!isChestFull(world, villager)) {
            WorkRegistry.Record record = WorkManager.findRecord(villager);
            boolean hasDepot = record != null && record.depot != null;
            Inventory chest = hasDepot ? AbstractJobTask.depotInventory(world, villager) : null;
            if (chest == null && hasDepot && record.depot != null) {
                return false;
            }
            if (chest != null && hasRoom(villager, chest)) {
                return false;
            }
            FULL.put(villager.getUuid(), world.getTime() + FULL_RECHECK_TICKS);
        }
        notifyFull(world, villager);
        return true;
    }

    private static boolean isChestFull(ServerWorld world, VillagerEntityMCA villager) {
        Long end = FULL.get(villager.getUuid());
        if (end == null) {
            return false;
        }
        if (end <= world.getTime()) {
            FULL.remove(villager.getUuid());
            return false;
        }
        return true;
    }

    private static void notifyFull(ServerWorld world, VillagerEntityMCA villager) {
        long day = world.getTimeOfDay() / 24000L;
        Long last = NOTIFIED.get(villager.getUuid());
        if (last != null && last == day) {
            return;
        }
        NOTIFIED.put(villager.getUuid(), day);
        WorkRegistry.Record record = WorkManager.findRecord(villager);
        Text message = Text.translatable(record == null || record.depot == null ? "job.mca_vie.no_depot_full" : "job.mca_vie.depot_full",
                villager.getName()).formatted(Formatting.YELLOW);
        for (ServerPlayerEntity player : recipients(world, villager, record)) {
            player.sendMessage(message, false);
        }
    }

    private static List<ServerPlayerEntity> recipients(ServerWorld world, VillagerEntityMCA villager, WorkRegistry.Record record) {
        ServerPlayerEntity employer = record == null || record.employer == null ? null
                : world.getServer().getPlayerManager().getPlayer(record.employer);
        if (employer != null) {
            return List.of(employer);
        }
        return world.getPlayers(player -> player.squaredDistanceTo(villager) <= NOTIFY_DISTANCE_SQ);
    }

    private static boolean isUnreachable(ServerWorld world, VillagerEntityMCA villager) {
        Long end = UNREACHABLE.get(villager.getUuid());
        if (end == null) {
            return false;
        }
        if (end <= world.getTime()) {
            UNREACHABLE.remove(villager.getUuid());
            return false;
        }
        return true;
    }

    private static boolean hasRoom(VillagerEntityMCA villager, Inventory to) {
        SimpleInventory from = villager.getInventory();
        int[] movable = movable(villager);
        for (int i = 0; i < to.size(); i++) {
            ItemStack slot = to.getStack(i);
            for (int j = 0; j < from.size(); j++) {
                ItemStack stack = from.getStack(j);
                if (movable[j] <= 0 || !to.isValid(i, stack)) {
                    continue;
                }
                if (slot.isEmpty() || (ItemStack.canCombine(slot, stack) && slot.getCount() < Math.min(slot.getMaxCount(), to.getMaxCountPerStack()))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void forget(UUID villager) {
        UNREACHABLE.remove(villager);
        FULL.remove(villager);
        NOTIFIED.remove(villager);
    }

    public static void clear() {
        UNREACHABLE.clear();
        FULL.clear();
        NOTIFIED.clear();
    }

    @Override
    protected boolean shouldRun(ServerWorld world, VillagerEntityMCA villager) {
        return WorkManager.isWorking(villager) && shouldDeposit(world, villager);
    }

    @Override
    protected boolean shouldKeepRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        return WorkManager.isWorking(villager) && shouldDeposit(world, villager);
    }

    @Override
    protected void run(ServerWorld world, VillagerEntityMCA villager, long time) {
        reach.reset();
    }

    @Override
    protected void finishRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        villager.getBrain().forget(MemoryModuleType.WALK_TARGET);
        villager.getBrain().forget(MemoryModuleType.LOOK_TARGET);
    }

    @Override
    protected void keepRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        BlockPos depot = AbstractJobTask.depotPos(villager);
        if (depot == null) {
            return;
        }
        if (villager.squaredDistanceTo(Vec3d.ofCenter(depot)) <= 6.25) {
            AbstractJobTask.arrive(villager, WorkStatus.DEPOSITING);
            villager.lookAt(depot);
            reach.reset();
            Inventory chest = AbstractJobTask.depotInventory(world, villager);
            if (chest != null) {
                deposit(villager, chest);
                villager.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            }
        } else {
            AbstractJobTask.walkTo(villager, depot);
            reach.giveUp(world, villager.getUuid());
        }
    }

    private static void deposit(VillagerEntityMCA villager, Inventory to) {
        SimpleInventory from = villager.getInventory();
        int[] movable = movable(villager);
        for (int i = 0; i < from.size(); i++) {
            ItemStack stack = from.getStack(i);
            if (movable[i] <= 0) {
                continue;
            }
            int keep = stack.getCount() - movable[i];
            ItemStack rest = insert(to, stack.copyWithCount(movable[i]));
            stack.setCount(keep + rest.getCount());
        }
        from.markDirty();
        to.markDirty();
    }

    public static ItemStack insert(Inventory to, ItemStack stack) {
        for (int i = 0; i < to.size() && !stack.isEmpty(); i++) {
            ItemStack slot = to.getStack(i);
            if (!slot.isEmpty() && ItemStack.canCombine(slot, stack) && to.isValid(i, stack)) {
                int move = Math.min(stack.getCount(), Math.min(slot.getMaxCount(), to.getMaxCountPerStack()) - slot.getCount());
                if (move > 0) {
                    slot.increment(move);
                    stack.decrement(move);
                }
            }
        }
        for (int i = 0; i < to.size() && !stack.isEmpty(); i++) {
            if (to.getStack(i).isEmpty() && to.isValid(i, stack)) {
                to.setStack(i, stack.copy());
                stack.setCount(0);
            }
        }
        return stack;
    }
}
