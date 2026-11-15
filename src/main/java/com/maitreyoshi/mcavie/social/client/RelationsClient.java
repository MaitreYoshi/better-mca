package com.maitreyoshi.mcavie.social.client;

import com.maitreyoshi.mcavie.social.Social;
import com.maitreyoshi.mcavie.social.SocialNetwork;
import com.maitreyoshi.mcavie.compat.client.ClientPlayConnectionEvents;
import com.maitreyoshi.mcavie.compat.client.ClientPlayNetworking;
import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RelationsClient {
    private static final Map<UUID, Relations> CACHE = new HashMap<>();

    public record Entry(UUID uuid, String name, boolean female, int score, long lastSeen, int tag, boolean openable) {
    }

    public record Relations(long today, List<Entry> entries) {
    }

    private RelationsClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SocialNetwork.RELATIONS, (client, handler, buf, responseSender) -> {
            UUID owner = buf.readUuid();
            long today = buf.readVarLong();
            int count = buf.readVarInt();
            List<Entry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                entries.add(new Entry(buf.readUuid(), buf.readString(), buf.readBoolean(), buf.readVarInt(), buf.readVarLong(), buf.readVarInt(), buf.readBoolean()));
            }
            entries.sort(Comparator.comparingInt((Entry e) -> e.score() >= 0 ? 0 : 1)
                    .thenComparingInt(e -> e.score() >= 0 ? -e.score() : e.score())
                    .thenComparing(Entry::name));
            Relations relations = new Relations(today, List.copyOf(entries));
            client.execute(() -> CACHE.put(owner, relations));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CACHE.clear());
    }

    public static void request(UUID villager) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(villager);
        ClientPlayNetworking.send(SocialNetwork.RELATIONS_REQUEST, buf);
    }

    @Nullable
    public static Relations get(UUID villager) {
        return CACHE.get(villager);
    }

    @Nullable
    public static Entry bestFriend(UUID villager) {
        Relations relations = CACHE.get(villager);
        if (relations == null || relations.entries().isEmpty()) {
            return null;
        }
        Entry first = relations.entries().get(0);
        return first.score() >= Social.FRIEND ? first : null;
    }
}
