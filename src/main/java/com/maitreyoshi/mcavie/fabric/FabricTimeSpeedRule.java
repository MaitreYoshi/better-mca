package com.maitreyoshi.mcavie.fabric;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.calendar.TimeSpeed;
import com.maitreyoshi.mcavie.calendar.TimeSpeedRule;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRules;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

final class FabricTimeSpeedRule implements TimeSpeedRule {
    private static final GameRuleCategory CATEGORY = GameRuleCategory.register(MCAVie.id("calendar"));
    private GameRule<TimeSpeed> rule;

    @Override
    public void register(TimeSpeed defaultValue, BiConsumer<MinecraftServer, TimeSpeed> onChanged) {
        rule = GameRuleBuilder.forEnum(defaultValue).category(CATEGORY).buildAndRegister(MCAVie.id(ID));
        GameRuleEvents.changeCallback(rule).register((value, server) -> onChanged.accept(server, value));
    }

    @Override
    public TimeSpeed get(GameRules rules) {
        return rules.get(rule);
    }

    @Override
    public void set(GameRules rules, TimeSpeed value, @Nullable MinecraftServer server) {
        rules.set(rule, value, server);
    }
}
