package com.maitreyoshi.mcavie.life;

import net.minecraft.world.level.block.state.BlockBehaviour;
import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.compat.Registration;
import net.conczin.mca.block.TombstoneBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
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
    public static net.minecraft.world.level.block.entity.BlockEntityType<MemorialBlockEntity> MEMORIAL_ENTITY;
    public static GhostAltarBlock GHOST_ALTAR;
    public static net.minecraft.world.level.block.entity.BlockEntityType<GhostAltarBlockEntity> GHOST_ALTAR_ENTITY;
    public static net.minecraft.world.entity.EntityType<GhostEntity> GHOST;

    private static <T extends Block> void register(String name, Supplier<T> factory, Consumer<T> onRegistered) {
        Registration.register(Registries.BLOCK, name, factory, onRegistered);
        Registration.register(Registries.ITEM, name, () -> new BlockItem(BuiltInRegistries.BLOCK.getValue(MCAVie.id(name)), Registration.blockItem(name)), item -> {
        });
    }

    public static void register() {
        register("wooden_cross_tomb", () -> new Tomb(Registration.block("wooden_cross_tomb", BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).noOcclusion()), TombstoneDetails.POOR,
            new double[][]{{6.9, 0, 7.2, 9.1, 17.3, 8.8}, {2.6, 12.2, 7.2, 13.4, 14.1, 8.8}, {3.2, 1.9, 6.8, 12.8, 11.2, 8.8}}), block -> WOODEN_CROSS = block);
        register("stele_tomb", () -> new Tomb(Registration.block("stele_tomb", BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).noOcclusion()), TombstoneDetails.MIDDLE,
            new double[][]{{0.8, 0, 4.8, 15.2, 1.9, 11.2}, {2.2, 1.9, 7, 13.8, 16, 9.3}}), block -> STELE = block);
        register("marble_tomb", () -> new Tomb(Registration.block("marble_tomb", BlockBehaviour.Properties.ofFullCopy(Blocks.QUARTZ_BLOCK).noOcclusion()), TombstoneDetails.RICH,
            new double[][]{{0.3, 0, 0, 15.7, 2.6, 15.7}, {1.6, 2.6, 12.8, 14.4, 19.7, 15.5}}), block -> MARBLE = block);
        register("family_vault", () -> new VaultBlock(Registration.block("family_vault", BlockBehaviour.Properties.ofFullCopy(Blocks.QUARTZ_BLOCK).strength(3, 1200).noOcclusion())), block -> VAULT = block);
        VaultBlock.registerNetwork();
        register("war_memorial", () -> new MemorialBlock(Registration.block("war_memorial", BlockBehaviour.Properties.ofFullCopy(Blocks.QUARTZ_BLOCK).strength(4, 1200).noOcclusion())), block -> MEMORIAL = block);
        Registration.register(Registries.BLOCK_ENTITY_TYPE, "war_memorial",
                () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(MemorialBlockEntity::new, java.util.Set.of(MEMORIAL)),
                type -> MEMORIAL_ENTITY = type);
        MemorialBlock.registerNetwork();
        register("ghost_altar", () -> new GhostAltarBlock(Registration.block("ghost_altar", BlockBehaviour.Properties.ofFullCopy(Blocks.DARK_OAK_PLANKS).strength(1.5f).noOcclusion()
                .lightLevel(state -> 6))), block -> GHOST_ALTAR = block);
        Registration.register(Registries.BLOCK_ENTITY_TYPE, "ghost_altar",
                () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(GhostAltarBlockEntity::new, java.util.Set.of(GHOST_ALTAR)),
                type -> GHOST_ALTAR_ENTITY = type);
        Registration.register(Registries.ENTITY_TYPE, "ghost",
                () -> net.minecraft.world.entity.EntityType.Builder.<GhostEntity>of(GhostEntity::new, net.minecraft.world.entity.MobCategory.MISC)
                        .sized(0.6f, 1.95f).fireImmune().clientTrackingRange(10).build(Registration.entity("ghost")),
                type -> {
                    GHOST = type;
                    dev.architectury.registry.level.entity.EntityAttributeRegistry.register(() -> type, GhostEntity::createAttributes);
                });
        Ghosts.registerNetwork();
    }

    public static Tomb forTier(int tier) {
        return tier == TombstoneDetails.POOR ? WOODEN_CROSS : tier == TombstoneDetails.RICH ? MARBLE : STELE;
    }

    public static class Tomb extends TombstoneBlock {
        private final int tier;
        private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

        public Tomb(Properties settings, int tier, double[][] boxes) {
            super(settings, 70, 30, Vec3.ZERO, 0, true, Shapes.block());
            this.tier = tier;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                VoxelShape shape = Shapes.empty();
                for (double[] b : boxes) {
                    shape = Shapes.or(shape, switch (direction) {
                        case EAST -> Block.box(16 - b[5], b[1], b[0], 16 - b[2], b[4], b[3]);
                        case SOUTH -> Block.box(16 - b[3], b[1], 16 - b[5], 16 - b[0], b[4], 16 - b[2]);
                        case WEST -> Block.box(b[2], b[1], 16 - b[3], b[5], b[4], 16 - b[0]);
                        default -> Block.box(b[0], b[1], b[2], b[3], b[4], b[5]);
                    });
                }
                shapes.put(direction, shape);
            }
        }

        public int tier() {
            return tier;
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter view, BlockPos pos, CollisionContext context) {
            return shapes.get(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
        }
    }
}
