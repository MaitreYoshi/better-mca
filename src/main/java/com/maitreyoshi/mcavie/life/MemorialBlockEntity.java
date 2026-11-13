package com.maitreyoshi.mcavie.life;

import net.mca.server.world.data.VillageManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
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
        markDirty();
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
        markDirty();
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        markDirty();
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
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("village", village);
        if (title != null) {
            nbt.putString("title", title);
        }
        if (dates != null) {
            nbt.putString("dates", dates);
        }
        if (owner != null) {
            nbt.putUuid("owner", owner);
        }
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        village = nbt.contains("village") ? nbt.getInt("village") : -1;
        title = nbt.contains("title") ? nbt.getString("title") : null;
        dates = nbt.contains("dates") ? nbt.getString("dates") : null;
        owner = nbt.containsUuid("owner") ? nbt.getUuid("owner") : null;
        first = nbt.getInt("first");
        if (nbt.contains("village_name")) {
            villageName = nbt.getString("village_name");
        }
        if (nbt.contains("fallen")) {
            fallen.clear();
            for (NbtElement element : nbt.getList("fallen", NbtElement.COMPOUND_TYPE)) {
                MemorialRegistry.Fallen entry = MemorialRegistry.Fallen.read((NbtCompound) element);
                if (entry != null) {
                    fallen.add(entry);
                }
            }
        }
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        NbtCompound nbt = createNbt();
        if (world instanceof ServerWorld server && village >= 0) {
            VillageManager.get(server).getOrEmpty(village).ifPresent(v -> nbt.putString("village_name", v.getName()));
            MemorialRegistry registry = MemorialRegistry.get(server.getServer());
            List<MemorialRegistry.Fallen> all = registry.fallen(village, server, pos, PER_MONUMENT);
            int start = Math.max(0, all.size() - MAX_SYNCED);
            NbtList list = new NbtList();
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
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}
