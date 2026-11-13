package com.maitreyoshi.mcavie.life;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import net.mca.server.world.data.FamilyTree;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** Autel du souvenir : posé au mur, la photo d'un défunt le fait revenir en fantôme la nuit. */
public class GhostAltarBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        double[] b = {2, 1.5, 10, 14, 16, 16};
        SHAPES.put(Direction.NORTH, Block.createCuboidShape(b[0], b[1], b[2], b[3], b[4], b[5]));
        SHAPES.put(Direction.SOUTH, Block.createCuboidShape(16 - b[3], b[1], 16 - b[5], 16 - b[0], b[4], 16 - b[2]));
        SHAPES.put(Direction.EAST, Block.createCuboidShape(16 - b[5], b[1], b[0], 16 - b[2], b[4], b[3]));
        SHAPES.put(Direction.WEST, Block.createCuboidShape(b[2], b[1], 16 - b[3], b[5], b[4], 16 - b[0]));
    }

    public GhostAltarBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView view, BlockPos pos, ShapeContext context) {
        return SHAPES.get(state.get(FACING));
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        for (Direction direction : context.getPlacementDirections()) {
            if (direction.getAxis().isHorizontal()) {
                BlockState state = getDefaultState().with(FACING, direction.getOpposite());
                if (state.canPlaceAt(context.getWorld(), context.getBlockPos())) {
                    return state;
                }
            }
        }
        return null;
    }

    @Override
    public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        Direction facing = state.get(FACING);
        BlockPos wall = pos.offset(facing.getOpposite());
        return world.getBlockState(wall).isSideSolidFullSquare(world, wall, facing);
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighbor, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        return direction == state.get(FACING).getOpposite() && !state.canPlaceAt(world, pos)
                ? net.minecraft.block.Blocks.AIR.getDefaultState() : state;
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new GhostAltarBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : checkType(type, TombstoneBlocks.GHOST_ALTAR_ENTITY, GhostAltarBlockEntity::tick);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof GhostAltarBlockEntity altar)) {
            return ActionResult.PASS;
        }
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        if (player.isSneaking()) {
            Ghosts.open((ServerPlayerEntity) player, altar);
        } else {
            UUID person = altar.person();
            Text message = person == null ? Text.translatable("block.mca_vie.ghost_altar.empty")
                    : Text.translatable("block.mca_vie.ghost_altar.person", FamilyTree.get(((ServerPlayerEntity) player).getServerWorld())
                    .getOrEmpty(person).map(n -> n.getName()).orElse("?"));
            player.sendMessage(message, true);
        }
        return ActionResult.CONSUME;
    }
}
