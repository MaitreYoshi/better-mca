package com.maitreyoshi.mcavie.village;

import net.conczin.mca.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
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

    public MutableComponent getName() {
        return Component.translatable("gui.mca_vie.quality." + name().toLowerCase(java.util.Locale.ROOT));
    }

    // storeys = niveaux comptés, rez-de-chaussée inclus (1 = rez-de-chaussée seul)
    public record Measure(int lights, int windows, int decor, int floor, int storeys) {
        public CompoundTag toNbt() {
            CompoundTag nbt = new CompoundTag();
            nbt.putInt("lights", lights);
            nbt.putInt("windows", windows);
            nbt.putInt("decor", decor);
            nbt.putInt("floor", floor);
            nbt.putInt("storeys", storeys);
            return nbt;
        }

        public static Measure fromNbt(CompoundTag nbt) {
            return new Measure(nbt.getInt("lights"), nbt.getInt("windows"), nbt.getInt("decor"), nbt.getInt("floor"), nbt.getInt("storeys"));
        }
    }

    public interface Holder {
        Measure mcavie$getMeasure();

        void mcavie$setMeasure(Measure measure);
    }

    public static Measure measureOf(net.conczin.mca.server.world.data.Building building) {
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
        if (surface >= config.magnificentSurface * minSurface && measure.windows() >= config.magnificentWindows && measure.decor() >= config.magnificentDecor) {
            return MAGNIFICENT;
        }
        if (surface >= config.beautifulSurface * minSurface && measure.windows() >= config.beautifulWindows && measure.decor() >= config.beautifulDecor) {
            return BEAUTIFUL;
        }
        return CORRECT;
    }

    public static Measure measure(Level world, BlockPos source, boolean strictScan) {
        int maxSize = Config.getInstance().maxBuildingSize;
        int maxRadius = Config.getInstance().maxBuildingRadius;

        Set<BlockPos> done = new HashSet<>();
        Set<BlockPos> hull = new HashSet<>();
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
            if (p.distManhattan(source) >= maxRadius) {
                continue;
            }
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
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
                if (isPassable(state) && hasRoof(world, n, roofCache)) {
                    queue.add(n);
                    if (outside) {
                        beyondDoor.add(n);
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
            if (state.getBlock() instanceof LiquidBlock || state.is(BlockTags.FIRE) || state.is(BlockTags.LEAVES) || isNatural(state)) {
                continue;
            }
            if (state.getLightEmission() > 0) {
                lights++;
            }
            if (isWindow(state)) {
                glass.add(pos);
            } else if (isStructural(state)) {
                if (hasRoof(world, pos, roofCache)) {
                    decor.add(state.getBlock());
                }
            } else if (!state.isSolidRender(world, pos) || state.hasBlockEntity()) {
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
        return new Measure(lights, countWindows(glass), decor.size(), floor.size(), countStoreys(floor));
    }

    public static boolean isPassable(BlockState state) {
        return state.isAir() || isClimbOrStep(state);
    }

    private static boolean isClimbOrStep(BlockState state) {
        Block block = state.getBlock();
        return state.is(BlockTags.CLIMBABLE) || block instanceof StairBlock || block instanceof TrapDoorBlock;
    }

    private static boolean isFloor(Level world, BlockPos pos, BlockState state) {
        if (!state.isAir()) {
            return false;
        }
        BlockPos below = pos.below();
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
        return block instanceof TransparentBlock || block instanceof StainedGlassPaneBlock || state.is(Blocks.GLASS_PANE);
    }

    private static boolean isNatural(BlockState state) {
        return state.canBeReplaced() || state.is(BlockTags.DIRT) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.FARMLAND);
    }

    private static boolean isStructural(BlockState state) {
        Block block = state.getBlock();
        return block instanceof StairBlock || block instanceof SlabBlock || block instanceof WallBlock || block instanceof FenceBlock;
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
                for (BlockPos n : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
                    if (glass.contains(n) && seen.add(n.immutable())) {
                        queue.add(n.immutable());
                    }
                }
            }
        }
        return windows;
    }

    private static boolean hasRoof(Level world, BlockPos pos, Map<BlockPos, Boolean> cache) {
        Boolean cached = cache.get(pos);
        if (cached != null) {
            return cached;
        }
        boolean roof = false;
        java.util.List<BlockPos> column = new java.util.ArrayList<>();
        column.add(pos);
        BlockPos p = pos;
        for (int i = 0; i < ROOF_SCAN_HEIGHT; i++) {
            p = p.above();
            Boolean known = cache.get(p);
            if (known != null) {
                roof = known;
                break;
            }
            BlockState state = world.getBlockState(p);
            if (!isPassable(state)) {
                roof =!state.is(BlockTags.LEAVES);
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
