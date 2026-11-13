package com.maitreyoshi.mcavie.phone.client;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;
import com.maitreyoshi.mcavie.phone.client.PhoneClient.Person;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

public final class PhoneSkins {
    private static final Map<UUID, ResourceLocation> CACHE = new HashMap<>();
    private static final Map<UUID, String> SOURCES = new HashMap<>();

    private PhoneSkins() {
    }

    @Nullable
    public static ResourceLocation head(@Nullable Person person) {
        if (person == null || person.uuid() == null) {
            return null;
        }
        UUID uuid = person.uuid();
        Minecraft client = Minecraft.getInstance();
        PlayerInfo entry = client.getConnection() == null ? null : client.getConnection().getPlayerInfo(uuid);
        if (entry != null && entry.isSkinLoaded()) {
            return entry.getSkinLocation();
        }
        String source = person.skin() + "|" + person.name();
        if (!source.equals(SOURCES.get(uuid))) {
            SOURCES.put(uuid, source);
            CACHE.put(uuid, DefaultPlayerSkin.getDefaultSkin(uuid));
            GameProfile profile = new GameProfile(uuid, person.name().isEmpty() ? null : person.name());
            if (!person.skin().isEmpty()) {
                profile.getProperties().put("textures", new Property("textures", person.skin()));
                CACHE.put(uuid, client.getSkinManager().getInsecureSkinLocation(profile));
            } else if (!person.name().isEmpty()) {
                client.getSkinManager().registerSkins(profile, (type, id, texture) -> {
                    if (type == MinecraftProfileTexture.Type.SKIN) {
                        CACHE.put(uuid, id);
                    }
                }, false);
            }
        }
        return CACHE.get(uuid);
    }

    public static void clear() {
        CACHE.clear();
        SOURCES.clear();
    }
}
