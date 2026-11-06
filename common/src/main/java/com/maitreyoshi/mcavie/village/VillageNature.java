package com.maitreyoshi.mcavie.village;

import com.maitreyoshi.mcavie.work.tasks.DepositTask;
import net.mca.server.world.data.Village;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class VillageNature {
    private VillageNature() {
    }

    public record Storage(Set<BlockPos> positions, List<Inventory> inventories) {
        public boolean isEmpty() {
            return inventories.isEmpty();
        }
    }

    public static int unitsPerEmerald(ItemStack stack) {
        if (stack.isEmpty() || stack.hasNbt()) {
            return 0;
        }
        return VillageConfig.get().unitsPerEmerald(Registries.ITEM.getId(stack.getItem()).toString());
    }

    public static boolean hasStorage(Village village, Prestige.Result result) {
        return Prestige.hasActive(result, village, "storage");
    }

    public static Storage storage(Village village, ServerWorld world, Prestige.Result result) {
        Map<BlockPos, Inventory> found = new LinkedHashMap<>();
        village.getBuildingsOfType("storage").forEach(building -> {
            Prestige.Score score = result.score(building);
            if (score == null || !score.isActive()) {
                return;
            }
            building.getBlocks().values().forEach(positions -> {
                for (BlockPos pos : positions) {
                    BlockPos key = canonical(world, pos);
                    if (key != null && !found.containsKey(key)) {
                        Inventory inventory = openContainer(world, pos);
                        if (inventory != null && isStorageBlock(world, pos)) {
                            found.put(key, inventory);
                        }
                    }
                }
            });
        });
        return new Storage(found.keySet(), new ArrayList<>(found.values()));
    }

    private static boolean isLoaded(ServerWorld world, BlockPos pos) {
        return world.isChunkLoaded(ChunkSectionPos.getSectionCoord(pos.getX()), ChunkSectionPos.getSectionCoord(pos.getZ()));
    }

    public static BlockPos canonical(ServerWorld world, BlockPos pos) {
        if (!isLoaded(world, pos)) {
            return null;
        }
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
            BlockPos other = pos.offset(ChestBlock.getFacing(state));
            if (!isLoaded(world, other)) {
                return null;
            }
            return other.asLong() < pos.asLong() ? other.toImmutable() : pos.toImmutable();
        }
        return pos.toImmutable();
    }

    private static boolean isStorageBlock(ServerWorld world, BlockPos pos) {
        return world.getBlockState(pos).getBlock() instanceof ChestBlock || world.getBlockEntity(pos) instanceof net.minecraft.block.entity.BarrelBlockEntity;
    }

    public static Inventory openContainer(ServerWorld world, BlockPos pos) {
        if (canonical(world, pos) == null) {
            return null;
        }
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chest) {
            return ChestBlock.getInventory(chest, state, world, pos, true);
        }
        return world.getBlockEntity(pos) instanceof Inventory inventory ? inventory : null;
    }

    public static double collect(Inventory depot, List<Inventory> storage, double need) {
        double collected = 0;
        for (int slot = 0; slot < depot.size() && need - collected > 1e-9; slot++) {
            ItemStack stack = depot.getStack(slot);
            int units = unitsPerEmerald(stack);
            if (units == 0) {
                continue;
            }
            int wanted = (int) Math.min(stack.getCount(), Math.floor((need - collected) * units + 1e-9));
            if (wanted <= 0) {
                continue;
            }
            ItemStack moving = stack.copyWithCount(wanted);
            for (Inventory target : storage) {
                if (moving.isEmpty()) {
                    break;
                }
                moving = DepositTask.insert(target, moving);
                target.markDirty();
            }
            int moved = wanted - moving.getCount();
            if (moved > 0) {
                stack.decrement(moved);
                depot.markDirty();
                collected += (double) moved / units;
            }
            if (!moving.isEmpty()) {
                break;
            }
        }
        return collected;
    }
}
