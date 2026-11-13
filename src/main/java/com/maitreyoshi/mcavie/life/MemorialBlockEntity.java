package com.maitreyoshi.mcavie.life;

import com.maitreyoshi.mcavie.compat.Nbt;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MemorialBlockEntity extends BlockEntity {
    public static final int MAX_SYNCED = 200;
    public static final int PER_MONUMENT = 20;

    private int village = -1;
    @Nullable
    private String title;
    @Nullable
    private String dates;
    @Nullable
    private UUID owner;
    private String villageName = "";
    private int first;
    private final List<MemorialRegistry.Fallen> fallen = new ArrayList<>();

    public MemorialBlockEntity(BlockPos pos, BlockState state) {
        super(TombstoneBlocks.MEMORIAL_ENTITY, pos, state);
    }

    public int village() {
        return village;
    }

    public void setVillage(int village) {
        this.village = village;
        setChanged();
    }

    @Nullable
    public String title() {
        return title;
    }

    @Nullable
    public String dates() {
        return dates;
    }

    public void setTexts(@Nullable String title, @Nullable String dates) {
        this.title = title;
        this.dates = dates;
        setChanged();
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    public String villageName() {
        return villageName;
    }

    public int first() {
        return first;
    }

    public List<MemorialRegistry.Fallen> fallen() {
        return fallen;
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.putInt("village", village);
        if (title != null) {
            nbt.putString("title", title);
        }
        if (dates != null) {
            nbt.putString("dates", dates);
        }
        if (owner != null) {
            Nbt.putUUID(nbt, "owner", owner);
        }
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        village = nbt.contains("village") ? nbt.getIntOr("village", 0) : -1;
        title = nbt.contains("title") ? nbt.getStringOr("title", "") : null;
        dates = nbt.contains("dates") ? nbt.getStringOr("dates", "") : null;
        owner = Nbt.hasUUID(nbt, "owner") ? Nbt.getUUID(nbt, "owner") : null;
        first = nbt.getIntOr("first", 0);
        if (nbt.contains("village_name")) {
            villageName = nbt.getStringOr("village_name", "");
        }
        if (nbt.contains("fallen")) {
            fallen.clear();
            for (Tag element : nbt.getListOrEmpty("fallen")) {
                MemorialRegistry.Fallen entry = MemorialRegistry.Fallen.read((CompoundTag) element);
                if (entry != null) {
                    fallen.add(entry);
                }
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag nbt = saveWithoutMetadata();
        if (level instanceof ServerLevel server && village >= 0) {
            VillageManager.get(server).getOrEmpty(village).ifPresent(v -> nbt.putString("village_name", v.getName()));
            MemorialRegistry registry = MemorialRegistry.get(server.getServer());
            List<MemorialRegistry.Fallen> all = registry.fallen(village, server, worldPosition, PER_MONUMENT);
            int start = Math.max(0, all.size() - MAX_SYNCED);
            ListTag list = new ListTag();
            for (int i = start; i < all.size(); i++) {
                list.add(all.get(i).write());
            }
            nbt.put("fallen", list);
            nbt.putInt("first", -start);
        }
        return nbt;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
