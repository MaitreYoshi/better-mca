package com.maitreyoshi.mcavie.life;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.phone.PortraitRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.UUID;

public final class TombstonePortraits {
    public static final Identifier REQUEST = MCAVie.id("tombstone_portrait_request");
    public static final Identifier RESULT = MCAVie.id("tombstone_portrait_result");

    private TombstonePortraits() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(REQUEST, (server, player, handler, buf, responseSender) -> {
            UUID person = buf.readUuid();
            server.execute(() -> {
                UUID image = PortraitRegistry.get(server).get(person);
                PacketByteBuf out = PacketByteBufs.create();
                out.writeUuid(person);
                out.writeBoolean(image != null);
                if (image != null) {
                    out.writeUuid(image);
                }
                ServerPlayNetworking.send(player, RESULT, out);
            });
        });
    }
}
