package com.maitreyoshi.mcavie.village;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.mca.resources.Rank;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public final class VillageTitleCommand {
    private VillageTitleCommand() {
    }

    public static void register() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("villagetitle").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("title", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                        Arrays.stream(Rank.values()).map(r -> r.name().toLowerCase(Locale.ROOT)), builder))
                                                .executes(VillageTitleCommand::set))))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(VillageTitleCommand::reset)))
                        .then(Commands.literal("get")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(VillageTitleCommand::get)))));
    }

    private static Optional<Village> village(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        Optional<Village> village = VillageManager.get(player.serverLevel()).findNearestVillage(player);
        if (village.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("command.mca_vie.title.no_village", player.getDisplayName()));
        }
        return village;
    }

    private static Component titleName(Rank rank) {
        return Component.translatable(rank.getTranslationKey());
    }

    private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String raw = StringArgumentType.getString(ctx, "title");
        Rank rank = Arrays.stream(Rank.values()).filter(r -> r.name().equalsIgnoreCase(raw)).findFirst().orElse(null);
        if (rank == null) {
            ctx.getSource().sendFailure(Component.translatable("command.mca_vie.title.unknown", raw));
            return 0;
        }
        Optional<Village> village = village(ctx, player);
        if (village.isEmpty()) {
            return 0;
        }
        VillageState.of(village.get()).forcedTitles.put(player.getUUID(), rank);
        village.get().markDirty();
        ctx.getSource().sendSuccess(() -> Component.translatable("command.mca_vie.title.set", player.getDisplayName(), titleName(rank), village.get().getName()), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        Optional<Village> village = village(ctx, player);
        if (village.isEmpty()) {
            return 0;
        }
        VillageState.of(village.get()).forcedTitles.remove(player.getUUID());
        village.get().markDirty();
        ctx.getSource().sendSuccess(() -> Component.translatable("command.mca_vie.title.reset", player.getDisplayName(), village.get().getName()), true);
        return 1;
    }

    private static int get(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        Optional<Village> village = village(ctx, player);
        if (village.isEmpty()) {
            return 0;
        }
        Rank forced = VillageState.of(village.get()).forcedTitles.get(player.getUUID());
        Rank current = VillageRights.personalRank(village.get(), player);
        ctx.getSource().sendSuccess(() -> Component.translatable(forced != null ? "command.mca_vie.title.get_forced" : "command.mca_vie.title.get",
                player.getDisplayName(), titleName(current), village.get().getName()), false);
        return 1;
    }
}
