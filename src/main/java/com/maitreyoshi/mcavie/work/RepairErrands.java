package com.maitreyoshi.mcavie.work;

import com.maitreyoshi.mcavie.work.tasks.DepositTask;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
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
        if (!(villager.level() instanceof ServerLevel world)) {
            return;
        }
        UUID id = villager.getUUID();
        if (villager.isBaby() || villager.isSleeping() || villager.isPassenger() || WorkManager.isWorking(villager)
                || WorkManager.getJob(villager) == Job.BLACKSMITH
                || villager.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
            ERRANDS.remove(id);
            return;
        }
        long now = world.getGameTime();
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
        Container forge = forgeAt(world, errand.forge());
        if (forge == null || now > errand.until()) {
            ERRANDS.remove(id);
            COOLDOWN.put(id, now + COOLDOWN_TICKS);
            return;
        }
        if (villager.distanceToSqr(Vec3.atCenterOf(errand.forge())) > REACH_SQ) {
            villager.moveTowards(errand.forge(), 0.6f, 1);
            return;
        }
        villager.lookAt(errand.forge());
        villager.swing(InteractionHand.MAIN_HAND);
        if (errand.mode() == Mode.DROP) {
            drop(villager, forge);
        } else {
            fetch(villager, forge);
        }
        ERRANDS.remove(id);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    public static void forget(UUID villager) {
        ERRANDS.remove(villager);
        COOLDOWN.remove(villager);
    }

    @Nullable
    private static Errand plan(ServerLevel world, VillagerEntityMCA villager, long now) {
        boolean worn = false;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = villager.getItemBySlot(slot);
            if (isWornGear(stack, slot)) {
                worn = true;
                break;
            }
        }
        BlockPos best = null;
        Mode mode = null;
        double bestDistance = SEARCH_SQ;
        ResourceLocation dimension = world.dimension().location();
        for (WorkRegistry.Record record : WorkRegistry.get(world.getServer()).all()) {
            if (record.job != Job.BLACKSMITH || record.depot == null
                    || (record.depotDimension != null && !record.depotDimension.equals(dimension))) {
                continue;
            }
            double distance = villager.distanceToSqr(Vec3.atCenterOf(record.depot));
            if (distance >= bestDistance) {
                continue;
            }
            Container forge = forgeAt(world, record.depot);
            if (forge == null) {
                continue;
            }
            Mode here = hasRepaired(forge, villager.getUUID()) ? Mode.FETCH : worn && hasRoom(forge) ? Mode.DROP : null;
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

    private static void drop(VillagerEntityMCA villager, Container forge) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = villager.getItemBySlot(slot);
            if (!isWornGear(stack, slot)) {
                continue;
            }
            ItemStack tagged = stack.copy();
            CompoundTag mark = new CompoundTag();
            mark.putUUID("owner", villager.getUUID());
            mark.putString("slot", slot.getName());
            tagged.getOrCreateTag().put(TAG, mark);
            ItemStack rest = DepositTask.insert(forge, tagged);
            if (rest.isEmpty()) {
                villager.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        forge.setChanged();
    }

    private static void fetch(VillagerEntityMCA villager, Container forge) {
        for (int i = 0; i < forge.getContainerSize(); i++) {
            ItemStack stack = forge.getItem(i);
            if (!isRepairedFor(stack, villager.getUUID())) {
                continue;
            }
            CompoundTag mark = stack.getTag().getCompound(TAG);
            EquipmentSlot slot = slotByName(mark.getString("slot"));
            ItemStack item = stack.copy();
            item.getTag().remove(TAG);
            if (item.getTag().isEmpty()) {
                item.setTag(null);
            }
            if (slot != null && villager.getItemBySlot(slot).isEmpty()) {
                villager.setItemSlot(slot, item);
            } else {
                ItemStack rest = villager.getInventory().addItem(item);
                if (!rest.isEmpty()) {
                    continue;
                }
            }
            forge.setItem(i, ItemStack.EMPTY);
        }
        forge.setChanged();
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
        if (stack.isEmpty() || !stack.hasTag() || !stack.getTag().contains(TAG) || stack.getDamageValue() > 0) {
            return false;
        }
        CompoundTag mark = stack.getTag().getCompound(TAG);
        return mark.hasUUID("owner") && owner.equals(mark.getUUID("owner"));
    }

    private static boolean hasRepaired(Container forge, UUID owner) {
        for (int i = 0; i < forge.getContainerSize(); i++) {
            if (isRepairedFor(forge.getItem(i), owner)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRoom(Container forge) {
        for (int i = 0; i < forge.getContainerSize(); i++) {
            if (forge.getItem(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static Container forgeAt(ServerLevel world, BlockPos pos) {
        if (!world.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
            return null;
        }
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chest) {
            return ChestBlock.getContainer(chest, state, world, pos, true);
        }
        return world.getBlockEntity(pos) instanceof Container inventory ? inventory : null;
    }
}
