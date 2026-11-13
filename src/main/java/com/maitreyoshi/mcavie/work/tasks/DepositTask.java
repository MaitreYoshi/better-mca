package com.maitreyoshi.mcavie.work.tasks;

import com.google.common.collect.ImmutableMap;
import com.maitreyoshi.mcavie.phone.PhoneEmployees;
import com.maitreyoshi.mcavie.work.WorkManager;
import com.maitreyoshi.mcavie.work.WorkRegistry;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import com.maitreyoshi.mcavie.work.WorkStatus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DepositTask extends Behavior<VillagerEntityMCA> {
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
        SimpleContainer inventory = villager.getInventory();
        int[] movable = movable(villager);
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (movable[i] > 0) {
                return true;
            }
        }
        return false;
    }

    // l'objet que le PNJ tenait avant de prendre son outil reste dans son sac pour lui être rendu
    private static int[] movable(VillagerEntityMCA villager) {
        SimpleContainer inventory = villager.getInventory();
        int[] movable = movable(inventory);
        ItemStack held = WorkManager.heldOriginal(villager);
        for (int i = 0; i < inventory.getContainerSize() && !held.isEmpty(); i++) {
            if (movable[i] > 0 && ItemStack.isSameItemSameTags(inventory.getItem(i), held)) {
                movable[i] = Math.max(0, movable[i] - held.getCount());
                break;
            }
        }
        return movable;
    }

    private static int[] movable(SimpleContainer inventory) {
        int[] movable = new int[inventory.getContainerSize()];
        Map<Item, Integer> kept = new HashMap<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || stack.isDamageableItem()) {
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
        return stack.is(ItemTags.SAPLINGS)
                || stack.is(Items.WHEAT_SEEDS) || stack.is(Items.BEETROOT_SEEDS)
                || stack.is(Items.CARROT) || stack.is(Items.POTATO)
                || stack.is(Items.MELON_SEEDS) || stack.is(Items.PUMPKIN_SEEDS);
    }

    public static boolean shouldDeposit(ServerLevel world, VillagerEntityMCA villager) {
        if (!needsDeposit(villager) || isUnreachable(world, villager) || isChestFull(world, villager)) {
            return false;
        }
        Container chest = AbstractJobTask.depotInventory(world, villager);
        if (chest == null) {
            return false;
        }
        if (!hasRoom(villager, chest)) {
            FULL.put(villager.getUUID(), world.getGameTime() + FULL_RECHECK_TICKS);
            return false;
        }
        return true;
    }

    public static boolean isBlocked(ServerLevel world, VillagerEntityMCA villager) {
        SimpleContainer inventory = villager.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).isEmpty()) {
                return false;
            }
        }
        if (!isChestFull(world, villager)) {
            WorkRegistry.Record record = WorkManager.findRecord(villager);
            boolean hasDepot = record != null && record.depot != null;
            Container chest = hasDepot ? AbstractJobTask.depotInventory(world, villager) : null;
            if (chest == null && hasDepot && record.depot != null) {
                return false;
            }
            if (chest != null && hasRoom(villager, chest)) {
                return false;
            }
            FULL.put(villager.getUUID(), world.getGameTime() + FULL_RECHECK_TICKS);
        }
        notifyFull(world, villager);
        return true;
    }

    private static boolean isChestFull(ServerLevel world, VillagerEntityMCA villager) {
        Long end = FULL.get(villager.getUUID());
        if (end == null) {
            return false;
        }
        if (end <= world.getGameTime()) {
            FULL.remove(villager.getUUID());
            return false;
        }
        return true;
    }

    private static void notifyFull(ServerLevel world, VillagerEntityMCA villager) {
        long day = world.getDayTime() / 24000L;
        Long last = NOTIFIED.get(villager.getUUID());
        if (last != null && last == day) {
            return;
        }
        NOTIFIED.put(villager.getUUID(), day);
        WorkRegistry.Record record = WorkManager.findRecord(villager);
        Component message = Component.translatable(record == null || record.depot == null ? "job.mca_vie.no_depot_full" : "job.mca_vie.depot_full",
                villager.getName()).withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer player : recipients(world, villager, record)) {
            player.displayClientMessage(message, false);
        }
    }

    private static List<ServerPlayer> recipients(ServerLevel world, VillagerEntityMCA villager, WorkRegistry.Record record) {
        ServerPlayer employer = record == null || record.employer == null ? null
                : world.getServer().getPlayerList().getPlayer(record.employer);
        if (employer != null) {
            return List.of(employer);
        }
        return world.getPlayers(player -> player.distanceToSqr(villager) <= NOTIFY_DISTANCE_SQ);
    }

    private static boolean isUnreachable(ServerLevel world, VillagerEntityMCA villager) {
        Long end = UNREACHABLE.get(villager.getUUID());
        if (end == null) {
            return false;
        }
        if (end <= world.getGameTime()) {
            UNREACHABLE.remove(villager.getUUID());
            return false;
        }
        return true;
    }

    private static boolean hasRoom(VillagerEntityMCA villager, Container to) {
        SimpleContainer from = villager.getInventory();
        int[] movable = movable(villager);
        for (int i = 0; i < to.getContainerSize(); i++) {
            ItemStack slot = to.getItem(i);
            for (int j = 0; j < from.getContainerSize(); j++) {
                ItemStack stack = from.getItem(j);
                if (movable[j] <= 0 || !to.canPlaceItem(i, stack)) {
                    continue;
                }
                if (slot.isEmpty() || (ItemStack.isSameItemSameTags(slot, stack) && slot.getCount() < Math.min(slot.getMaxStackSize(), to.getMaxStackSize()))) {
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
    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA villager) {
        return WorkManager.isWorking(villager) && shouldDeposit(world, villager);
    }

    @Override
    protected boolean canStillUse(ServerLevel world, VillagerEntityMCA villager, long time) {
        return WorkManager.isWorking(villager) && shouldDeposit(world, villager);
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        reach.reset();
    }

    @Override
    protected void stop(ServerLevel world, VillagerEntityMCA villager, long time) {
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    @Override
    protected void tick(ServerLevel world, VillagerEntityMCA villager, long time) {
        BlockPos depot = AbstractJobTask.depotPos(villager);
        if (depot == null) {
            return;
        }
        if (villager.distanceToSqr(Vec3.atCenterOf(depot)) <= 6.25) {
            AbstractJobTask.arrive(villager, WorkStatus.DEPOSITING);
            villager.lookAt(depot);
            reach.reset();
            Container chest = AbstractJobTask.depotInventory(world, villager);
            if (chest != null) {
                deposit(villager, chest);
                villager.swing(InteractionHand.MAIN_HAND);
            }
        } else {
            AbstractJobTask.walkTo(villager, depot);
            reach.giveUp(world, villager.getUUID());
        }
    }

    private static void deposit(VillagerEntityMCA villager, Container to) {
        SimpleContainer from = villager.getInventory();
        int[] movable = movable(villager);
        for (int i = 0; i < from.getContainerSize(); i++) {
            ItemStack stack = from.getItem(i);
            if (movable[i] <= 0) {
                continue;
            }
            int keep = stack.getCount() - movable[i];
            ItemStack rest = insert(to, stack.copyWithCount(movable[i]));
            if (movable[i] > rest.getCount()) {
                PhoneEmployees.recordDeposit(villager, stack.getItem(), movable[i] - rest.getCount());
            }
            stack.setCount(keep + rest.getCount());
        }
        from.setChanged();
        to.setChanged();
    }

    public static ItemStack insert(Container to, ItemStack stack) {
        for (int i = 0; i < to.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack slot = to.getItem(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameTags(slot, stack) && to.canPlaceItem(i, stack)) {
                int move = Math.min(stack.getCount(), Math.min(slot.getMaxStackSize(), to.getMaxStackSize()) - slot.getCount());
                if (move > 0) {
                    slot.grow(move);
                    stack.shrink(move);
                }
            }
        }
        for (int i = 0; i < to.getContainerSize() && !stack.isEmpty(); i++) {
            if (to.getItem(i).isEmpty() && to.canPlaceItem(i, stack)) {
                to.setItem(i, stack.copy());
                stack.setCount(0);
            }
        }
        return stack;
    }
}
