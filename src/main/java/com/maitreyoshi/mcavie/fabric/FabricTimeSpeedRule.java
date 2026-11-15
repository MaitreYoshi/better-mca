package com.maitreyoshi.mcavie.fabric;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.calendar.TimeSpeed;
import com.maitreyoshi.mcavie.calendar.TimeSpeedRule;
import net.fabricmc.fabric.api.gamerule.v1.CustomGameRuleCategory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.fabricmc.fabric.api.gamerule.v1.rule.EnumRule;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.world.GameRules;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

final class FabricTimeSpeedRule implements TimeSpeedRule {
    private GameRules.Key<EnumRule<TimeSpeed>> key;

    @Override
    public void register(TimeSpeed defaultValue, BiConsumer<MinecraftServer, TimeSpeed> onChanged) {
        key = GameRuleRegistry.register(NAME,
                new CustomGameRuleCategory(MCAVie.id("calendar"), Text.translatable("gamerule.category.mca_vie")),
                GameRuleFactory.createEnumRule(defaultValue, (server, rule) -> onChanged.accept(server, rule.get())));
    }

    @Override
    public TimeSpeed get(GameRules rules) {
        return rules.get(key).get();
    }

    @Override
    public void set(GameRules rules, TimeSpeed value, @Nullable MinecraftServer server) {
        rules.get(key).set(value, server);
    }
}
