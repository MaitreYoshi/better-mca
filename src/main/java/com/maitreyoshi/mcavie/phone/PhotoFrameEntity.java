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
    public static final int MAX_SIZE = 64;
    private static final TrackedData<Optional<UUID>> PHOTO = DataTracker.registerData(PhotoFrameEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<Integer> WIDTH = DataTracker.registerData(PhotoFrameEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> HEIGHT = DataTracker.registerData(PhotoFrameEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> TOP = DataTracker.registerData(PhotoFrameEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private String author = "";
    private long date;

    public PhotoFrameEntity(EntityType<? extends PhotoFrameEntity> type, World world) {
        super(type, world);
    }

    public PhotoFrameEntity(World world, BlockPos pos, Direction facing, Direction top, int width, int height, @Nullable UUID photo, String author, long date) {
        super(PhoneServer.PHOTO_FRAME, world, pos);
        this.dataTracker.set(PHOTO, Optional.ofNullable(photo));
        this.dataTracker.set(WIDTH, clamp(width));
        this.dataTracker.set(HEIGHT, clamp(height));
        this.dataTracker.set(TOP, top.getAxis().isHorizontal() ? top.getHorizontal() : 0);
        this.author = author;
        this.date = date;
        setFacing(facing);
    }

    public static boolean fits(World world, BlockPos pos, Direction facing) {
        return supported(world, pos, facing);
    }

    private static boolean supported(World world, BlockPos pos, Direction facing) {
        BlockState state = world.getBlockState(pos.offset(facing.getOpposite()));
        return state.isSolid() || facing.getAxis().isHorizontal() && AbstractRedstoneGateBlock.isRedstoneGate(state);
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
        this.dataTracker.startTracking(WIDTH, 4);
        this.dataTracker.startTracking(HEIGHT, 2);
        this.dataTracker.startTracking(TOP, 0);
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        if ((WIDTH.equals(data) || HEIGHT.equals(data) || TOP.equals(data)) && this.facing != null && this.attachmentPos != null) {
            updateAttachmentPosition();
        }
        super.onTrackedDataSet(data);
    }

    @Nullable
    public UUID photo() {
        return this.dataTracker.get(PHOTO).orElse(null);
    }

    public int widthHalves() {
        return this.dataTracker.get(WIDTH);
    }

    public int heightHalves() {
        return this.dataTracker.get(HEIGHT);
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
        return 8 * widthHalves();
    }

    @Override
    public int getHeightPixels() {
        return 8 * heightHalves();
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

    @Override
    protected void updateAttachmentPosition() {
        if (this.facing == null || this.attachmentPos == null) {
            return;
        }
        Direction right = right();
        Direction up = top();
        double x = this.attachmentPos.getX() + 0.5 - this.facing.getOffsetX() * 0.46875;
        double y = this.attachmentPos.getY() + 0.5 - this.facing.getOffsetY() * 0.46875;
        double z = this.attachmentPos.getZ() + 0.5 - this.facing.getOffsetZ() * 0.46875;
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
        return supported(getWorld(), this.attachmentPos, this.facing);
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

    public void resize(int width, int height) {
        if (clamp(width) == widthHalves() && clamp(height) == heightHalves()) {
            return;
        }
        this.dataTracker.set(WIDTH, clamp(width));
        this.dataTracker.set(HEIGHT, clamp(height));
        playSound(SoundEvents.ENTITY_ITEM_FRAME_ROTATE_ITEM, 1.0f, 1.0f);
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
        nbt.putInt("w", widthHalves());
        nbt.putInt("h", heightHalves());
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
        if (nbt.contains("w")) {
            this.dataTracker.set(WIDTH, clamp(nbt.getInt("w")));
            this.dataTracker.set(HEIGHT, clamp(nbt.getInt("h")));
        } else {
            int size = Math.max(1, nbt.getInt("size"));
            this.dataTracker.set(WIDTH, clamp(2 * size));
            this.dataTracker.set(HEIGHT, clamp(Math.round(size * 9f / 8f)));
        }
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
