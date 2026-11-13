package com.maitreyoshi.mcavie.compat;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

public final class PacketByteBufs {
    private PacketByteBufs() {
    }

    public static FriendlyByteBuf create() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    public static FriendlyByteBuf empty() {
        return new FriendlyByteBuf(Unpooled.EMPTY_BUFFER);
    }
}
