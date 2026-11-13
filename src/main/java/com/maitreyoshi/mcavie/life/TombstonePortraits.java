package com.maitreyoshi.mcavie.life;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.phone.PortraitRegistry;
import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import com.maitreyoshi.mcavie.compat.ServerPlayNetworking;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public final class TombstonePortraits {
    public static final ResourceLocation REQUEST = MCAVie.id("tombstone_portrait_request");
    public static final ResourceLocation RESULT = MCAVie.id("tombstone_portrait_result");

    private TombstonePortraits() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(REQUEST, (server, player, handler, buf, responseSender) -> {
            UUID person = buf.readUUID();
            server.execute(() -> {
                UUID image = PortraitRegistry.get(server).get(person);
                FriendlyByteBuf out = PacketByteBufs.create();
                out.writeUUID(person);
                out.writeBoolean(image != null);
                if (image != null) {
                    out.writeUUID(image);
                }
                ServerPlayNetworking.send(player, RESULT, out);
            });
        });
    }
}
