package com.maitreyoshi.mcavie.life;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import com.maitreyoshi.mcavie.compat.ServerPlayNetworking;
import net.mca.server.world.data.Village;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public class MemorialBlock extends Block implements BlockEntityProvider {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final int LAYERS = 8;
    public static final int PARTS = LAYERS * 9;
    public static final IntProperty PART = IntProperty.of("part", 0, PARTS - 1);
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

    public MemorialBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(FACING, Direction.NORTH).with(PART, MASTER));
        for (Direction facing : Direction.Type.HORIZONTAL) {
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
            String title = buf.readString(TITLE_MAX * 4);
            String dates = buf.readString(DATES_MAX * 4);
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
        Direction left = facing.rotateYCounterclockwise();
        int x = cx(part);
        int z = cz(part);
        return new BlockPos(back.getOffsetX() * x + left.getOffsetX() * z, cy(part), back.getOffsetZ() * x + left.getOffsetZ() * z);
    }

    public static BlockPos master(BlockPos pos, BlockState state) {
        return pos.subtract(offset(state.get(FACING), state.get(PART)).subtract(offset(state.get(FACING), MASTER)));
    }

    private static VoxelShape shape(Direction facing, int part) {
        Direction back = facing.getOpposite();
        Direction left = facing.rotateYCounterclockwise();
        double ox = cx(part) * 16;
        double oy = cy(part) * 16;
        double oz = cz(part) * 16;
        VoxelShape shape = VoxelShapes.empty();
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
            double ax = 8 + back.getOffsetX() * (x0 - 8) + left.getOffsetX() * (z0 - 8);
            double az = 8 + back.getOffsetZ() * (x0 - 8) + left.getOffsetZ() * (z0 - 8);
            double bx = 8 + back.getOffsetX() * (x1 - 8) + left.getOffsetX() * (z1 - 8);
            double bz = 8 + back.getOffsetZ() * (x1 - 8) + left.getOffsetZ() * (z1 - 8);
            shape = VoxelShapes.union(shape, Block.createCuboidShape(Math.min(ax, bx), y0, Math.min(az, bz), Math.max(ax, bx), y1, Math.max(az, bz)));
        }
        return shape;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state;
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView view, BlockPos pos, ShapeContext context) {
        return shapes.get(state.get(FACING))[state.get(PART)];
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        Direction facing = context.getHorizontalPlayerFacing().getOpposite();
        BlockPos master = context.getBlockPos();
        World world = context.getWorld();
        for (int part = 0; part < PARTS; part++) {
            BlockPos pos = master.add(offset(facing, part)).subtract(offset(facing, MASTER));
            if (!world.isInBuildLimit(pos) || !(part == MASTER ? world.getBlockState(pos).canReplace(context) : world.getBlockState(pos).isReplaceable())) {
                return null;
            }
        }
        return getDefaultState().with(FACING, facing).with(PART, MASTER);
    }

    public static BlockPos partPos(BlockPos master, Direction facing, int part) {
        return master.add(offset(facing, part)).subtract(offset(facing, MASTER));
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (world.isClient) {
            return;
        }
        Direction facing = state.get(FACING);
        for (int part = 0; part < PARTS; part++) {
            if (part != MASTER) {
                world.setBlockState(partPos(pos, facing, part), state.with(PART, part), Block.NOTIFY_ALL);
            }
        }
        if (world.getBlockEntity(pos) instanceof MemorialBlockEntity memorial && world instanceof ServerWorld server) {
            if (placer instanceof ServerPlayerEntity player) {
                memorial.setOwner(player.getUuid());
            }
            link(server, memorial, placer instanceof ServerPlayerEntity player ? player : null);
        }
    }

    private static void link(ServerWorld world, MemorialBlockEntity memorial, @Nullable ServerPlayerEntity player) {
        Optional<Village> village = player != null ? TombReservations.village(world, memorial.getPos(), player)
                : net.mca.server.world.data.VillageManager.get(world).findNearestVillage(memorial.getPos(), 32);
        if (village.isEmpty()) {
            if (player != null) {
                player.sendMessage(Text.translatable("tomb.mca_vie.no_village").formatted(Formatting.RED), true);
            }
            return;
        }
        memorial.setVillage(village.get().getId());
        MemorialRegistry registry = MemorialRegistry.get(world.getServer());
        registry.addMonument(village.get().getId(), world, memorial.getPos());
        world.updateListeners(memorial.getPos(), memorial.getCachedState(), memorial.getCachedState(), Block.NOTIFY_ALL);
        registry.refresh(world.getServer(), village.get().getId());
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!world.isClient && !state.isOf(newState.getBlock())) {
            BlockPos master = master(pos, state);
            Direction facing = state.get(FACING);
            if (state.get(PART) == MASTER && world.getBlockEntity(pos) instanceof MemorialBlockEntity memorial && memorial.village() >= 0) {
                MemorialRegistry registry = MemorialRegistry.get(((ServerWorld) world).getServer());
                registry.removeMonument(memorial.village(), world, pos);
                registry.refresh(((ServerWorld) world).getServer(), memorial.village());
            }
            for (int part = 0; part < PARTS; part++) {
                BlockPos target = partPos(master, facing, part);
                BlockState other = world.getBlockState(target);
                if (!target.equals(pos) && other.isOf(this) && other.get(FACING) == facing && other.get(PART) == part) {
                    world.setBlockState(target, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient && !player.isCreative() && player.canHarvest(state)) {
            Block.dropStack(world, pos, new ItemStack(this));
        }
        super.onBreak(world, pos, state, player);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return state.get(PART) == MASTER ? new MemorialBlockEntity(pos, state) : null;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }
        if (world instanceof ServerWorld server && player instanceof ServerPlayerEntity serverPlayer
                && server.getBlockEntity(master(pos, state)) instanceof MemorialBlockEntity memorial) {
            if (memorial.village() < 0) {
                link(server, memorial, serverPlayer);
            }
            if (player.isSneaking() && player.getMainHandStack().isEmpty()) {
                openEdit(serverPlayer, memorial);
            } else {
                PacketByteBuf out = PacketByteBufs.create();
                out.writeBlockPos(memorial.getPos());
                ServerPlayNetworking.send(serverPlayer, LIST_OPEN, out);
            }
        }
        return ActionResult.success(world.isClient);
    }

    private static boolean canEdit(ServerPlayerEntity player, MemorialBlockEntity memorial) {
        return memorial.owner() == null || memorial.owner().equals(player.getUuid()) || player.hasPermissionLevel(2);
    }

    private static void openEdit(ServerPlayerEntity player, MemorialBlockEntity memorial) {
        if (!canEdit(player, memorial)) {
            player.sendMessage(Text.translatable("block.mca_vie.war_memorial.not_owner").formatted(Formatting.RED), true);
            return;
        }
        PacketByteBuf out = PacketByteBufs.create();
        out.writeBlockPos(memorial.getPos());
        out.writeString(memorial.title() == null ? "" : memorial.title());
        out.writeString(memorial.dates() == null ? "" : memorial.dates());
        ServerPlayNetworking.send(player, EDIT_OPEN, out);
    }

    private static String clean(String text, int max) {
        String out = text.strip();
        return out.length() > max ? out.substring(0, max) : out;
    }

    private static void setTexts(ServerPlayerEntity player, BlockPos pos, String title, String dates) {
        ServerWorld world = player.getServerWorld();
        if (player.squaredDistanceTo(pos.toCenterPos()) > 100 || !(world.getBlockEntity(pos) instanceof MemorialBlockEntity memorial)
                || !canEdit(player, memorial)) {
            return;
        }
        String t = clean(title, TITLE_MAX);
        String d = clean(dates, DATES_MAX);
        memorial.setTexts(t.isEmpty() ? null : t, d.isEmpty() ? null : d);
        world.updateListeners(pos, memorial.getCachedState(), memorial.getCachedState(), Block.NOTIFY_ALL);
    }
}
