package com.maitreyoshi.mcavie.life;

import net.minecraft.server.permissions.Permissions;
import com.maitreyoshi.mcavie.compat.Nbt;
import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import com.maitreyoshi.mcavie.compat.ServerPlayNetworking;
import net.conczin.mca.block.TombstoneBlock;
import net.conczin.mca.item.ScytheItem;
import net.conczin.mca.item.StaffOfLifeItem;
import net.conczin.mca.server.world.data.GraveyardManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class VaultBlock extends TombstoneBlock {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 11);
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

    public VaultBlock(Properties settings) {
        super(settings, 70, 30, Vec3.ZERO, 0, false, Shapes.block());
        registerDefaultState(defaultBlockState().setValue(PART, PLACED));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
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
            String title = buf.readUtf(TITLE_MAX * 4);
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
        return state.getBlock() instanceof VaultBlock && slotOf(state.getValue(PART)) >= 0;
    }

    public static BlockPos offset(Direction facing, int part) {
        Direction back = facing.getOpposite();
        Direction left = facing.getCounterClockWise();
        int x = cx(part);
        int z = cz(part);
        return new BlockPos(back.getStepX() * x + left.getStepX() * z, cy(part), back.getStepZ() * x + left.getStepZ() * z);
    }

    public static BlockPos master(BlockPos pos, BlockState state) {
        return pos.subtract(offset(state.getValue(BlockStateProperties.HORIZONTAL_FACING), state.getValue(PART)));
    }

    public static BlockPos partPos(BlockPos master, Direction facing, int part) {
        return master.offset(offset(facing, part));
    }

    public static Vec3 local(BlockPos master, Direction facing, Vec3 hit) {
        Direction back = facing.getOpposite();
        Direction left = facing.getCounterClockWise();
        double dx = hit.x - master.getX() - 0.5;
        double dz = hit.z - master.getZ() - 0.5;
        return new Vec3(8 + 16 * (dx * back.getStepX() + dz * back.getStepZ()), 16 * (hit.y - master.getY()),
                8 + 16 * (dx * left.getStepX() + dz * left.getStepZ()));
    }

    public static int slotAt(Vec3 local) {
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
        super.createBlockStateDefinition(builder);
        builder.add(PART);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter view, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(BlockStateProperties.HORIZONTAL_FACING))[state.getValue(PART)];
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        return true;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos master = context.getClickedPos().subtract(offset(facing, PLACED));
        Level world = context.getLevel();
        for (int part = 0; part < 12; part++) {
            BlockPos pos = partPos(master, facing, part);
            if (!world.isInWorldBounds(pos) || !(part == PLACED ? world.getBlockState(pos).canBeReplaced(context) : world.getBlockState(pos).canBeReplaced())) {
                return null;
            }
        }
        return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing).setValue(PART, PLACED);
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (world.isClientSide()) {
            return;
        }
        BlockPos master = master(pos, state);
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        for (int part = 0; part < 12; part++) {
            BlockPos target = partPos(master, facing, part);
            if (!target.equals(pos)) {
                world.setBlock(target, state.setValue(PART, part).setValue(BlockStateProperties.WATERLOGGED, false), Block.UPDATE_ALL);
            }
        }
        CompoundTag tag = Nbt.tag(stack);
        if (tag != null && tag.contains(SLOTS_KEY) && world instanceof ServerLevel server) {
            restore(server, master, facing, tag.getListOrEmpty(SLOTS_KEY));
        }
        if (placer instanceof Player player) {
            titleHolder(world, master, facing).ifPresent(data -> {
                TombstoneDetails.Holder holder = (TombstoneDetails.Holder) data;
                if (holder.mcavie$vaultOwner() == null) {
                    holder.mcavie$setVaultOwner(player.getUUID());
                    data.setChanged();
                }
            });
        }
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify) {
        if (isSlot(state)) {
            super.onPlace(state, world, pos, oldState, notify);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean moved) {
        super.affectNeighborsAfterRemoval(state, world, pos, moved);
        if (world.getBlockState(pos).is(this)) {
            return;
        }
        BlockPos master = master(pos, state);
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        for (int part = 0; part < 12; part++) {
            BlockPos target = partPos(master, facing, part);
            BlockState other = world.getBlockState(target);
            if (!target.equals(pos) && other.is(this) && other.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing && other.getValue(PART) == part) {
                world.setBlock(target, other.getValue(BlockStateProperties.WATERLOGGED) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isSlot(state) ? super.newBlockEntity(pos, state) : null;
    }

    public static boolean occupied(BlockGetter world, BlockPos master, Direction facing) {
        for (int part : SLOTS) {
            if (Data.of(world.getBlockEntity(partPos(master, facing, part))).map(Data::hasEntity).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    public static boolean silkTouch(Player player) {
        return EnchantmentHelper.getItemEnchantmentLevel(player.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), player.getMainHandItem()) > 0;
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter world, BlockPos pos) {
        return !silkTouch(player) && occupied(world, master(pos, state), state.getValue(BlockStateProperties.HORIZONTAL_FACING)) ? 0 : super.getDestroyProgress(state, player, world, pos);
    }

    @Override
    public void attack(BlockState state, Level world, BlockPos pos, Player player) {
        if (!world.isClientSide() && !silkTouch(player) && occupied(world, master(pos, state), state.getValue(BlockStateProperties.HORIZONTAL_FACING))) {
            player.sendOverlayMessage(Component.translatable("tomb.mca_vie.vault.occupied").withStyle(ChatFormatting.GOLD));
        }
    }

    @Override
    public BlockState playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        BlockPos master = master(pos, state);
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        boolean occupied = occupied(world, master, facing);
        if (!world.isClientSide() && (occupied ? silkTouch(player) : !player.isCreative() && player.hasCorrectToolForDrops(state))) {
            ItemStack stack = new ItemStack(this);
            if (occupied) {
                ListTag slots = new ListTag();
                java.util.List<Component> lore = new java.util.ArrayList<>();
                for (int part : SLOTS) {
                    Optional<Data> data = Data.of(world.getBlockEntity(partPos(master, facing, part)));
                    slots.add(data.map(be -> be.saveWithoutMetadata(world.registryAccess())).orElseGet(CompoundTag::new));
                    data.filter(Data::hasEntity).flatMap(Data::getEntityName)
                            .ifPresent(name -> lore.add(Component.literal(name).withStyle(ChatFormatting.GRAY)));
                }
                com.maitreyoshi.mcavie.compat.Nbt.update(stack, tag -> tag.put(SLOTS_KEY, slots));
                stack.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));
            }
            Block.popResource(world, pos, stack);
        }
        return super.playerWillDestroy(world, pos, state, player);
    }

    private static void restore(ServerLevel world, BlockPos master, Direction facing, ListTag slots) {
        for (int i = 0; i < SLOTS.length && i < slots.size(); i++) {
            BlockPos target = partPos(master, facing, SLOTS[i]);
            CompoundTag nbt = slots.getCompoundOrEmpty(i);
            Data.of(world.getBlockEntity(target)).ifPresent(data -> {
                data.loadWithComponents(com.maitreyoshi.mcavie.compat.Nbt.input(nbt, world.registryAccess()));
                data.setChanged();
                GraveyardManager.get(world).setTombstoneState(target,
                        data.hasEntity() ? GraveyardManager.TombstoneState.FILLED : GraveyardManager.TombstoneState.EMPTY);
                world.sendBlockUpdated(target, data.getBlockState(), data.getBlockState(), Block.UPDATE_ALL);
            });
        }
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
        BlockPos master = master(pos, state);
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        int slot = slotAt(local(master, facing, hit.getLocation()));
        if (slot == NONE) {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getMainHandItem();
        if (player.isShiftKeyDown() && stack.isEmpty()) {
            if (world instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer) {
                if (slot == TITLE) {
                    openTitle(serverPlayer, server, master, facing);
                } else {
                    TombReservations.openAt(serverPlayer, server, partPos(master, facing, SLOTS[slot]));
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (slot == TITLE) {
            return InteractionResult.PASS;
        }
        BlockPos target = partPos(master, facing, SLOTS[slot]);
        if (!target.equals(pos) && (stack.getItem() instanceof ScytheItem || stack.getItem() instanceof StaffOfLifeItem)) {
            return stack.useOn(new UseOnContext(player, hand, new BlockHitResult(hit.getLocation(), hit.getDirection(), target, hit.isInside())));
        }
        if (stack.isEmpty() && world instanceof ServerLevel server) {
            TombReservations.announce(player, server, target);
        }
        return InteractionResult.PASS;
    }

    public static Optional<Data> titleHolder(BlockGetter world, BlockPos master, Direction facing) {
        return Data.of(world.getBlockEntity(partPos(master, facing, SLOTS[0])));
    }

    private static boolean canEdit(ServerPlayer player, Data holder) {
        UUID owner = ((TombstoneDetails.Holder) holder).mcavie$vaultOwner();
        return owner == null || owner.equals(player.getUUID()) || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    private static void openTitle(ServerPlayer player, ServerLevel world, BlockPos master, Direction facing) {
        titleHolder(world, master, facing).ifPresent(holder -> {
            if (!canEdit(player, holder)) {
                player.sendOverlayMessage(Component.translatable("tomb.mca_vie.vault.not_owner").withStyle(ChatFormatting.RED));
                return;
            }
            String title = ((TombstoneDetails.Holder) holder).mcavie$vaultTitle();
            FriendlyByteBuf out = PacketByteBufs.create();
            out.writeBlockPos(holder.getBlockPos());
            out.writeUtf(title == null ? "" : title);
            ServerPlayNetworking.send(player, TITLE_OPEN, out);
        });
    }

    private static void setTitle(ServerPlayer player, BlockPos pos, String title) {
        ServerLevel world = player.level();
        BlockState state = world.getBlockState(pos);
        if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > 64 || !(state.getBlock() instanceof VaultBlock) || state.getValue(PART) != SLOTS[0]) {
            return;
        }
        Data.of(world.getBlockEntity(pos)).filter(holder -> canEdit(player, holder)).ifPresent(holder -> {
            String clean = title.strip();
            if (clean.length() > TITLE_MAX) {
                clean = clean.substring(0, TITLE_MAX);
            }
            ((TombstoneDetails.Holder) holder).mcavie$setVaultTitle(clean.isEmpty() ? null : clean);
            holder.setChanged();
            world.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        });
    }
}
