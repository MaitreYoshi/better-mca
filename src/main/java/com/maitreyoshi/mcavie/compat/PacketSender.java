package com.maitreyoshi.mcavie.compat;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class PacketSender {
    private final ServerPlayerEntity player;

    PacketSender(ServerPlayerEntity player) {
        this.player = player;
    }

    public void sendPacket(Identifier id, PacketByteBuf buf) {
        ServerPlayNetworking.send(player, id, buf);
    }
}
