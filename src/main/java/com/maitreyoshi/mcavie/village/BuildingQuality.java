package com.maitreyoshi.mcavie.village;

import net.mca.Config;
import net.minecraft.block.AbstractGlassBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.StainedGlassPaneBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

// ordinal sauvegardé : ne jamais réordonner, seulement ajouter à la fin
public enum BuildingQuality {
    RUDIMENTARY,
    CORRECT,
    BEAUTIFUL,
    MAGNIFICENT;

    private static final int ACCENT_MAX_COUNT = 4;
    private static final int ROOF_SCAN_HEIGHT = 16;
    private static final int STOREY_MIN_FLOOR = 4;
    private static final int STOREY_MIN_HEIGHT = 3;

    public double multiplier() {
        List<Double> list = VillageConfig.get().qualityMultipliers;
        return list != null && list.size() > ordinal() ? list.get(ordinal()) : 1;
    }

    public MutableText getName() {
        return Text.translatable("gui.mca_vie.quality." + name().toLowerCase(java.util.Locale.ROOT));
    }

    public record Measure(int lights, int windows, int decor, int openings, int floor, int storeys) {
        public NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putInt("lights", lights);
            nbt.putInt("windows", windows);
            nbt.putInt("decor", decor);
            nbt.putInt("openings", openings);
            nbt.putInt("floor", floor);
            nbt.putInt("storeys", storeys);
            return nbt;
        }

