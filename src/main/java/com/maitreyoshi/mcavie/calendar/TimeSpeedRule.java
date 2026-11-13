package com.maitreyoshi.mcavie.calendar;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRules;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

public interface TimeSpeedRule {
    String NAME = "mcaVieTimeSpeed";
    /** Identifiant 26.x de la règle (mca_vie:time_speed). */
    String ID = "time_speed";

    void register(TimeSpeed defaultValue, BiConsumer<MinecraftServer, TimeSpeed> onChanged);

    TimeSpeed get(GameRules rules);

    void set(GameRules rules, TimeSpeed value, @Nullable MinecraftServer server);
}
