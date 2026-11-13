package com.maitreyoshi.mcavie.life;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.compat.Registration;
import dev.architectury.registry.CreativeTabRegistry;
import net.mca.block.TombstoneBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class TombstoneBlocks {
    public static Tomb WOODEN_CROSS;
    public static Tomb STELE;
    public static Tomb MARBLE;

    private TombstoneBlocks() {
    }

    public static VaultBlock VAULT;
    public static MemorialBlock MEMORIAL;
    public static net.minecraft.block.entity.BlockEntityType<MemorialBlockEntity> MEMORIAL_ENTITY;
    public static GhostAltarBlock GHOST_ALTAR;
    public static net.minecraft.block.entity.BlockEntityType<GhostAltarBlockEntity> GHOST_ALTAR_ENTITY;
    public static net.minecraft.entity.EntityType<GhostEntity> GHOST;

    private static <T extends Block> void register(String name, Supplier<T> factory, Consumer<T> onRegistered) {
        Registration.register(RegistryKeys.BLOCK, name, factory, onRegistered);
        Registration.register(RegistryKeys.ITEM, name, () -> new BlockItem(Registries.BLOCK.get(MCAVie.id(name)), new Item.Settings()), item -> {
        });
    }

    public static void register() {
        register("wooden_cross_tomb", () -> new Tomb(Block.Settings.copy(Blocks.OAK_PLANKS).nonOpaque(), TombstoneDetails.POOR,
            new double[][]{{6.9, 0, 7.2, 9.1, 17.3, 8.8}, {2.6, 12.2, 7.2, 13.4, 14.1, 8.8}, {3.2, 1.9, 6.8, 12.8, 11.2, 8.8}}), block -> WOODEN_CROSS = block);
        register("stele_tomb", () -> new Tomb(Block.Settings.copy(Blocks.STONE).nonOpaque(), TombstoneDetails.MIDDLE,
            new double[][]{{0.8, 0, 4.8, 15.2, 1.9, 11.2}, {2.2, 1.9, 7, 13.8, 16, 9.3}}), block -> STELE = block);
        register("marble_tomb", () -> new Tomb(Block.Settings.copy(Blocks.QUARTZ_BLOCK).nonOpaque(), TombstoneDetails.RICH,
            new double[][]{{0.3, 0, 0, 15.7, 2.6, 15.7}, {1.6, 2.6, 12.8, 14.4, 19.7, 15.5}}), block -> MARBLE = block);
        register("family_vault", () -> new VaultBlock(Block.Settings.copy(Blocks.QUARTZ_BLOCK).strength(3, 1200).nonOpaque()), block -> VAULT = block);
        VaultBlock.registerNetwork();
        register("war_memorial", () -> new MemorialBlock(Block.Settings.copy(Blocks.QUARTZ_BLOCK).strength(4, 1200).nonOpaque()), block -> MEMORIAL = block);
        Registration.register(RegistryKeys.BLOCK_ENTITY_TYPE, "war_memorial",
                () -> net.minecraft.block.entity.BlockEntityType.Builder.create(MemorialBlockEntity::new, MEMORIAL).build(null),
                type -> MEMORIAL_ENTITY = type);
        MemorialBlock.registerNetwork();
        register("ghost_altar", () -> new GhostAltarBlock(Block.Settings.copy(Blocks.DARK_OAK_PLANKS).strength(1.5f).nonOpaque()
                .luminance(state -> 6)), block -> GHOST_ALTAR = block);
        Registration.register(RegistryKeys.BLOCK_ENTITY_TYPE, "ghost_altar",
                () -> net.minecraft.block.entity.BlockEntityType.Builder.create(GhostAltarBlockEntity::new, GHOST_ALTAR).build(null),
                type -> GHOST_ALTAR_ENTITY = type);
        Registration.register(RegistryKeys.ENTITY_TYPE, "ghost",
                () -> net.minecraft.entity.EntityType.Builder.<GhostEntity>create(GhostEntity::new, net.minecraft.entity.SpawnGroup.MISC)
                        .setDimensions(0.6f, 1.95f).makeFireImmune().maxTrackingRange(10).build(MCAVie.id("ghost").toString()),
                type -> {
                    GHOST = type;
                    dev.architectury.registry.level.entity.EntityAttributeRegistry.register(() -> type, GhostEntity::createAttributes);
                });
        Ghosts.registerNetwork();
        CreativeTabRegistry.modify(CreativeTabRegistry.defer(ItemGroups.FUNCTIONAL), (flags, entries, operator) -> {
            entries.add(WOODEN_CROSS);
            entries.add(STELE);
            entries.add(MARBLE);
            entries.add(VAULT);
            entries.add(MEMORIAL);
            entries.add(GHOST_ALTAR);
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
