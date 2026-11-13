package com.maitreyoshi.mcavie.compat;

import com.maitreyoshi.mcavie.MCAVie;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Minecraft 26.x n'accepte plus que des paquets typés : tous nos anciens canaux passent par deux paquets
 * « bruts » (un par sens) qui portent l'identifiant du canal et les octets écrits comme avant.
 */
public record RawPayload(Type<RawPayload> type, Identifier channel, byte[] data) implements CustomPacketPayload {
    public static final Type<RawPayload> S2C = new Type<>(MCAVie.id("raw_s2c"));
    public static final Type<RawPayload> C2S = new Type<>(MCAVie.id("raw_c2s"));
    private static final int MAX = 8 * 1024 * 1024;

    static final Map<Identifier, BiConsumer<FriendlyByteBuf, NetworkManager.PacketContext>> SERVER = new ConcurrentHashMap<>();
    static final Map<Identifier, BiConsumer<FriendlyByteBuf, NetworkManager.PacketContext>> CLIENT = new ConcurrentHashMap<>();
    private static boolean serverReady;
    private static boolean clientReady;

    private static StreamCodec<RegistryFriendlyByteBuf, RawPayload> codec(Type<RawPayload> type) {
        return StreamCodec.composite(Identifier.STREAM_CODEC, RawPayload::channel, ByteBufCodecs.byteArray(MAX), RawPayload::data,
                (channel, data) -> new RawPayload(type, channel, data));
    }

    public static RawPayload of(Type<RawPayload> type, Identifier channel, FriendlyByteBuf buf) {
        byte[] data = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), data);
        return new RawPayload(type, channel, data);
    }

    public FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
    }

    /** Côté serveur (et solo) : reçoit C2S ; sur serveur dédié, déclare aussi le type S2C. */
    static synchronized void initServer() {
        if (serverReady) {
            return;
        }
        serverReady = true;
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, C2S, codec(C2S), RawPayload::dispatchServer);
        if (Platform.getEnvironment() == Env.SERVER) {
            NetworkManager.registerS2CPayloadType(S2C, codec(S2C));
        }
    }

    /** Côté client : reçoit S2C. */
    public static synchronized void initClient() {
        if (clientReady) {
            return;
        }
        clientReady = true;
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, S2C, codec(S2C), RawPayload::dispatchClient);
    }

    private static void dispatchServer(RawPayload payload, NetworkManager.PacketContext context) {
        BiConsumer<FriendlyByteBuf, NetworkManager.PacketContext> handler = SERVER.get(payload.channel());
        if (handler != null) {
            handler.accept(payload.buffer(), context);
        }
    }

    private static void dispatchClient(RawPayload payload, NetworkManager.PacketContext context) {
        BiConsumer<FriendlyByteBuf, NetworkManager.PacketContext> handler = CLIENT.get(payload.channel());
        if (handler != null) {
            handler.accept(payload.buffer(), context);
        }
    }

    public static void registerClient(Identifier channel, BiConsumer<FriendlyByteBuf, NetworkManager.PacketContext> handler) {
        initClient();
        CLIENT.put(channel, handler);
    }
}
