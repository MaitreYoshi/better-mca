package com.maitreyoshi.mcavie.phone;

import net.minecraft.block.AbstractRedstoneGateBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public class PhotoFrameEntity extends AbstractDecorationEntity {
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 8;
    private static final TrackedData<Optional<UUID>> PHOTO = DataTracker.registerData(PhotoFrameEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<Integer> SIZE = DataTracker.registerData(PhotoFrameEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> TOP = DataTracker.registerData(PhotoFrameEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private String author = "";
    private long date;
    @Nullable
    private Entity ignored;

    public PhotoFrameEntity(EntityType<? extends PhotoFrameEntity> type, World world) {
        super(type, world);
    }

    public PhotoFrameEntity(World world, BlockPos pos, Direction facing, Direction top, int size, @Nullable UUID photo, String author, long date) {
        super(PhoneServer.PHOTO_FRAME, world, pos);
        this.dataTracker.set(PHOTO, Optional.ofNullable(photo));
        this.dataTracker.set(SIZE, clamp(size));
        this.dataTracker.set(TOP, top.getAxis().isHorizontal() ? top.getHorizontal() : 0);
        this.author = author;
        this.date = date;
        setFacing(facing);
    }

    public static boolean fits(World world, BlockPos pos, Direction facing, Direction top, int size, @Nullable Entity ignored) {
        PhotoFrameEntity probe = new PhotoFrameEntity(world, pos, facing, top, size, null, "", 0);
        probe.ignored = ignored;
        return probe.canStayAttached();
    }

    public static Direction topFor(Direction facing, Direction look) {
        return facing == Direction.DOWN ? look.getOpposite() : look;
    }

    public static int clamp(int size) {
        return Math.max(MIN_SIZE, Math.min(MAX_SIZE, size));
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(PHOTO, Optional.empty());
        this.dataTracker.startTracking(SIZE, 2);
        this.dataTracker.startTracking(TOP, 0);
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        if ((SIZE.equals(data) || TOP.equals(data)) && this.facing != null && this.attachmentPos != null) {
            updateAttachmentPosition();
        }
        super.onTrackedDataSet(data);
    }

    @Nullable
    public UUID photo() {
        return this.dataTracker.get(PHOTO).orElse(null);
    }

    public int size() {
        return this.dataTracker.get(SIZE);
    }

    public Direction facing() {
        return this.facing;
    }

    public Direction top() {
        return this.facing.getAxis().isHorizontal() ? Direction.UP : Direction.fromHorizontal(this.dataTracker.get(TOP));
    }

    private Direction right() {
        return this.facing.getAxis().isHorizontal() ? this.facing.rotateYCounterclockwise() : top().rotateYCounterclockwise();
    }

    @Override
    public int getWidthPixels() {
        return 16 * size();
    }

    @Override
    public int getHeightPixels() {
        return Math.max(9, Math.round(size() * 9f));
    }

    @Override
    protected void setFacing(Direction facing) {
        this.facing = facing;
        if (facing.getAxis().isHorizontal()) {
            setPitch(0.0f);
            setYaw(facing.getHorizontal() * 90);
        } else {
            setPitch(-90 * facing.getDirection().offset());
            setYaw(0.0f);
        }
        this.prevPitch = getPitch();
        this.prevYaw = getYaw();
        updateAttachmentPosition();
    }

    private static int blocks(int pixels) {
        return Math.max(1, pixels / 16);
    }

    private static double offset(int pixels) {
        return blocks(pixels) % 2 == 0 ? 0.5 : 0.0;
    }

    @Override
    protected void updateAttachmentPosition() {
        if (this.facing == null || this.attachmentPos == null) {
            return;
        }
        Direction right = right();
        Direction up = top();
        double ow = offset(getWidthPixels());
        double oh = offset(getHeightPixels());
        double x = this.attachmentPos.getX() + 0.5 - this.facing.getOffsetX() * 0.46875 + right.getOffsetX() * ow + up.getOffsetX() * oh;
        double y = this.attachmentPos.getY() + 0.5 - this.facing.getOffsetY() * 0.46875 + right.getOffsetY() * ow + up.getOffsetY() * oh;
        double z = this.attachmentPos.getZ() + 0.5 - this.facing.getOffsetZ() * 0.46875 + right.getOffsetZ() * ow + up.getOffsetZ() * oh;
        setPos(x, y, z);
        double hw = getWidthPixels() / 32.0;
        double hh = getHeightPixels() / 32.0;
        double depth = 1.0 / 32.0;
        double ex = Math.abs(right.getOffsetX()) * hw + Math.abs(up.getOffsetX()) * hh + Math.abs(this.facing.getOffsetX()) * depth;
        double ey = Math.abs(right.getOffsetY()) * hw + Math.abs(up.getOffsetY()) * hh + Math.abs(this.facing.getOffsetY()) * depth;
        double ez = Math.abs(right.getOffsetZ()) * hw + Math.abs(up.getOffsetZ()) * hh + Math.abs(this.facing.getOffsetZ()) * depth;
        setBoundingBox(new Box(x - ex, y - ey, z - ez, x + ex, y + ey, z + ez));
    }

    @Override
    public boolean canStayAttached() {
        World world = getWorld();
        if (!world.isSpaceEmpty(this)) {
            return false;
        }
        int columns = blocks(getWidthPixels());
        int rows = blocks(getHeightPixels());
        Direction right = right();
        Direction up = top();
        BlockPos base = this.attachmentPos.offset(this.facing.getOpposite());
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int k = 0; k < columns; k++) {
            for (int l = 0; l < rows; l++) {
                mutable.set(base).move(right, k + (columns - 1) / -2).move(up, l + (rows - 1) / -2);
                BlockState state = world.getBlockState(mutable);
                if (!state.isSolid() && !(this.facing.getAxis().isHorizontal() && AbstractRedstoneGateBlock.isRedstoneGate(state))) {
                    return false;
                }
            }
        }
        return world.getOtherEntities(this, getBoundingBox(), entity -> entity != this.ignored && PREDICATE.test(entity)).isEmpty();
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (getWorld().isClient) {
            return ActionResult.SUCCESS;
        }
        if (player instanceof ServerPlayerEntity serverPlayer) {
            if (player.isSneaking()) {
                PhoneServer.openResize(serverPlayer, this);
            } else {
                UUID photo = photo();
                if (photo != null) {
                    PhoneServer.viewPrint(serverPlayer, photo);
                }
            }
        }
        return ActionResult.CONSUME;
    }

    public void resize(ServerPlayerEntity player, int size) {
        int original = size();
        int next = clamp(size);
        if (next == original) {
            return;
        }
        this.dataTracker.set(SIZE, next);
        updateAttachmentPosition();
        if (canStayAttached()) {
            playSound(SoundEvents.ENTITY_ITEM_FRAME_ROTATE_ITEM, 1.0f, 1.0f);
            return;
        }
        this.dataTracker.set(SIZE, original);
        updateAttachmentPosition();
        player.sendMessage(Text.translatable("msg.mca_vie.photo.no_room"), true);
    }

    @Override
    public void onBreak(@Nullable Entity breaker) {
        if (!getWorld().getGameRules().getBoolean(GameRules.DO_ENTITY_DROPS)) {
            return;
        }
        playSound(SoundEvents.ENTITY_PAINTING_BREAK, 1.0f, 1.0f);
        if (breaker instanceof PlayerEntity player && player.getAbilities().creativeMode) {
            return;
        }
        dropStack(item());
    }

    @Override
    public void onPlace() {
        playSound(SoundEvents.ENTITY_PAINTING_PLACE, 1.0f, 1.0f);
    }

    private ItemStack item() {
        UUID photo = photo();
        return photo == null ? ItemStack.EMPTY : PhotoItem.create(photo, author, date);
    }

    @Override
    public ItemStack getPickBlockStack() {
        return item();
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        UUID photo = photo();
        if (photo != null) {
            nbt.putUuid("photo", photo);
        }
        nbt.putInt("size", size());
        nbt.putString("author", author);
        nbt.putLong("date", date);
        nbt.putByte("dir", (byte) this.facing.getId());
        nbt.putByte("top", this.dataTracker.get(TOP).byteValue());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("photo")) {
            this.dataTracker.set(PHOTO, Optional.of(nbt.getUuid("photo")));
        }
        this.dataTracker.set(SIZE, clamp(nbt.getInt("size")));
        this.dataTracker.set(TOP, nbt.getByte("top") & 3);
        this.author = nbt.getString("author");
        this.date = nbt.getLong("date");
        super.readCustomDataFromNbt(nbt);
        setFacing(nbt.contains("dir") ? Direction.byId(nbt.getByte("dir")) : Direction.fromHorizontal(nbt.getByte("facing")));
    }

    @Override
    public void refreshPositionAndAngles(double x, double y, double z, float yaw, float pitch) {
        setPosition(x, y, z);
    }

    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int interpolationSteps, boolean interpolate) {
        setPosition(x, y, z);
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this, this.facing.getId(), getDecorationBlockPos());
    }

    @Override
    public void onSpawnPacket(EntitySpawnS2CPacket packet) {
        super.onSpawnPacket(packet);
        setFacing(Direction.byId(packet.getEntityData()));
    }
}
