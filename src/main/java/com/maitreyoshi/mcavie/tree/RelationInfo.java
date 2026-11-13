package com.maitreyoshi.mcavie.tree;

import net.minecraft.network.FriendlyByteBuf;

public record RelationInfo(String code, int count) {
    public static final RelationInfo NONE = new RelationInfo("none", 0);

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(code);
        buf.writeVarInt(count);
    }

    public static RelationInfo read(FriendlyByteBuf buf) {
        return new RelationInfo(buf.readUtf(), buf.readVarInt());
    }
}
