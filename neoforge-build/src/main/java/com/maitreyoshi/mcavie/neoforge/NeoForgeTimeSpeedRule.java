package com.maitreyoshi.mcavie.neoforge;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.calendar.TimeSpeed;
import com.maitreyoshi.mcavie.calendar.TimeSpeedRule;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameRuleCategoryEvent;
import net.neoforged.neoforge.event.level.GameRuleChangedEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

/**
 * Règle de jeu {@code mca_vie:time_speed} sur NeoForge. Les règles 26.x n'acceptent que des entiers ou
 * des booléens : la valeur est l'indice de {@link TimeSpeed} (0 = jour, 1 = semaine, 2 = mois,
 * 3 = saison, 4 = année).
 */
final class NeoForgeTimeSpeedRule implements TimeSpeedRule {
    private static final TimeSpeed[] VALUES = TimeSpeed.values();
    private final IEventBus modBus;
    private final GameRuleCategory category = new GameRuleCategory(MCAVie.id("calendar"));
    private GameRule<Integer> rule;

    NeoForgeTimeSpeedRule(IEventBus modBus) {
        this.modBus = modBus;
    }

    @Override
    public void register(TimeSpeed defaultValue, BiConsumer<MinecraftServer, TimeSpeed> onChanged) {
        modBus.addListener(RegisterGameRuleCategoryEvent.class, event -> event.register(category));
        rule = new GameRule<>(category, GameRuleType.INT, IntegerArgumentType.integer(0, VALUES.length - 1),
                (visitor, r) -> visitor.visitInteger(r), Codec.intRange(0, VALUES.length - 1), value -> value,
                defaultValue.ordinal(), FeatureFlagSet.of());
        DeferredRegister<GameRule<?>> register = DeferredRegister.create(Registries.GAME_RULE, MCAVie.MOD_ID);
        register.register(ID, () -> rule);
        register.register(modBus);
        NeoForge.EVENT_BUS.addListener(GameRuleChangedEvent.class,
                event -> event.runIfMatching(rule, value -> onChanged.accept(event.getServer(), VALUES[value])));
    }

    @Override
    public TimeSpeed get(GameRules rules) {
        return VALUES[rules.get(rule)];
    }

    @Override
    public void set(GameRules rules, TimeSpeed value, @Nullable MinecraftServer server) {
        rules.set(rule, value.ordinal(), server);
    }
}