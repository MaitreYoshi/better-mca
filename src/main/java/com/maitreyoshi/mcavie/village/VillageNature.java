package com.maitreyoshi.mcavie.village;

import com.maitreyoshi.mcavie.work.tasks.DepositTask;
import net.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class VillageNature {
    private VillageNature() {
    }

    public record Storage(Set<BlockPos> positions, List<Container> inventories) {
        public boolean isEmpty() {
            return inventories.isEmpty();
        }
    }

    public static int unitsPerEmerald(ItemStack stack) {
        if (stack.isEmpty() || stack.hasTag()) {
            return 0;
        }
        return VillageConfig.get().unitsPerEmerald(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    public static boolean hasStorage(Village village, Prestige.Result result) {
        return Prestige.hasActive(result, village, "storage");
    }

    public static Storage storage(Village village, ServerLevel world, Prestige.Result result) {
        Map<BlockPos, Container> found = new LinkedHashMap<>();
        village.getBuildingsOfType("storage").forEach(building -> {
            Prestige.Score score = result.score(building);
            if (score == null || !score.isActive()) {
                return;
            }
            building.getBlocks().values().forEach(positions -> {
                for (BlockPos pos : positions) {
                    BlockPos key = canonical(world, pos);
                    if (key != null && !found.containsKey(key)) {
                        Container inventory = openContainer(world, pos);
                        if (inventory != null && isStorageBlock(world, pos)) {
                            found.put(key, inventory);
                        }
                    }
                }
            });
        });
        return new Storage(found.keySet(), new ArrayList<>(found.values()));
    }

    private static boolean isLoaded(ServerLevel world, BlockPos pos) {
        return world.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    public static BlockPos canonical(ServerLevel world, BlockPos pos) {
        if (!isLoaded(world, pos)) {
            return null;
        }
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
            if (!isLoaded(world, other)) {
                return null;
            }
            return other.asLong() < pos.asLong() ? other.immutable() : pos.immutable();
        }
        return pos.immutable();
    }

    private static boolean isStorageBlock(ServerLevel world, BlockPos pos) {
        return world.getBlockState(pos).getBlock() instanceof ChestBlock || world.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity;
    }

    public static Container openContainer(ServerLevel world, BlockPos pos) {
        if (canonical(world, pos) == null) {
            return null;
        }
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chest) {
            return ChestBlock.getContainer(chest, state, world, pos, true);
        }
        return world.getBlockEntity(pos) instanceof Container inventory ? inventory : null;
    }

    public static double collect(Container depot, List<Container> storage, double need) {
        double collected = 0;
        for (int slot = 0; slot < depot.getContainerSize() && need - collected > 1e-9; slot++) {
            ItemStack stack = depot.getItem(slot);
            int units = unitsPerEmerald(stack);
            if (units == 0) {
                continue;
            }
            int wanted = (int) Math.min(stack.getCount(), Math.floor((need - collected) * units + 1e-9));
            if (wanted <= 0) {
                continue;
            }
            ItemStack moving = stack.copyWithCount(wanted);
            for (Container target : storage) {
                if (moving.isEmpty()) {
                    break;
                }
                moving = DepositTask.insert(target, moving);
                target.setChanged();
            }
            int moved = wanted - moving.getCount();
            if (moved > 0) {
                stack.shrink(moved);
                depot.setChanged();
                collected += (double) moved / units;
            }
            if (!moving.isEmpty()) {
                break;
            }
        }
        return collected;
    }
}
