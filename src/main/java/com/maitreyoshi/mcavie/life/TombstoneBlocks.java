package com.maitreyoshi.mcavie.life;

import com.maitreyoshi.mcavie.MCAVie;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.mca.block.TombstoneBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

import java.util.EnumMap;
import java.util.Map;

public final class TombstoneBlocks {
    public static final Tomb WOODEN_CROSS = register("wooden_cross_tomb", new Tomb(Block.Settings.copy(Blocks.OAK_PLANKS).nonOpaque(), TombstoneDetails.POOR,
            new double[][]{{6.9, 0, 7.2, 9.1, 17.3, 8.8}, {2.6, 12.2, 7.2, 13.4, 14.1, 8.8}, {3.2, 1.9, 6.8, 12.8, 11.2, 8.8}}));
    public static final Tomb STELE = register("stele_tomb", new Tomb(Block.Settings.copy(Blocks.STONE).nonOpaque(), TombstoneDetails.MIDDLE,
            new double[][]{{0.8, 0, 4.8, 15.2, 1.9, 11.2}, {2.2, 1.9, 7, 13.8, 16, 9.3}}));
    public static final Tomb MARBLE = register("marble_tomb", new Tomb(Block.Settings.copy(Blocks.QUARTZ_BLOCK).nonOpaque(), TombstoneDetails.RICH,
            new double[][]{{0.3, 0, 0, 15.7, 2.6, 15.7}, {1.6, 2.6, 12.8, 14.4, 19.7, 15.5}}));

    private TombstoneBlocks() {
    }

    private static Tomb register(String name, Tomb block) {
        Registry.register(Registries.BLOCK, MCAVie.id(name), block);
        Registry.register(Registries.ITEM, MCAVie.id(name), new BlockItem(block, new Item.Settings()));
        return block;
    }

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
            entries.add(WOODEN_CROSS);
            entries.add(STELE);
            entries.add(MARBLE);
        });
    }

    public static Tomb forTier(int tier) {
        return tier == TombstoneDetails.POOR ? WOODEN_CROSS : tier == TombstoneDetails.RICH ? MARBLE : STELE;
    }

    public static class Tomb extends TombstoneBlock {
        private final int tier;
        private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

        public Tomb(Settings settings, int tier, double[][] boxes) {
            super(settings, 70, 30, Vec3d.ZERO, 0, true, VoxelShapes.fullCube());
            this.tier = tier;
            for (Direction direction : Direction.Type.HORIZONTAL) {
                VoxelShape shape = VoxelShapes.empty();
                for (double[] b : boxes) {
                    shape = VoxelShapes.union(shape, switch (direction) {
                        case EAST -> Block.createCuboidShape(16 - b[5], b[1], b[0], 16 - b[2], b[4], b[3]);
                        case SOUTH -> Block.createCuboidShape(16 - b[3], b[1], 16 - b[5], 16 - b[0], b[4], 16 - b[2]);
                        case WEST -> Block.createCuboidShape(b[2], b[1], 16 - b[3], b[5], b[4], 16 - b[0]);
                        default -> Block.createCuboidShape(b[0], b[1], b[2], b[3], b[4], b[5]);
                    });
                }
                shapes.put(direction, shape);
            }
        }

        public int tier() {
            return tier;
        }

        @Override
        public VoxelShape getOutlineShape(BlockState state, BlockView view, BlockPos pos, ShapeContext context) {
            return shapes.get(state.get(Properties.HORIZONTAL_FACING));
        }
    }
}
