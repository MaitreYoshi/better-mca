package com.maitreyoshi.mcavie.life;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

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
        markDirty();
    }

    public static void tick(World world, BlockPos pos, BlockState state, GhostAltarBlockEntity altar) {
        if (!(world instanceof ServerWorld server) || (world.getTime() + pos.asLong()) % 40 != 0 || altar.person == null) {
            return;
        }
        Entity current = altar.ghost == null ? null : server.getEntity(altar.ghost);
        if (current != null && current.isAlive() || !Ghosts.isNight(world) || !Ghosts.canReturn(server.getServer(), altar.person)) {
            return;
        }
        NbtCompound look = Ghosts.get(server.getServer()).look(altar.person);
        Direction facing = state.get(Properties.HORIZONTAL_FACING);
        BlockPos spawn = pos.offset(facing);
        GhostEntity ghost = new GhostEntity(TombstoneBlocks.GHOST, world);
        ghost.setup(look, altar.person, pos);
        ghost.refreshPositionAndAngles(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, facing.asRotation(), 0);
        if (server.spawnEntity(ghost)) {
            altar.ghost = ghost.getUuid();
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        if (person != null) {
            nbt.putUuid("person", person);
        }
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        person = nbt.containsUuid("person") ? nbt.getUuid("person") : null;
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}
