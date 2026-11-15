package com.maitreyoshi.mcavie.forge;

import com.maitreyoshi.mcavie.calendar.TimeSpeed;
import com.maitreyoshi.mcavie.calendar.TimeSpeedRule;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.world.GameRules;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

final class ForgeTimeSpeedRule implements TimeSpeedRule {
    private GameRules.Key<Rule> key;

    @Override
    public void register(TimeSpeed defaultValue, BiConsumer<MinecraftServer, TimeSpeed> onChanged) {
        GameRules.Type<Rule> type = new GameRules.Type<>(StringArgumentType::word, t -> new Rule(t, defaultValue),
                (server, rule) -> onChanged.accept(server, rule.value), (visitor, k, t) -> {
        });
        key = GameRules.register(NAME, GameRules.Category.MISC, type);
    }

    @Override
    public TimeSpeed get(GameRules rules) {
        return rules.get(key).value;
    }

    @Override
    public void set(GameRules rules, TimeSpeed value, @Nullable MinecraftServer server) {
        Rule rule = rules.get(key);
        rule.value = value;
        rule.notifyChanged(server);
    }

    static final class Rule extends GameRules.Rule<Rule> {
        private TimeSpeed value;

        Rule(GameRules.Type<Rule> type, TimeSpeed value) {
            super(type);
            this.value = value;
        }

        @Nullable
        private static TimeSpeed parse(String text) {
            for (TimeSpeed speed : TimeSpeed.values()) {
                if (speed.name().equalsIgnoreCase(text.trim())) {
                    return speed;
                }
            }
            return null;
        }

        void notifyChanged(@Nullable MinecraftServer server) {
            changed(server);
        }

        @Override
        protected void setFromArgument(CommandContext<ServerCommandSource> context, String name) {
            TimeSpeed parsed = parse(StringArgumentType.getString(context, name));
            if (parsed != null) {
                value = parsed;
            }
        }

        @Override
        protected void deserialize(String text) {
            TimeSpeed parsed = parse(text);
            if (parsed != null) {
                value = parsed;
            }
        }

        @Override
        public String serialize() {
            return value.name();
        }

        @Override
        public int getCommandResult() {
            return value.ordinal();
        }

        @Override
        protected Rule getThis() {
            return this;
        }

        @Override
        protected Rule copy() {
            return new Rule(type, value);
        }

        @Override
        public void setValue(Rule other, @Nullable MinecraftServer server) {
            value = other.value;
            changed(server);
        }
    }
}
