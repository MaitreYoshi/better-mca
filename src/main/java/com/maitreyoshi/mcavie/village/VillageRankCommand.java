package com.maitreyoshi.mcavie.village;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageManager;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** /villagerank <hamlet|village|town|city|metropolis> : fixe le rang du village le plus proche (op). */
public final class VillageRankCommand {
    private VillageRankCommand() {
    }

    public static void register() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                Commands.literal("villagerank").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("rank", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(VillageRank.values()).map(r -> r.name().toLowerCase(Locale.ROOT)), builder))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String name = StringArgumentType.getString(ctx, "rank").toUpperCase(Locale.ROOT);
                                    VillageRank rank = Arrays.stream(VillageRank.values()).filter(r -> r.name().equals(name)).findFirst().orElse(null);
                                    if (rank == null) {
                                        ctx.getSource().sendFailure(Component.translatable("command.mca_vie.villagerank.unknown", name.toLowerCase(Locale.ROOT)));
                                        return 0;
                                    }
                                    Optional<Village> village = VillageManager.get(player.serverLevel()).findNearestVillage(player);
                                    if (village.isEmpty()) {
                                        ctx.getSource().sendFailure(Component.translatable("command.mca_vie.villagerank.no_village"));
                                        return 0;
                                    }
                                    VillageState state = VillageState.of(village.get());
                                    state.rank = rank;
                                    state.descentDaysLeft = -1;
                                    village.get().markDirty();
                                    ctx.getSource().sendSuccess(() -> Component.translatable("command.mca_vie.villagerank.set", village.get().getName(), rank.getName()), true);
                                    return 1;
                                })));
            dispatcher.register(Commands.literal("villagemarket").requires(s -> s.hasPermission(2))
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        Optional<Village> village = VillageManager.get(player.serverLevel()).findNearestVillage(player);
                        if (village.isEmpty()) {
                            ctx.getSource().sendFailure(Component.translatable("command.mca_vie.villagerank.no_village"));
                            return 0;
                        }
                        VillageMarket.devHold(village.get(), player.serverLevel());
                        ctx.getSource().sendSuccess(() -> Component.translatable("command.mca_vie.villagemarket.done", village.get().getName()), true);
                        return 1;
                    }));
        });
    }
}
