package com.maitreyoshi.mcavie.life;

import com.maitreyoshi.mcavie.compat.Nbt;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class GhostAltarBlockEntity extends BlockEntity {
    @Nullable
    private UUID person;
    @Nullable
    private UUID ghost;

    public GhostAltarBlockEntity(BlockPos pos, BlockState state) {
        super(TombstoneBlocks.GHOST_ALTAR_ENTITY, pos, state);
    }

    @Nullable
    public UUID person() {
        return person;
    }

    public void setPerson(@Nullable UUID person) {
        this.person = person;
        setChanged();
    }

    public static void tick(Level world, BlockPos pos, BlockState state, GhostAltarBlockEntity altar) {
        if (!(world instanceof ServerLevel server) || (world.getGameTime() + pos.asLong()) % 40 != 0 || altar.person == null) {
            return;
        }
        Entity current = altar.ghost == null ? null : server.getEntity(altar.ghost);
        if (current != null && current.isAlive() || !Ghosts.isNight(world) || !Ghosts.canReturn(server.getServer(), altar.person)) {
            return;
        }
        CompoundTag look = Ghosts.get(server.getServer()).look(altar.person);
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        BlockPos spawn = pos.relative(facing);
        GhostEntity ghost = new GhostEntity(TombstoneBlocks.GHOST, world);
        ghost.setup(look, altar.person, pos);
        ghost.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, facing.toYRot(), 0);
        if (server.addFreshEntity(ghost)) {
            altar.ghost = ghost.getUUID();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        if (person != null) {
            Nbt.putUUID(nbt, "person", person);
        }
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        person = Nbt.hasUUID(nbt, "person") ? Nbt.getUUID(nbt, "person") : null;
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
