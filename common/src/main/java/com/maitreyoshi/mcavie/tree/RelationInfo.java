package com.maitreyoshi.mcavie.tree;

import net.minecraft.network.PacketByteBuf;

public record RelationInfo(String code, int count) {
    public static final RelationInfo NONE = new RelationInfo("none", 0);

    public void write(PacketByteBuf buf) {
        buf.writeString(code);
        buf.writeVarInt(count);
    }

    public static RelationInfo read(PacketByteBuf buf) {
        return new RelationInfo(buf.readString(), buf.readVarInt());
    }
}
