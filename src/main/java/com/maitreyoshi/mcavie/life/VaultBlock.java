package com.maitreyoshi.mcavie.life;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import com.maitreyoshi.mcavie.compat.ServerPlayNetworking;
import net.mca.block.TombstoneBlock;
import net.mca.item.ScytheItem;
import net.mca.item.StaffOfLifeItem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.mca.server.world.data.GraveyardManager;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class VaultBlock extends TombstoneBlock {
    public static final IntProperty PART = IntProperty.of("part", 0, 11);
    public static final int PLACED = 0;
    public static final int[] SLOTS = {8, 11, 2, 5};
    public static final int NONE = -2;
    public static final int TITLE = -1;
    public static final int TITLE_MAX = 32;
    private static final String SLOTS_KEY = "mca_vie:vault_slots";
    public static final Identifier TITLE_OPEN = MCAVie.id("vault_title_open");
    public static final Identifier TITLE_SET = MCAVie.id("vault_title_set");
    private static final double[][] BOXES = {
            {-16, 0, 0, 32, 1, 32}, {-15, 1, 1, 31, 2, 31}, {-13, 2, 3, 27, 12, 29}, {-14, 12, 2, 28, 14, 30},
            {-11, 14, 5, 19, 15, 27}, {19, 14, 3, 27, 15.5, 29}, {21, 15.5, 4, 24, 32, 28}
    };

    private final Map<Direction, VoxelShape[]> shapes = new EnumMap<>(Direction.class);

    public VaultBlock(Settings settings) {
        super(settings, 70, 30, Vec3d.ZERO, 0, false, VoxelShapes.fullCube());
        setDefaultState(getDefaultState().with(PART, PLACED));
        for (Direction facing : Direction.Type.HORIZONTAL) {
            VoxelShape[] parts = new VoxelShape[12];
            for (int part = 0; part < 12; part++) {
                parts[part] = shape(facing, part);
            }
            shapes.put(facing, parts);
        }
    }

    public static void registerNetwork() {
        ServerPlayNetworking.registerGlobalReceiver(TITLE_SET, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            String title = buf.readString(TITLE_MAX * 4);
            server.execute(() -> setTitle(player, pos, title));
        });
    }

    public static int cx(int part) {
        return part % 3 - 1;
    }

    public static int cy(int part) {
        return part / 6;
    }

    public static int cz(int part) {
        return part / 3 % 2;
    }

    public static int slotOf(int part) {
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] == part) {
                return i;
            }
        }
        return NONE;
    }

    public static boolean isSlot(BlockState state) {
        return state.getBlock() instanceof VaultBlock && slotOf(state.get(PART)) >= 0;
    }

    public static BlockPos offset(Direction facing, int part) {
        Direction back = facing.getOpposite();
        Direction left = facing.rotateYCounterclockwise();
        int x = cx(part);
        int z = cz(part);
        return new BlockPos(back.getOffsetX() * x + left.getOffsetX() * z, cy(part), back.getOffsetZ() * x + left.getOffsetZ() * z);
    }

    public static BlockPos master(BlockPos pos, BlockState state) {
        return pos.subtract(offset(state.get(Properties.HORIZONTAL_FACING), state.get(PART)));
    }

    public static BlockPos partPos(BlockPos master, Direction facing, int part) {
        return master.add(offset(facing, part));
    }

    public static Vec3d local(BlockPos master, Direction facing, Vec3d hit) {
        Direction back = facing.getOpposite();
        Direction left = facing.rotateYCounterclockwise();
        double dx = hit.x - master.getX() - 0.5;
        double dz = hit.z - master.getZ() - 0.5;
        return new Vec3d(8 + 16 * (dx * back.getOffsetX() + dz * back.getOffsetZ()), 16 * (hit.y - master.getY()),
                8 + 16 * (dx * left.getOffsetX() + dz * left.getOffsetZ()));
    }

    public static int slotAt(Vec3d local) {
        if (local.x < 20 || local.y < 15.5) {
            return NONE;
        }
        if (local.y >= 28.3) {
            return local.z >= 10.5 && local.z <= 21.5 ? TITLE : NONE;
        }
        return (local.y >= 22.1 ? 0 : 2) + (local.z < 16 ? 0 : 1);
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
        super.appendProperties(builder);
        builder.add(PART);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView view, BlockPos pos, ShapeContext context) {
        return shapes.get(state.get(Properties.HORIZONTAL_FACING))[state.get(PART)];
    }

    @Override
    public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        return true;
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        Direction facing = context.getHorizontalPlayerFacing().getOpposite();
        BlockPos master = context.getBlockPos().subtract(offset(facing, PLACED));
        World world = context.getWorld();
        for (int part = 0; part < 12; part++) {
            BlockPos pos = partPos(master, facing, part);
            if (!world.isInBuildLimit(pos) || !(part == PLACED ? world.getBlockState(pos).canReplace(context) : world.getBlockState(pos).isReplaceable())) {
                return null;
            }
        }
        return getDefaultState().with(Properties.HORIZONTAL_FACING, facing).with(PART, PLACED);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (world.isClient) {
            return;
        }
        BlockPos master = master(pos, state);
        Direction facing = state.get(Properties.HORIZONTAL_FACING);
        for (int part = 0; part < 12; part++) {
            BlockPos target = partPos(master, facing, part);
            if (!target.equals(pos)) {
                world.setBlockState(target, state.with(PART, part).with(Properties.WATERLOGGED, false), Block.NOTIFY_ALL);
            }
        }
        NbtCompound tag = stack.getNbt();
        if (tag != null && tag.contains(SLOTS_KEY) && world instanceof ServerWorld server) {
            restore(server, master, facing, tag.getList(SLOTS_KEY, NbtElement.COMPOUND_TYPE));
        }
        if (placer instanceof PlayerEntity player) {
            titleHolder(world, master, facing).ifPresent(data -> {
                TombstoneDetails.Holder holder = (TombstoneDetails.Holder) data;
                if (holder.mcavie$vaultOwner() == null) {
                    holder.mcavie$setVaultOwner(player.getUuid());
                    data.markDirty();
                }
            });
        }
    }

    @Override
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        if (isSlot(state)) {
            super.onBlockAdded(state, world, pos, oldState, notify);
        }
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        super.onStateReplaced(state, world, pos, newState, moved);
        if (world.isClient || state.isOf(newState.getBlock())) {
            return;
        }
        BlockPos master = master(pos, state);
        Direction facing = state.get(Properties.HORIZONTAL_FACING);
        for (int part = 0; part < 12; part++) {
            BlockPos target = partPos(master, facing, part);
            BlockState other = world.getBlockState(target);
            if (!target.equals(pos) && other.isOf(this) && other.get(Properties.HORIZONTAL_FACING) == facing && other.get(PART) == part) {
                world.setBlockState(target, other.get(Properties.WATERLOGGED) ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            }
        }
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return isSlot(state) ? super.createBlockEntity(pos, state) : null;
    }

    public static boolean occupied(BlockView world, BlockPos master, Direction facing) {
        for (int part : SLOTS) {
            if (Data.of(world.getBlockEntity(partPos(master, facing, part))).map(Data::hasEntity).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    public static boolean silkTouch(PlayerEntity player) {
        return EnchantmentHelper.getLevel(Enchantments.SILK_TOUCH, player.getMainHandStack()) > 0;
    }

    @Override
    public float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
        return !silkTouch(player) && occupied(world, master(pos, state), state.get(Properties.HORIZONTAL_FACING)) ? 0 : super.calcBlockBreakingDelta(state, player, world, pos);
    }

    @Override
    public void onBlockBreakStart(BlockState state, World world, BlockPos pos, PlayerEntity player) {
        if (!world.isClient && !silkTouch(player) && occupied(world, master(pos, state), state.get(Properties.HORIZONTAL_FACING))) {
            player.sendMessage(Text.translatable("tomb.mca_vie.vault.occupied").formatted(Formatting.GOLD), true);
        }
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        BlockPos master = master(pos, state);
        Direction facing = state.get(Properties.HORIZONTAL_FACING);
        boolean occupied = occupied(world, master, facing);
        if (!world.isClient && (occupied ? silkTouch(player) : !player.isCreative() && player.canHarvest(state))) {
            ItemStack stack = new ItemStack(this);
            if (occupied) {
                NbtList slots = new NbtList();
                NbtList lore = new NbtList();
                for (int part : SLOTS) {
                    Optional<Data> data = Data.of(world.getBlockEntity(partPos(master, facing, part)));
                    slots.add(data.map(BlockEntity::createNbt).orElseGet(NbtCompound::new));
                    data.filter(Data::hasEntity).flatMap(Data::getEntityName)
                            .ifPresent(name -> lore.add(NbtString.of(Text.Serializer.toJson(Text.literal(name.getString()).formatted(Formatting.GRAY)))));
                }
                stack.getOrCreateNbt().put(SLOTS_KEY, slots);
                stack.getOrCreateSubNbt(ItemStack.DISPLAY_KEY).put(ItemStack.LORE_KEY, lore);
            }
            Block.dropStack(world, pos, stack);
        }
        super.onBreak(world, pos, state, player);
    }

    private static void restore(ServerWorld world, BlockPos master, Direction facing, NbtList slots) {
        for (int i = 0; i < SLOTS.length && i < slots.size(); i++) {
            BlockPos target = partPos(master, facing, SLOTS[i]);
            NbtCompound nbt = slots.getCompound(i);
            Data.of(world.getBlockEntity(target)).ifPresent(data -> {
                data.readNbt(nbt);
                data.markDirty();
                GraveyardManager.get(world).setTombstoneState(target,
                        data.hasEntity() ? GraveyardManager.TombstoneState.FILLED : GraveyardManager.TombstoneState.EMPTY);
                world.updateListeners(target, data.getCachedState(), data.getCachedState(), Block.NOTIFY_ALL);
            });
        }
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }
        BlockPos master = master(pos, state);
        Direction facing = state.get(Properties.HORIZONTAL_FACING);
        int slot = slotAt(local(master, facing, hit.getPos()));
        if (slot == NONE) {
            return ActionResult.PASS;
        }
        ItemStack stack = player.getMainHandStack();
        if (player.isSneaking() && stack.isEmpty()) {
            if (world instanceof ServerWorld server && player instanceof ServerPlayerEntity serverPlayer) {
                if (slot == TITLE) {
                    openTitle(serverPlayer, server, master, facing);
                } else {
                    TombReservations.openAt(serverPlayer, server, partPos(master, facing, SLOTS[slot]));
                }
            }
            return ActionResult.success(world.isClient);
        }
        if (slot == TITLE) {
            return ActionResult.PASS;
        }
        BlockPos target = partPos(master, facing, SLOTS[slot]);
        if (!target.equals(pos) && (stack.getItem() instanceof ScytheItem || stack.getItem() instanceof StaffOfLifeItem)) {
            return stack.useOnBlock(new ItemUsageContext(player, hand, new BlockHitResult(hit.getPos(), hit.getSide(), target, hit.isInsideBlock())));
        }
        if (stack.isEmpty() && world instanceof ServerWorld server) {
            TombReservations.announce(player, server, target);
        }
        return ActionResult.PASS;
    }

    public static Optional<Data> titleHolder(BlockView world, BlockPos master, Direction facing) {
        return Data.of(world.getBlockEntity(partPos(master, facing, SLOTS[0])));
    }

    private static boolean canEdit(ServerPlayerEntity player, Data holder) {
        UUID owner = ((TombstoneDetails.Holder) holder).mcavie$vaultOwner();
        return owner == null || owner.equals(player.getUuid()) || player.hasPermissionLevel(2);
    }

    private static void openTitle(ServerPlayerEntity player, ServerWorld world, BlockPos master, Direction facing) {
        titleHolder(world, master, facing).ifPresent(holder -> {
            if (!canEdit(player, holder)) {
                player.sendMessage(Text.translatable("tomb.mca_vie.vault.not_owner").formatted(Formatting.RED), true);
                return;
            }
            String title = ((TombstoneDetails.Holder) holder).mcavie$vaultTitle();
            PacketByteBuf out = PacketByteBufs.create();
            out.writeBlockPos(holder.getPos());
            out.writeString(title == null ? "" : title);
            ServerPlayNetworking.send(player, TITLE_OPEN, out);
        });
    }

    private static void setTitle(ServerPlayerEntity player, BlockPos pos, String title) {
        ServerWorld world = player.getServerWorld();
        BlockState state = world.getBlockState(pos);
        if (player.squaredDistanceTo(pos.toCenterPos()) > 64 || !(state.getBlock() instanceof VaultBlock) || state.get(PART) != SLOTS[0]) {
            return;
        }
        Data.of(world.getBlockEntity(pos)).filter(holder -> canEdit(player, holder)).ifPresent(holder -> {
            String clean = title.strip();
            if (clean.length() > TITLE_MAX) {
                clean = clean.substring(0, TITLE_MAX);
            }
            ((TombstoneDetails.Holder) holder).mcavie$setVaultTitle(clean.isEmpty() ? null : clean);
            holder.markDirty();
            world.updateListeners(pos, state, state, Block.NOTIFY_ALL);
        });
    }
}