        public static Measure fromNbt(NbtCompound nbt) {
            return new Measure(nbt.getInt("lights"), nbt.getInt("windows"), nbt.getInt("decor"), nbt.getInt("openings"), nbt.getInt("floor"), nbt.getInt("storeys"));
        }
    }

    public interface Holder {
        Measure mcavie$getMeasure();

        void mcavie$setMeasure(Measure measure);
    }

    public static Measure measureOf(net.mca.server.world.data.Building building) {
        return ((Holder) building).mcavie$getMeasure();
    }

    public static BuildingQuality of(Measure measure, int surface, int minSurface) {
        if (measure == null || minSurface <= 0) {
            return CORRECT;
        }
        VillageConfig config = VillageConfig.get();
        if (measure.lights() <= 0 || measure.windows() <= 0) {
            return RUDIMENTARY;
        }
        if (measure.openings() > 0) {
            return CORRECT;
        }
        if (surface >= config.magnificentSurface * minSurface && measure.windows() >= config.magnificentWindows && measure.decor() >= config.magnificentDecor) {
            return MAGNIFICENT;
        }
        if (surface >= config.beautifulSurface * minSurface && measure.windows() >= config.beautifulWindows && measure.decor() >= config.beautifulDecor) {
            return BEAUTIFUL;
        }
        return CORRECT;
    }

    public static Measure measure(World world, BlockPos source, boolean strictScan) {
        int maxSize = Config.getInstance().maxBuildingSize;
        int maxRadius = Config.getInstance().maxBuildingRadius;

        Set<BlockPos> done = new HashSet<>();
        Set<BlockPos> hull = new HashSet<>();
        Set<BlockPos> openings = new HashSet<>();
        Set<BlockPos> floor = new HashSet<>();
        Set<BlockPos> beyondDoor = new HashSet<>();
        Map<BlockPos, Boolean> roofCache = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(source);
        done.add(source);
        int scanned = 0;
        while (!queue.isEmpty() && scanned++ < maxSize) {
            BlockPos p = queue.removeLast();
            BlockState here = world.getBlockState(p);
            boolean isDoor = here.getBlock() instanceof DoorBlock;
            boolean outside = isDoor || beyondDoor.contains(p);
            if (!isDoor && isFloor(world, p, here)) {
                floor.add(p);
            }
            if (p.getManhattanDistance(source) >= maxRadius) {
                continue;
            }
            for (Direction d : Direction.values()) {
                BlockPos n = p.offset(d);
                if (!done.add(n)) {
                    continue;
                }
                BlockState state = world.getBlockState(n);
                if (state.getBlock() instanceof DoorBlock) {
                    if (!strictScan) {
                        queue.add(n);
                    }
                    continue;
                }
                boolean air = state.isAir();
                if (!air) {
                    hull.add(n);
                }
                if (isPassable(state)) {
                    if (hasRoof(world, n, roofCache)) {
                        queue.add(n);
                        if (outside) {
                            beyondDoor.add(n);
                        }
                    } else if (air && !outside && !isClimbOrStep(here)) {
                        openings.add(n);
                    }
                }
            }
        }

        int lights = 0;
        Set<BlockPos> glass = new HashSet<>();
        Map<Block, Integer> fullBlocks = new HashMap<>();
        Set<Block> decor = new HashSet<>();
        for (BlockPos pos : hull) {
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof FluidBlock || state.isIn(BlockTags.FIRE) || state.isIn(BlockTags.LEAVES) || isNatural(state)) {
                continue;
            }
            if (state.getLuminance() > 0) {
                lights++;
            }
            if (isWindow(state)) {
                glass.add(pos);
            } else if (isStructural(state)) {
                if (hasRoof(world, pos, roofCache)) {
                    decor.add(state.getBlock());
                }
            } else if (!state.isOpaqueFullCube(world, pos) || state.hasBlockEntity()) {
                decor.add(state.getBlock());
            } else {
                fullBlocks.merge(state.getBlock(), 1, Integer::sum);
            }
        }
        fullBlocks.forEach((block, count) -> {
            if (count <= ACCENT_MAX_COUNT) {
                decor.add(block);
            }
        });
        return new Measure(lights, countWindows(glass), decor.size(), openings.size(), floor.size(), countStoreys(floor));
    }

    public static boolean isPassable(BlockState state) {
        return state.isAir() || isClimbOrStep(state);
    }

    private static boolean isClimbOrStep(BlockState state) {
        Block block = state.getBlock();
        return state.isIn(BlockTags.CLIMBABLE) || block instanceof StairsBlock || block instanceof TrapdoorBlock;
    }

    private static boolean isFloor(World world, BlockPos pos, BlockState state) {
        if (!state.isAir()) {
            return false;
        }
        BlockPos below = pos.down();
        BlockState ground = world.getBlockState(below);
        return !isPassable(ground) && !ground.getCollisionShape(world, below).isEmpty();
    }

    private static int countStoreys(Set<BlockPos> floor) {
        Map<Integer, Integer> levels = new TreeMap<>();
        for (BlockPos pos : floor) {
            levels.merge(pos.getY(), 1, Integer::sum);
        }
        int storeys = 0;
        int last = Integer.MIN_VALUE;
        for (Map.Entry<Integer, Integer> level : levels.entrySet()) {
            if (level.getValue() >= STOREY_MIN_FLOOR && (storeys == 0 || level.getKey() - last >= STOREY_MIN_HEIGHT)) {
                storeys++;
                last = level.getKey();
            }
        }
        return storeys;
    }

    public static boolean isWindow(BlockState state) {
        Block block = state.getBlock();
        return block instanceof AbstractGlassBlock || block instanceof StainedGlassPaneBlock || state.isOf(Blocks.GLASS_PANE);
    }

    private static boolean isNatural(BlockState state) {
        return state.isReplaceable() || state.isIn(BlockTags.DIRT) || state.isIn(BlockTags.FLOWERS) || state.isIn(BlockTags.SAPLINGS)
                || state.isIn(BlockTags.SAND) || state.isIn(BlockTags.BASE_STONE_OVERWORLD) || state.isOf(Blocks.DIRT_PATH)
                || state.isOf(Blocks.GRAVEL) || state.isOf(Blocks.FARMLAND);
    }

    private static boolean isStructural(BlockState state) {
        Block block = state.getBlock();
        return block instanceof StairsBlock || block instanceof SlabBlock || block instanceof WallBlock || block instanceof FenceBlock;
    }

    private static int countWindows(Set<BlockPos> glass) {
        Set<BlockPos> seen = new HashSet<>();
        int windows = 0;
        for (BlockPos start : glass) {
            if (!seen.add(start)) {
                continue;
            }
            windows++;
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                BlockPos p = queue.removeLast();
                for (BlockPos n : BlockPos.iterate(p.add(-1, -1, -1), p.add(1, 1, 1))) {
                    if (glass.contains(n) && seen.add(n.toImmutable())) {
                        queue.add(n.toImmutable());
                    }
                }
            }
        }
        return windows;
    }

    private static boolean hasRoof(World world, BlockPos pos, Map<BlockPos, Boolean> cache) {
        Boolean cached = cache.get(pos);
        if (cached != null) {
            return cached;
        }
        boolean roof = false;
        java.util.List<BlockPos> column = new java.util.ArrayList<>();
        column.add(pos);
        BlockPos p = pos;
        for (int i = 0; i < ROOF_SCAN_HEIGHT; i++) {
            p = p.up();
            Boolean known = cache.get(p);
            if (known != null) {
                roof = known;
                break;
            }
            BlockState state = world.getBlockState(p);
            if (!isPassable(state)) {
                roof =!state.isIn(BlockTags.LEAVES);
                break;
            }
            column.add(p);
        }
        for (BlockPos c : column) {
            cache.put(c, roof);
        }
        return roof;
    }
}
