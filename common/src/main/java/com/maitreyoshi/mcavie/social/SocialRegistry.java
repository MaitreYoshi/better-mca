package com.maitreyoshi.mcavie.social;

import com.maitreyoshi.mcavie.MCAVie;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class SocialRegistry extends PersistentState {
    private static final String DATA_ID = "mca_vie_social";

    private final Map<UUID, Map<UUID, Link>> links = new HashMap<>();
    private final Map<UUID, Long> lastTalk = new HashMap<>();
    private long lastForgetDay = Long.MIN_VALUE / 2;

    public static SocialRegistry get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(SocialRegistry::fromNbt, SocialRegistry::new, DATA_ID);
    }

    private static SocialRegistry fromNbt(NbtCompound nbt) {
        SocialRegistry registry = new SocialRegistry();
        if (nbt.contains("lastForgetDay")) {
            registry.lastForgetDay = nbt.getLong("lastForgetDay");
        }
        NbtCompound owners = nbt.getCompound("villagers");
        for (String key : owners.getKeys()) {
            try {
                UUID owner = UUID.fromString(key);
                NbtCompound entries = owners.getCompound(key);
                Map<UUID, Link> map = new HashMap<>();
                for (String other : entries.getKeys()) {
                    map.put(UUID.fromString(other), Link.fromNbt(entries.getCompound(other)));
                }
                registry.links.put(owner, map);
            } catch (IllegalArgumentException e) {
                MCAVie.LOGGER.warn("UUID invalide dans le registre des relations : {}", key);
            }
        }
        return registry;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound owners = new NbtCompound();
        links.forEach((owner, map) -> {
            NbtCompound entries = new NbtCompound();
            map.forEach((other, link) -> entries.put(other.toString(), link.toNbt()));
            owners.put(owner.toString(), entries);
        });
        nbt.put("villagers", owners);
        nbt.putLong("lastForgetDay", lastForgetDay);
        return nbt;
    }

    public Map<UUID, Link> links(UUID owner) {
        return Collections.unmodifiableMap(links.getOrDefault(owner, Map.of()));
    }

    public Set<UUID> owners() {
        return Collections.unmodifiableSet(links.keySet());
    }

    public Link get(UUID owner, UUID other) {
        Map<UUID, Link> map = links.get(owner);
        return map == null ? null : map.get(other);
    }

    @Nullable
    public Link create(UUID owner, UUID other, int initialScore, int expectedScore, boolean family) {
        Map<UUID, Link> map = links.computeIfAbsent(owner, k -> new HashMap<>());
        if (map.size() >= Social.MAX_LINKS) {
            Optional<Map.Entry<UUID, Link>> weakest = map.entrySet().stream()
                    .filter(e -> !e.getValue().family)
                    .min(Comparator.comparingInt(e -> Math.abs(e.getValue().score)));
            if (!family && (weakest.isEmpty() || Math.abs(weakest.get().getValue().score) > Math.abs(expectedScore))) {
                return null;
            }
            if (weakest.isEmpty()) {
                weakest = map.entrySet().stream().min(Comparator.comparingInt(e -> Math.abs(e.getValue().score)));
            }
            weakest.map(Map.Entry::getKey).ifPresent(map::remove);
        }
        Link link = new Link();
        link.score = initialScore;
        link.family = family;
        link.talkDay = -1;
        link.hitDay = -1;
        map.put(other, link);
        markDirty();
        return link;
    }

    public void forgetUntil(long today, long time) {
        if (lastForgetDay >= today) {
            return;
        }
        long from = Math.max(lastForgetDay + 1, today - Social.MAX_CATCH_UP_DAYS + 1);
        lastForgetDay = today;
        for (long day = from; day <= today; day++) {
            forgetDay(day);
        }
        lastTalk.values().removeIf(t -> time - t > Social.TALK_COOLDOWN);
        markDirty();
    }

    private void forgetDay(long day) {
        for (Iterator<Map<UUID, Link>> owners = links.values().iterator(); owners.hasNext(); ) {
            Map<UUID, Link> map = owners.next();
            for (Iterator<Link> it = map.values().iterator(); it.hasNext(); ) {
                Link link = it.next();
                if (link.family || day - link.lastSeen < Social.FORGET_AFTER_DAYS) {
                    continue;
                }
                link.score -= Integer.signum(link.score);
                if (link.score == 0) {
                    it.remove();
                }
            }
            if (map.isEmpty()) {
                owners.remove();
            }
        }
    }

    public void remove(UUID dead) {
        links.remove(dead);
        lastTalk.remove(dead);
        links.values().forEach(map -> map.remove(dead));
        markDirty();
    }

    public long lastTalk(UUID uuid) {
        return lastTalk.getOrDefault(uuid, Long.MIN_VALUE / 2);
    }

    public void setLastTalk(UUID uuid, long time) {
        lastTalk.put(uuid, time);
    }

    public static final class Link {
        public int score;
        public long lastSeen;
        public boolean family;
        public long talkDay;
        public int talks;
        public long hitDay;

        NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putInt("score", score);
            nbt.putLong("lastSeen", lastSeen);
            nbt.putBoolean("family", family);
            nbt.putLong("talkDay", talkDay);
            nbt.putInt("talks", talks);
            nbt.putLong("hitDay", hitDay);
            return nbt;
        }

        static Link fromNbt(NbtCompound nbt) {
            Link link = new Link();
            link.score = nbt.getInt("score");
            link.lastSeen = nbt.getLong("lastSeen");
            link.family = nbt.getBoolean("family");
            link.talkDay = nbt.getLong("talkDay");
            link.talks = nbt.getInt("talks");
            link.hitDay = nbt.getLong("hitDay");
            return link;
        }
    }
}
