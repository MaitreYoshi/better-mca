package com.maitreyoshi.mcavie.phone;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class PhotoFrameEntity extends HangingEntity {
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 64;
    private static final EntityDataAccessor<Optional<UUID>> PHOTO = SynchedEntityData.defineId(PhotoFrameEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> WIDTH = SynchedEntityData.defineId(PhotoFrameEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HEIGHT = SynchedEntityData.defineId(PhotoFrameEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TOP = SynchedEntityData.defineId(PhotoFrameEntity.class, EntityDataSerializers.INT);

    private String author = "";
    private long date;

    public PhotoFrameEntity(EntityType<? extends PhotoFrameEntity> type, Level world) {
        super(type, world);
    }

    public PhotoFrameEntity(Level world, BlockPos pos, Direction facing, Direction top, int width, int height, @Nullable UUID photo, String author, long date) {
        super(PhoneServer.PHOTO_FRAME, world, pos);
        this.entityData.set(PHOTO, Optional.ofNullable(photo));
        this.entityData.set(WIDTH, clamp(width));
        this.entityData.set(HEIGHT, clamp(height));
        this.entityData.set(TOP, top.getAxis().isHorizontal() ? top.get2DDataValue() : 0);
        this.author = author;
        this.date = date;
        setDirection(facing);
    }

    public static boolean fits(Level world, BlockPos pos, Direction facing) {
        return supported(world, pos, facing);
    }

    private static boolean supported(Level world, BlockPos pos, Direction facing) {
        BlockState state = world.getBlockState(pos.relative(facing.getOpposite()));
        return state.isSolid() || facing.getAxis().isHorizontal() && DiodeBlock.isDiode(state);
    }

    public static Direction topFor(Direction facing, Direction look) {
        return facing == Direction.DOWN ? look.getOpposite() : look;
    }

    public static int clamp(int size) {
        return Math.max(MIN_SIZE, Math.min(MAX_SIZE, size));
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(PHOTO, Optional.empty());
        this.entityData.define(WIDTH, 4);
        this.entityData.define(HEIGHT, 2);
        this.entityData.define(TOP, 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        if ((WIDTH.equals(data) || HEIGHT.equals(data) || TOP.equals(data)) && this.direction != null && this.pos != null) {
            recalculateBoundingBox();
        }
        super.onSyncedDataUpdated(data);
    }

    @Nullable
    public UUID photo() {
        return this.entityData.get(PHOTO).orElse(null);
    }

    public int widthHalves() {
        return this.entityData.get(WIDTH);
    }

    public int heightHalves() {
        return this.entityData.get(HEIGHT);
    }

    public Direction facing() {
        return this.direction;
    }

    public Direction top() {
        return this.direction.getAxis().isHorizontal() ? Direction.UP : Direction.from2DDataValue(this.entityData.get(TOP));
    }

    private Direction right() {
        return this.direction.getAxis().isHorizontal() ? this.direction.getCounterClockWise() : top().getCounterClockWise();
    }

    @Override
    public int getWidth() {
        return 8 * widthHalves();
    }

    @Override
    public int getHeight() {
        return 8 * heightHalves();
    }

    @Override
    protected void setDirection(Direction facing) {
        this.direction = facing;
        if (facing.getAxis().isHorizontal()) {
            setXRot(0.0f);
            setYRot(facing.get2DDataValue() * 90);
        } else {
            setXRot(-90 * facing.getAxisDirection().getStep());
            setYRot(0.0f);
        }
        this.xRotO = getXRot();
        this.yRotO = getYRot();
        recalculateBoundingBox();
    }

    @Override
    protected void recalculateBoundingBox() {
        if (this.direction == null || this.pos == null) {
            return;
        }
        Direction right = right();
        Direction up = top();
        double x = this.pos.getX() + 0.5 - this.direction.getStepX() * 0.46875;
        double y = this.pos.getY() + 0.5 - this.direction.getStepY() * 0.46875;
        double z = this.pos.getZ() + 0.5 - this.direction.getStepZ() * 0.46875;
        setPosRaw(x, y, z);
        double hw = getWidth() / 32.0;
        double hh = getHeight() / 32.0;
        double depth = 1.0 / 32.0;
        double ex = Math.abs(right.getStepX()) * hw + Math.abs(up.getStepX()) * hh + Math.abs(this.direction.getStepX()) * depth;
        double ey = Math.abs(right.getStepY()) * hw + Math.abs(up.getStepY()) * hh + Math.abs(this.direction.getStepY()) * depth;
        double ez = Math.abs(right.getStepZ()) * hw + Math.abs(up.getStepZ()) * hh + Math.abs(this.direction.getStepZ()) * depth;
        setBoundingBox(new AABB(x - ex, y - ey, z - ez, x + ex, y + ey, z + ez));
    }

    @Override
    public boolean survives() {
        return supported(level(), this.pos, this.direction);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                PhoneServer.openResize(serverPlayer, this);
            } else {
                UUID photo = photo();
                if (photo != null) {
                    PhoneServer.viewPrint(serverPlayer, photo);
                }
            }
        }
        return InteractionResult.CONSUME;
    }

    public void resize(int width, int height) {
        if (clamp(width) == widthHalves() && clamp(height) == heightHalves()) {
            return;
        }
        this.entityData.set(WIDTH, clamp(width));
        this.entityData.set(HEIGHT, clamp(height));
        playSound(SoundEvents.ITEM_FRAME_ROTATE_ITEM, 1.0f, 1.0f);
    }

    @Override
    public void dropItem(@Nullable Entity breaker) {
        if (!level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            return;
        }
        playSound(SoundEvents.PAINTING_BREAK, 1.0f, 1.0f);
        if (breaker instanceof Player player && player.getAbilities().instabuild) {
            return;
        }
        spawnAtLocation(item());
    }

    @Override
    public void playPlacementSound() {
        playSound(SoundEvents.PAINTING_PLACE, 1.0f, 1.0f);
    }

    private ItemStack item() {
        UUID photo = photo();
        return photo == null ? ItemStack.EMPTY : PhotoItem.create(photo, author, date);
    }

    @Override
    public ItemStack getPickResult() {
        return item();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        UUID photo = photo();
        if (photo != null) {
            nbt.putUUID("photo", photo);
        }
        nbt.putInt("w", widthHalves());
        nbt.putInt("h", heightHalves());
        nbt.putString("author", author);
        nbt.putLong("date", date);
        nbt.putByte("dir", (byte) this.direction.get3DDataValue());
        nbt.putByte("top", this.entityData.get(TOP).byteValue());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        if (nbt.hasUUID("photo")) {
            this.entityData.set(PHOTO, Optional.of(nbt.getUUID("photo")));
        }
        if (nbt.contains("w")) {
            this.entityData.set(WIDTH, clamp(nbt.getInt("w")));
            this.entityData.set(HEIGHT, clamp(nbt.getInt("h")));
        } else {
            int size = Math.max(1, nbt.getInt("size"));
            this.entityData.set(WIDTH, clamp(2 * size));
            this.entityData.set(HEIGHT, clamp(Math.round(size * 9f / 8f)));
        }
        this.entityData.set(TOP, nbt.getByte("top") & 3);
        this.author = nbt.getString("author");
        this.date = nbt.getLong("date");
        super.readAdditionalSaveData(nbt);
        setDirection(nbt.contains("dir") ? Direction.from3DDataValue(nbt.getByte("dir")) : Direction.from2DDataValue(nbt.getByte("facing")));
    }

    @Override
    public void moveTo(double x, double y, double z, float yaw, float pitch) {
        setPos(x, y, z);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int interpolationSteps, boolean interpolate) {
        setPos(x, y, z);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this, this.direction.get3DDataValue(), getPos());
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        setDirection(Direction.from3DDataValue(packet.getData()));
    }
}
