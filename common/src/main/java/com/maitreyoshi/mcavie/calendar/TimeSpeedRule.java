package com.maitreyoshi.mcavie.calendar;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.GameRules;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

public interface TimeSpeedRule {
    String NAME = "mcaVieTimeSpeed";

    void register(TimeSpeed defaultValue, BiConsumer<MinecraftServer, TimeSpeed> onChanged);

    TimeSpeed get(GameRules rules);

    void set(GameRules rules, TimeSpeed value, @Nullable MinecraftServer server);
}
