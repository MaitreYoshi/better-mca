package com.maitreyoshi.mcavie.work;

import com.maitreyoshi.mcavie.work.tasks.DepositTask;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// les villageois équipés portent leurs armes et armures à moitié usées à la forge, puis reviennent les chercher réparées
public final class RepairErrands {
    private static final String TAG = "mca_vie_repair";
    private static final double SEARCH_SQ = 64 * 64;
    private static final double REACH_SQ = 6.25;
    private static final int CHECK_EVERY = 200;
    private static final long GIVE_UP_TICKS = 1200;
    private static final long COOLDOWN_TICKS = 2400;

    private enum Mode {DROP, FETCH}

    private record Errand(BlockPos forge, Mode mode, long until) {
    }

    private static final Map<UUID, Errand> ERRANDS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> COOLDOWN = new ConcurrentHashMap<>();

    private RepairErrands() {
    }

    /** Appelé toutes les 20 ticks pour chaque villageois MCA côté serveur. */
    public static void update(VillagerEntityMCA villager) {
        if (!(villager.getWorld() instanceof ServerWorld world)) {
            return;
        }
        UUID id = villager.getUuid();
        if (villager.isBaby() || villager.isSleeping() || villager.hasVehicle() || WorkManager.isWorking(villager)
                || WorkManager.getJob(villager) == Job.BLACKSMITH
                || villager.getBrain().hasMemoryModule(MemoryModuleType.ATTACK_TARGET)) {
            ERRANDS.remove(id);
            return;
        }
        long now = world.getTime();
        Errand errand = ERRANDS.get(id);
        if (errand == null) {
            Long cooldown = COOLDOWN.get(id);
            if ((cooldown != null && now < cooldown) || Math.floorMod(now + villager.getId(), CHECK_EVERY) >= 20) {
                return;
            }
            errand = plan(world, villager, now);
            if (errand == null) {
                return;
            }
            ERRANDS.put(id, errand);
        }
        Inventory forge = forgeAt(world, errand.forge());
        if (forge == null || now > errand.until()) {
            ERRANDS.remove(id);
            COOLDOWN.put(id, now + COOLDOWN_TICKS);
            return;
        }
        if (villager.squaredDistanceTo(Vec3d.ofCenter(errand.forge())) > REACH_SQ) {
            villager.moveTowards(errand.forge(), 0.6f, 1);
            return;
        }
        villager.lookAt(errand.forge());
        villager.swingHand(Hand.MAIN_HAND);
        if (errand.mode() == Mode.DROP) {
            drop(villager, forge);
        } else {
            fetch(villager, forge);
        }
        ERRANDS.remove(id);
        villager.getBrain().forget(MemoryModuleType.WALK_TARGET);
    }

    public static void forget(UUID villager) {
        ERRANDS.remove(villager);
        COOLDOWN.remove(villager);
    }

    @Nullable
    private static Errand plan(ServerWorld world, VillagerEntityMCA villager, long now) {
        boolean worn = false;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = villager.getEquippedStack(slot);
            if (isWornGear(stack, slot)) {
                worn = true;
                break;
            }
        }
        BlockPos best = null;
        Mode mode = null;
        double bestDistance = SEARCH_SQ;
        Identifier dimension = world.getRegistryKey().getValue();
        for (WorkRegistry.Record record : WorkRegistry.get(world.getServer()).all()) {
            if (record.job != Job.BLACKSMITH || record.depot == null
                    || (record.depotDimension != null && !record.depotDimension.equals(dimension))) {
                continue;
            }
            double distance = villager.squaredDistanceTo(Vec3d.ofCenter(record.depot));
            if (distance >= bestDistance) {
                continue;
            }
            Inventory forge = forgeAt(world, record.depot);
            if (forge == null) {
                continue;
            }
            Mode here = hasRepaired(forge, villager.getUuid()) ? Mode.FETCH : worn && hasRoom(forge) ? Mode.DROP : null;
            if (here != null) {
                best = record.depot;
                mode = here;
                bestDistance = distance;
            }
        }
        return best == null ? null : new Errand(best, mode, now + GIVE_UP_TICKS);
    }

    private static boolean isWornGear(ItemStack stack, EquipmentSlot slot) {
        return Repairs.isGear(stack, slot) && !WorkManager.isWorkTool(stack) && Repairs.isHalfWorn(stack);
    }

    private static void drop(VillagerEntityMCA villager, Inventory forge) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = villager.getEquippedStack(slot);
            if (!isWornGear(stack, slot)) {
                continue;
            }
            ItemStack tagged = stack.copy();
            NbtCompound mark = new NbtCompound();
            mark.putUuid("owner", villager.getUuid());
            mark.putString("slot", slot.getName());
            tagged.getOrCreateNbt().put(TAG, mark);
            ItemStack rest = DepositTask.insert(forge, tagged);
            if (rest.isEmpty()) {
                villager.equipStack(slot, ItemStack.EMPTY);
            }
        }
        forge.markDirty();
    }

    private static void fetch(VillagerEntityMCA villager, Inventory forge) {
        for (int i = 0; i < forge.size(); i++) {
            ItemStack stack = forge.getStack(i);
            if (!isRepairedFor(stack, villager.getUuid())) {
                continue;
            }
            NbtCompound mark = stack.getNbt().getCompound(TAG);
            EquipmentSlot slot = slotByName(mark.getString("slot"));
            ItemStack item = stack.copy();
            item.getNbt().remove(TAG);
            if (item.getNbt().isEmpty()) {
                item.setNbt(null);
            }
            if (slot != null && villager.getEquippedStack(slot).isEmpty()) {
                villager.equipStack(slot, item);
            } else {
                ItemStack rest = villager.getInventory().addStack(item);
                if (!rest.isEmpty()) {
                    continue;
                }
            }
            forge.setStack(i, ItemStack.EMPTY);
        }
        forge.markDirty();
    }

    @Nullable
    private static EquipmentSlot slotByName(String name) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getName().equals(name)) {
                return slot;
            }
        }
        return null;
    }

    private static boolean isRepairedFor(ItemStack stack, UUID owner) {
        if (stack.isEmpty() || !stack.hasNbt() || !stack.getNbt().contains(TAG) || stack.getDamage() > 0) {
            return false;
        }
        NbtCompound mark = stack.getNbt().getCompound(TAG);
        return mark.containsUuid("owner") && owner.equals(mark.getUuid("owner"));
    }

    private static boolean hasRepaired(Inventory forge, UUID owner) {
        for (int i = 0; i < forge.size(); i++) {
            if (isRepairedFor(forge.getStack(i), owner)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRoom(Inventory forge) {
        for (int i = 0; i < forge.size(); i++) {
            if (forge.getStack(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static Inventory forgeAt(ServerWorld world, BlockPos pos) {
        if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            return null;
        }
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chest) {
            return ChestBlock.getInventory(chest, state, world, pos, true);
        }
        return world.getBlockEntity(pos) instanceof Inventory inventory ? inventory : null;
    }
}
