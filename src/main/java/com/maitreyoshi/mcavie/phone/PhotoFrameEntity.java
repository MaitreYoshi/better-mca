package com.maitreyoshi.mcavie.phone;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import com.maitreyoshi.mcavie.compat.Nbt;
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
    private static final EntityDataAccessor<String> PHOTO = SynchedEntityData.defineId(PhotoFrameEntity.class, EntityDataSerializers.STRING);
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
        this.entityData.set(PHOTO, photo == null ? "" : photo.toString());
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
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PHOTO, "");
        builder.define(WIDTH, 4);
        builder.define(HEIGHT, 2);
        builder.define(TOP, 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        if ((WIDTH.equals(data) || HEIGHT.equals(data) || TOP.equals(data)) && this.pos != null) {
            recalculateBoundingBox();
        }
        super.onSyncedDataUpdated(data);
    }

    @Nullable
    public UUID photo() {
        String id = this.entityData.get(PHOTO);
        return id.isEmpty() ? null : UUID.fromString(id);
    }

    public int widthHalves() {
        return this.entityData.get(WIDTH);
    }

    public int heightHalves() {
        return this.entityData.get(HEIGHT);
    }

    public Direction facing() {
        return getDirection();
    }

    public Direction top() {
        return getDirection().getAxis().isHorizontal() ? Direction.UP : Direction.from2DDataValue(this.entityData.get(TOP));
    }

    private Direction right() {
        return getDirection().getAxis().isHorizontal() ? getDirection().getCounterClockWise() : top().getCounterClockWise();
    }

    public int getWidth() {
        return 8 * widthHalves();
    }

    public int getHeight() {
        return 8 * heightHalves();
    }

    @Override
    protected void setDirection(Direction facing) {
        setDirectionRaw(facing);
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
        if (this.pos == null) {
            return;
        }
        Direction right = right();
        Direction up = top();
        double x = this.pos.getX() + 0.5 - getDirection().getStepX() * 0.46875;
        double y = this.pos.getY() + 0.5 - getDirection().getStepY() * 0.46875;
        double z = this.pos.getZ() + 0.5 - getDirection().getStepZ() * 0.46875;
        setPosRaw(x, y, z);
        double hw = getWidth() / 32.0;
        double hh = getHeight() / 32.0;
        double depth = 1.0 / 32.0;
        double ex = Math.abs(right.getStepX()) * hw + Math.abs(up.getStepX()) * hh + Math.abs(getDirection().getStepX()) * depth;
        double ey = Math.abs(right.getStepY()) * hw + Math.abs(up.getStepY()) * hh + Math.abs(getDirection().getStepY()) * depth;
        double ez = Math.abs(right.getStepZ()) * hw + Math.abs(up.getStepZ()) * hh + Math.abs(getDirection().getStepZ()) * depth;
        setBoundingBox(new AABB(x - ex, y - ey, z - ez, x + ex, y + ey, z + ez));
    }

    @Override
    protected AABB calculateBoundingBox(BlockPos pos, Direction facing) {
        recalculateBoundingBox();
        return getBoundingBox();
    }

    @Override
    public boolean survives() {
        return supported(level(), this.pos, getDirection());
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, net.minecraft.world.phys.Vec3 location) {
        if (level().isClientSide()) {
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
    public void dropItem(net.minecraft.server.level.ServerLevel level, @Nullable Entity breaker) {
        if (!level.getGameRules().get(GameRules.ENTITY_DROPS)) {
            return;
        }
        playSound(SoundEvents.PAINTING_BREAK, 1.0f, 1.0f);
        if (breaker instanceof Player player && player.getAbilities().instabuild) {
            return;
        }
        spawnAtLocation(level, item());
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
    public void addAdditionalSaveData(ValueOutput mcavieOutput) {
        CompoundTag nbt = Nbt.out(mcavieOutput);
        super.addAdditionalSaveData(mcavieOutput);
        UUID photo = photo();
        if (photo != null) {
            Nbt.putUUID(nbt, "photo", photo);
        }
        nbt.putInt("w", widthHalves());
        nbt.putInt("h", heightHalves());
        nbt.putString("author", author);
        nbt.putLong("date", date);
        nbt.putByte("dir", (byte) getDirection().get3DDataValue());
        nbt.putByte("top", this.entityData.get(TOP).byteValue());
    }

    @Override
    public void readAdditionalSaveData(ValueInput mcavieInput) {
        CompoundTag nbt = Nbt.in(mcavieInput);
        if (Nbt.hasUUID(nbt, "photo")) {
            this.entityData.set(PHOTO, Nbt.getUUID(nbt, "photo").toString());
        }
        if (nbt.contains("w")) {
            this.entityData.set(WIDTH, clamp(nbt.getIntOr("w", 0)));
            this.entityData.set(HEIGHT, clamp(nbt.getIntOr("h", 0)));
        } else {
            int size = Math.max(1, nbt.getIntOr("size", 0));
            this.entityData.set(WIDTH, clamp(2 * size));
            this.entityData.set(HEIGHT, clamp(Math.round(size * 9f / 8f)));
        }
        this.entityData.set(TOP, nbt.getByteOr("top", (byte) 0) & 3);
        this.author = nbt.getStringOr("author", "");
        this.date = nbt.getLongOr("date", 0L);
        super.readAdditionalSaveData(mcavieInput);
        setDirection(nbt.contains("dir") ? Direction.from3DDataValue(nbt.getByteOr("dir", (byte) 0)) : Direction.from2DDataValue(nbt.getByteOr("facing", (byte) 0)));
    }

    @Override
    public void snapTo(double x, double y, double z, float yaw, float pitch) {
        setPos(x, y, z);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(net.minecraft.server.level.ServerEntity entity) {
        return new ClientboundAddEntityPacket(this, getDirection().get3DDataValue(), getPos());
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        setDirection(Direction.from3DDataValue(packet.getData()));
    }
}
