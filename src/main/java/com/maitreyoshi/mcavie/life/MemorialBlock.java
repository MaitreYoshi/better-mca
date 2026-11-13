package com.maitreyoshi.mcavie.life;

import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.server.permissions.Permissions;
import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import com.maitreyoshi.mcavie.compat.ServerPlayNetworking;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public class MemorialBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final int LAYERS = 8;
    public static final int PARTS = LAYERS * 9;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, PARTS - 1);
    public static final int MASTER = 4;
    public static final int TITLE_MAX = 64;
    public static final int DATES_MAX = 24;
    public static final Identifier EDIT_OPEN = MCAVie.id("war_memorial_edit_open");
    public static final Identifier EDIT_SET = MCAVie.id("war_memorial_edit_set");
    public static final Identifier LIST_OPEN = MCAVie.id("war_memorial_list_open");
    private static final double[][] BOXES = {
            {-16, 0, -16, 32, 1, 32}, {-13, 1, -13, 29, 3, 29}, {-10, 3, -10, 26, 5, 26}, {-7, 5, -7, 23, 42, 23},
            {-8, 38, -8, 24, 42, 24}, {-3, 42, -3, 19, 48, 19}, {0, 48, 0, 16, 86, 16}, {-0.5, 86, -0.5, 16.5, 91, 16.5},
            {1.5, 91, 1.5, 14.5, 100, 14.5}, {3, 100, 3, 13, 117, 13}, {-16, 1, 1.5, -7, 36, 14.5},
            {-16, 1, -16, -12, 10.5, -12}, {-16, 1, 28, -12, 10.5, 32}, {28, 1, -16, 32, 10.5, -12}, {28, 1, 28, 32, 10.5, 32}
    };

    private final Map<Direction, VoxelShape[]> shapes = new EnumMap<>(Direction.class);

    public MemorialBlock(Properties settings) {
        super(settings);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(PART, MASTER));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape[] parts = new VoxelShape[PARTS];
            for (int part = 0; part < PARTS; part++) {
                parts[part] = shape(facing, part);
            }
            shapes.put(facing, parts);
        }
    }

    public static void registerNetwork() {
        ServerPlayNetworking.registerGlobalReceiver(EDIT_SET, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            String title = buf.readUtf(TITLE_MAX * 4);
            String dates = buf.readUtf(DATES_MAX * 4);
            server.execute(() -> setTexts(player, pos, title, dates));
        });
    }

    public static int cx(int part) {
        return part % 3 - 1;
    }

    public static int cz(int part) {
        return part / 3 % 3 - 1;
    }

    public static int cy(int part) {
        return part / 9;
    }

    public static BlockPos offset(Direction facing, int part) {
        Direction back = facing.getOpposite();
        Direction left = facing.getCounterClockWise();
        int x = cx(part);
        int z = cz(part);
        return new BlockPos(back.getStepX() * x + left.getStepX() * z, cy(part), back.getStepZ() * x + left.getStepZ() * z);
    }

    public static BlockPos master(BlockPos pos, BlockState state) {
        return pos.subtract(offset(state.getValue(FACING), state.getValue(PART)).subtract(offset(state.getValue(FACING), MASTER)));
    }

    private static VoxelShape shape(Direction facing, int part) {
        Direction back = facing.getOpposite();
        Direction left = facing.getCounterClockWise();
        double ox = cx(part) * 16;
        double oy = cy(part) * 16;
        double oz = cz(part) * 16;
        VoxelShape shape = Shapes.empty();
        for (double[] b : BOXES) {
            double x0 = Math.max(b[0] - ox, 0);
            double x1 = Math.min(b[3] - ox, 16);
            double y0 = Math.max(b[1] - oy, 0);
            double y1 = Math.min(b[4] - oy, 16);
            double z0 = Math.max(b[2] - oz, 0);
            double z1 = Math.min(b[5] - oz, 16);
            if (x0 >= x1 || y0 >= y1 || z0 >= z1) {
                continue;
            }
            double ax = 8 + back.getStepX() * (x0 - 8) + left.getStepX() * (z0 - 8);
            double az = 8 + back.getStepZ() * (x0 - 8) + left.getStepZ() * (z0 - 8);
            double bx = 8 + back.getStepX() * (x1 - 8) + left.getStepX() * (z1 - 8);
            double bz = 8 + back.getStepZ() * (x1 - 8) + left.getStepZ() * (z1 - 8);
            shape = Shapes.or(shape, Block.box(Math.min(ax, bx), y0, Math.min(az, bz), Math.max(ax, bx), y1, Math.max(az, bz)));
        }
        return shape;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter view, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING))[state.getValue(PART)];
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos master = context.getClickedPos();
        Level world = context.getLevel();
        for (int part = 0; part < PARTS; part++) {
            BlockPos pos = master.offset(offset(facing, part)).subtract(offset(facing, MASTER));
            if (!world.isInWorldBounds(pos) || !(part == MASTER ? world.getBlockState(pos).canBeReplaced(context) : world.getBlockState(pos).canBeReplaced())) {
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, facing).setValue(PART, MASTER);
    }

    public static BlockPos partPos(BlockPos master, Direction facing, int part) {
        return master.offset(offset(facing, part)).subtract(offset(facing, MASTER));
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (world.isClientSide()) {
            return;
        }
        Direction facing = state.getValue(FACING);
        for (int part = 0; part < PARTS; part++) {
            if (part != MASTER) {
                world.setBlock(partPos(pos, facing, part), state.setValue(PART, part), Block.UPDATE_ALL);
            }
        }
        if (world.getBlockEntity(pos) instanceof MemorialBlockEntity memorial && world instanceof ServerLevel server) {
            if (placer instanceof ServerPlayer player) {
                memorial.setOwner(player.getUUID());
            }
            link(server, memorial, placer instanceof ServerPlayer player ? player : null);
        }
    }

    private static void link(ServerLevel world, MemorialBlockEntity memorial, @Nullable ServerPlayer player) {
        Optional<Village> village = player != null ? TombReservations.village(world, memorial.getBlockPos(), player)
                : net.conczin.mca.server.world.data.VillageManager.get(world).findNearestVillage(memorial.getBlockPos(), 32);
        if (village.isEmpty()) {
            if (player != null) {
                player.sendOverlayMessage(Component.translatable("tomb.mca_vie.no_village").withStyle(ChatFormatting.RED));
            }
            return;
        }
        memorial.setVillage(village.get().getId());
        MemorialRegistry registry = MemorialRegistry.get(world.getServer());
        registry.addMonument(village.get().getId(), world, memorial.getBlockPos());
        world.sendBlockUpdated(memorial.getBlockPos(), memorial.getBlockState(), memorial.getBlockState(), Block.UPDATE_ALL);
        registry.refresh(world.getServer(), village.get().getId());
    }

    // le retrait du registre se fait dans MemorialBlockEntity.preRemoveSideEffects (l'entité n'existe plus ici)
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean moved) {
        if (!world.getBlockState(pos).is(this)) {
            BlockPos master = master(pos, state);
            Direction facing = state.getValue(FACING);
            for (int part = 0; part < PARTS; part++) {
                BlockPos target = partPos(master, facing, part);
                BlockState other = world.getBlockState(target);
                if (!target.equals(pos) && other.is(this) && other.getValue(FACING) == facing && other.getValue(PART) == part) {
                    world.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        super.affectNeighborsAfterRemoval(state, world, pos, moved);
    }

    @Override
    public BlockState playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        if (!world.isClientSide() && !player.isCreative() && player.hasCorrectToolForDrops(state)) {
            Block.popResource(world, pos, new ItemStack(this));
        }
        return super.playerWillDestroy(world, pos, state, player);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == MASTER ? new MemorialBlockEntity(pos, state) : null;
    }

    @Override
    protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack mcavieStack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return use(state, world, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        return use(state, world, pos, player, InteractionHand.MAIN_HAND, hit);
    }

    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (world instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer
                && server.getBlockEntity(master(pos, state)) instanceof MemorialBlockEntity memorial) {
            if (memorial.village() < 0) {
                link(server, memorial, serverPlayer);
            }
            if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
                openEdit(serverPlayer, memorial);
            } else {
                FriendlyByteBuf out = PacketByteBufs.create();
                out.writeBlockPos(memorial.getBlockPos());
                ServerPlayNetworking.send(serverPlayer, LIST_OPEN, out);
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static boolean canEdit(ServerPlayer player, MemorialBlockEntity memorial) {
        return memorial.owner() == null || memorial.owner().equals(player.getUUID()) || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    private static void openEdit(ServerPlayer player, MemorialBlockEntity memorial) {
        if (!canEdit(player, memorial)) {
            player.sendOverlayMessage(Component.translatable("block.mca_vie.war_memorial.not_owner").withStyle(ChatFormatting.RED));
            return;
        }
        FriendlyByteBuf out = PacketByteBufs.create();
        out.writeBlockPos(memorial.getBlockPos());
        out.writeUtf(memorial.title() == null ? "" : memorial.title());
        out.writeUtf(memorial.dates() == null ? "" : memorial.dates());
        ServerPlayNetworking.send(player, EDIT_OPEN, out);
    }

    private static String clean(String text, int max) {
        String out = text.strip();
        return out.length() > max ? out.substring(0, max) : out;
    }

    private static void setTexts(ServerPlayer player, BlockPos pos, String title, String dates) {
        ServerLevel world = player.level();
        if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > 100 || !(world.getBlockEntity(pos) instanceof MemorialBlockEntity memorial)
                || !canEdit(player, memorial)) {
            return;
        }
        String t = clean(title, TITLE_MAX);
        String d = clean(dates, DATES_MAX);
        memorial.setTexts(t.isEmpty() ? null : t, d.isEmpty() ? null : d);
        world.sendBlockUpdated(pos, memorial.getBlockState(), memorial.getBlockState(), Block.UPDATE_ALL);
    }
}
