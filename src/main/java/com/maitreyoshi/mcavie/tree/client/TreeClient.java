package com.maitreyoshi.mcavie.tree.client;

import com.maitreyoshi.mcavie.tree.TreeProtocol;
import com.maitreyoshi.mcavie.compat.client.ClientPlayNetworking;
import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;

public final class TreeClient {
    private TreeClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(TreeProtocol.RESPONSE, (client, handler, buf, responseSender) -> {
            TreeProtocol.Response response = TreeProtocol.Response.read(buf);
            client.execute(() -> {
                if (client.gui.screen() instanceof TreeScreen screen) {
                    screen.accept(response);
                }
            });
        });
    }

    public static void open(UUID root, @Nullable Screen parent) {
        Minecraft.getInstance().gui.setScreen(new TreeScreen(root, parent));
    }

    public static void send(TreeProtocol.Request request) {
        if (!ClientPlayNetworking.canSend(TreeProtocol.REQUEST)) {
            return;
        }
        FriendlyByteBuf buf = PacketByteBufs.create();
        request.write(buf);
        ClientPlayNetworking.send(TreeProtocol.REQUEST, buf);
    }
}
