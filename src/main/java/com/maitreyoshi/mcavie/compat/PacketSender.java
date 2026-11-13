package com.maitreyoshi.mcavie.compat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class PacketSender {
    private final ServerPlayer player;

    PacketSender(ServerPlayer player) {
        this.player = player;
    }

    public void sendPacket(ResourceLocation id, FriendlyByteBuf buf) {
        ServerPlayNetworking.send(player, id, buf);
    }
}
