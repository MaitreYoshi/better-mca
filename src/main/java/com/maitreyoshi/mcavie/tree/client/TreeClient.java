package com.maitreyoshi.mcavie.tree.client;

import com.maitreyoshi.mcavie.tree.TreeProtocol;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.PacketByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class TreeClient {
    private TreeClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(TreeProtocol.RESPONSE, (client, handler, buf, responseSender) -> {
            TreeProtocol.Response response = TreeProtocol.Response.read(buf);
            client.execute(() -> {
                if (client.currentScreen instanceof TreeScreen screen) {
                    screen.accept(response);
                }
            });
        });
    }

    public static void open(UUID root, @Nullable Screen parent) {
        MinecraftClient.getInstance().setScreen(new TreeScreen(root, parent));
    }

    public static void send(TreeProtocol.Request request) {
        if (!ClientPlayNetworking.canSend(TreeProtocol.REQUEST)) {
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        request.write(buf);
        ClientPlayNetworking.send(TreeProtocol.REQUEST, buf);
    }
}
