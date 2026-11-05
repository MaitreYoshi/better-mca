package com.maitreyoshi.mcavie.village;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageManager;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** /villagerank <hamlet|village|town|city|metropolis> : fixe le rang du village le plus proche (op). */
public final class VillageRankCommand {
    private VillageRankCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                CommandManager.literal("villagerank").requires(s -> s.hasPermissionLevel(2))
                        .then(CommandManager.argument("rank", StringArgumentType.word())
                                .suggests((ctx, builder) -> CommandSource.suggestMatching(
                                        Arrays.stream(VillageRank.values()).map(r -> r.name().toLowerCase(Locale.ROOT)), builder))
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                    String name = StringArgumentType.getString(ctx, "rank").toUpperCase(Locale.ROOT);
                                    VillageRank rank = Arrays.stream(VillageRank.values()).filter(r -> r.name().equals(name)).findFirst().orElse(null);
                                    if (rank == null) {
                                        ctx.getSource().sendError(Text.literal("Rang inconnu : " + name.toLowerCase(Locale.ROOT)));
                                        return 0;
                                    }
                                    Optional<Village> village = VillageManager.get(player.getServerWorld()).findNearestVillage(player);
                                    if (village.isEmpty()) {
                                        ctx.getSource().sendError(Text.literal("Aucun village à proximité"));
                                        return 0;
                                    }
                                    VillageState state = VillageState.of(village.get());
                                    state.rank = rank;
                                    state.descentDaysLeft = -1;
                                    village.get().markDirty();
                                    ctx.getSource().sendFeedback(() -> Text.literal(village.get().getName() + " : rang ").append(rank.getName()), true);
                                    return 1;
                                })));
            dispatcher.register(CommandManager.literal("villagemarket").requires(s -> s.hasPermissionLevel(2))
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        Optional<Village> village = VillageManager.get(player.getServerWorld()).findNearestVillage(player);
                        if (village.isEmpty()) {
                            ctx.getSource().sendError(Text.literal("Aucun village à proximité"));
                            return 0;
                        }
                        VillageMarket.devHold(village.get(), player.getServerWorld());
                        ctx.getSource().sendFeedback(() -> Text.literal(village.get().getName() + " : vente des étals effectuée"), true);
                        return 1;
                    }));
        });
    }
}
