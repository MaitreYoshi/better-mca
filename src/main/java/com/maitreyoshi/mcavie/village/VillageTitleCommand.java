package com.maitreyoshi.mcavie.village;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.mca.resources.Rank;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageManager;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * /villagetitle (opérateurs) : donne à un joueur un titre fixe (Paysan… Monarque) dans le village le plus proche de lui,
 * à la place du titre calculé par MCA. « reset » rend le calcul normal.
 */
public final class VillageTitleCommand {
    private VillageTitleCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("villagetitle").requires(s -> s.hasPermissionLevel(2))
                        .then(CommandManager.literal("set")
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .then(CommandManager.argument("title", StringArgumentType.word())
                                                .suggests((ctx, builder) -> CommandSource.suggestMatching(
                                                        Arrays.stream(Rank.values()).map(r -> r.name().toLowerCase(Locale.ROOT)), builder))
                                                .executes(VillageTitleCommand::set))))
                        .then(CommandManager.literal("reset")
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(VillageTitleCommand::reset)))
                        .then(CommandManager.literal("get")
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(VillageTitleCommand::get)))));
    }

    private static Optional<Village> village(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity player) {
        Optional<Village> village = VillageManager.get(player.getServerWorld()).findNearestVillage(player);
        if (village.isEmpty()) {
            ctx.getSource().sendError(Text.translatable("command.mca_vie.title.no_village", player.getDisplayName()));
        }
        return village;
    }

    private static Text titleName(Rank rank) {
        return Text.translatable(rank.getTranslationKey());
    }

    private static int set(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
        String raw = StringArgumentType.getString(ctx, "title");
        Rank rank = Arrays.stream(Rank.values()).filter(r -> r.name().equalsIgnoreCase(raw)).findFirst().orElse(null);
        if (rank == null) {
            ctx.getSource().sendError(Text.translatable("command.mca_vie.title.unknown", raw));
            return 0;
        }
        Optional<Village> village = village(ctx, player);
        if (village.isEmpty()) {
            return 0;
        }
        VillageState.of(village.get()).forcedTitles.put(player.getUuid(), rank);
        village.get().markDirty();
        ctx.getSource().sendFeedback(() -> Text.translatable("command.mca_vie.title.set", player.getDisplayName(), titleName(rank), village.get().getName()), true);
        return 1;
    }

    private static int reset(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
        Optional<Village> village = village(ctx, player);
        if (village.isEmpty()) {
            return 0;
        }
        VillageState.of(village.get()).forcedTitles.remove(player.getUuid());
        village.get().markDirty();
        ctx.getSource().sendFeedback(() -> Text.translatable("command.mca_vie.title.reset", player.getDisplayName(), village.get().getName()), true);
        return 1;
    }

    private static int get(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
        Optional<Village> village = village(ctx, player);
        if (village.isEmpty()) {
            return 0;
        }
        Rank forced = VillageState.of(village.get()).forcedTitles.get(player.getUuid());
        Rank current = VillageRights.personalRank(village.get(), player);
        ctx.getSource().sendFeedback(() -> Text.translatable(forced != null ? "command.mca_vie.title.get_forced" : "command.mca_vie.title.get",
                player.getDisplayName(), titleName(current), village.get().getName()), false);
        return 1;
    }
}
