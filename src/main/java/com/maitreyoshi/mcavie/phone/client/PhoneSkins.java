package com.maitreyoshi.mcavie.phone.client;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.maitreyoshi.mcavie.phone.client.PhoneClient.Person;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

public final class PhoneSkins {
    private static final Map<UUID, Supplier<PlayerSkin>> CACHE = new HashMap<>();
    private static final Map<UUID, String> SOURCES = new HashMap<>();

    private PhoneSkins() {
    }

    @Nullable
    public static Identifier head(@Nullable Person person) {
        if (person == null || person.uuid() == null) {
            return null;
        }
        UUID uuid = person.uuid();
        Minecraft client = Minecraft.getInstance();
        PlayerInfo entry = client.getConnection() == null ? null : client.getConnection().getPlayerInfo(uuid);
        if (entry != null) {
            return entry.getSkin().body().texturePath();
        }
        String source = person.skin() + "|" + person.name();
        if (!source.equals(SOURCES.get(uuid))) {
            SOURCES.put(uuid, source);
            PlayerSkin fallback = DefaultPlayerSkin.get(uuid);
            CACHE.put(uuid, () -> fallback);
            if (!person.skin().isEmpty() || !person.name().isEmpty()) {
                PropertyMap properties = person.skin().isEmpty() ? PropertyMap.EMPTY
                        : new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", person.skin())));
                GameProfile profile = new GameProfile(uuid, person.name(), properties);
                CACHE.put(uuid, client.getSkinManager().createLookup(profile, false));
            }
        }
        return CACHE.get(uuid).get().body().texturePath();
    }

    public static void clear() {
        CACHE.clear();
        SOURCES.clear();
    }
}
