package com.maitreyoshi.mcavie.phone.client;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;
import com.maitreyoshi.mcavie.phone.client.PhoneClient.Person;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PhoneSkins {
    private static final Map<UUID, Identifier> CACHE = new HashMap<>();
    private static final Map<UUID, String> SOURCES = new HashMap<>();

    private PhoneSkins() {
    }

    @Nullable
    public static Identifier head(@Nullable Person person) {
        if (person == null || person.uuid() == null) {
            return null;
        }
        UUID uuid = person.uuid();
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerListEntry entry = client.getNetworkHandler() == null ? null : client.getNetworkHandler().getPlayerListEntry(uuid);
        if (entry != null && entry.hasSkinTexture()) {
            return entry.getSkinTexture();
        }
        String source = person.skin() + "|" + person.name();
        if (!source.equals(SOURCES.get(uuid))) {
            SOURCES.put(uuid, source);
            CACHE.put(uuid, DefaultSkinHelper.getTexture(uuid));
            GameProfile profile = new GameProfile(uuid, person.name().isEmpty() ? null : person.name());
            if (!person.skin().isEmpty()) {
                profile.getProperties().put("textures", new Property("textures", person.skin()));
                CACHE.put(uuid, client.getSkinProvider().loadSkin(profile));
            } else if (!person.name().isEmpty()) {
                client.getSkinProvider().loadSkin(profile, (type, id, texture) -> {
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
