package com.maitreyoshi.mcavie.phone;

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
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public class PhotoFrameEntity extends AbstractDecorationEntity {
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 4;
    private static final TrackedData<Optional<UUID>> PHOTO = DataTracker.registerData(PhotoFrameEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<Integer> SIZE = DataTracker.registerData(PhotoFrameEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private String author = "";
    private long date;

    public PhotoFrameEntity(EntityType<? extends PhotoFrameEntity> type, World world) {
        super(type, world);
    }

    public PhotoFrameEntity(World world, BlockPos pos, Direction facing, UUID photo, String author, long date) {
        super(PhoneServer.PHOTO_FRAME, world, pos);
        this.dataTracker.set(PHOTO, Optional.of(photo));
        this.author = author;
        this.date = date;
        setFacing(facing);
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(PHOTO, Optional.empty());
        this.dataTracker.startTracking(SIZE, 2);
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        if (SIZE.equals(data) && this.facing != null && this.attachmentPos != null) {
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

    @Override
    public int getWidthPixels() {
        return 16 * size();
    }

    @Override
    public int getHeightPixels() {
        return Math.max(9, Math.round(size() * 9f));
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (getWorld().isClient) {
            return ActionResult.SUCCESS;
        }
        if (!player.isSneaking()) {
            UUID photo = photo();
            if (photo != null && player instanceof ServerPlayerEntity serverPlayer) {
                PhoneServer.viewPrint(serverPlayer, photo);
            }
            return ActionResult.CONSUME;
        }
        int original = size();
        for (int i = 1; i <= MAX_SIZE; i++) {
            int next = (original - MIN_SIZE + i) % MAX_SIZE + MIN_SIZE;
            this.dataTracker.set(SIZE, next);
            updateAttachmentPosition();
            if (canStayAttached()) {
                playSound(SoundEvents.ENTITY_ITEM_FRAME_ROTATE_ITEM, 1.0f, 1.0f);
                return ActionResult.CONSUME;
            }
        }
        this.dataTracker.set(SIZE, original);
        updateAttachmentPosition();
        return ActionResult.CONSUME;
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
        nbt.putByte("facing", (byte) this.facing.getHorizontal());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("photo")) {
            this.dataTracker.set(PHOTO, Optional.of(nbt.getUuid("photo")));
        }
        this.dataTracker.set(SIZE, Math.max(MIN_SIZE, Math.min(MAX_SIZE, nbt.getInt("size"))));
        this.author = nbt.getString("author");
        this.date = nbt.getLong("date");
        super.readCustomDataFromNbt(nbt);
        setFacing(Direction.fromHorizontal(nbt.getByte("facing")));
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
