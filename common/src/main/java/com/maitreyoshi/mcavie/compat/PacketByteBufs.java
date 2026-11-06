package com.maitreyoshi.mcavie.compat;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;

public final class PacketByteBufs {
    private PacketByteBufs() {
    }

    public static PacketByteBuf create() {
        return new PacketByteBuf(Unpooled.buffer());
    }

    public static PacketByteBuf empty() {
        return new PacketByteBuf(Unpooled.EMPTY_BUFFER);
    }
}
