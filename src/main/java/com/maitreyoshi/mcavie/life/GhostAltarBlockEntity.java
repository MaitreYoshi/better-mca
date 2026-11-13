package com.maitreyoshi.mcavie.life;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
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
        if (current != null && current.isAlive() || !Ghosts.isNight(world)) {
            return;
        }
        if (!Ghosts.canReturn(server.getServer(), altar.person)) {
            Ghosts.debug("autel : retour impossible (apparence " + (Ghosts.get(server.getServer()).look(altar.person) != null)
                    + ", photo " + (com.maitreyoshi.mcavie.phone.PortraitRegistry.get(server.getServer()).get(altar.person) != null)
                    + ", mort dans l'arbre " + net.conczin.mca.server.world.data.FamilyTree.get(server).getOrEmpty(altar.person).map(net.conczin.mca.server.world.data.FamilyTreeNode::isDeceased).orElse(false) + ")");
            return;
        }
        CompoundTag look = Ghosts.get(server.getServer()).look(altar.person);
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        BlockPos spawn = pos.relative(facing);
        GhostEntity ghost = new GhostEntity(TombstoneBlocks.GHOST, world);
        ghost.setup(look, altar.person, pos);
        ghost.snapTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, facing.toYRot(), 0);
        if (server.addFreshEntity(ghost)) {
            altar.ghost = ghost.getUUID();
            Ghosts.debug("autel : fantôme apparu (" + look.size() + " clés, " + look.toString().length() + " caractères)");
        } else {
            Ghosts.debug("autel : apparition refusée par le monde");
        }
    }

    @Override
    protected void saveAdditional(ValueOutput mcavieOutput) {
        CompoundTag nbt = Nbt.out(mcavieOutput);
        super.saveAdditional(mcavieOutput);
        if (person != null) {
            Nbt.putUUID(nbt, "person", person);
        }
    }

    @Override
    protected void loadAdditional(ValueInput mcavieInput) {
        CompoundTag nbt = Nbt.in(mcavieInput);
        super.loadAdditional(mcavieInput);
        person = Nbt.hasUUID(nbt, "person") ? Nbt.getUUID(nbt, "person") : null;
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
