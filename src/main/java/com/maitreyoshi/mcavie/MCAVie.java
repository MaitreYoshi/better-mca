package com.maitreyoshi.mcavie;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.maitreyoshi.mcavie.phone.PhoneServer;
import com.maitreyoshi.mcavie.tree.server.TreeServer;
import com.maitreyoshi.mcavie.social.SocialConfig;
import com.maitreyoshi.mcavie.social.SocialNetwork;
import com.maitreyoshi.mcavie.village.VillageConfig;
import com.maitreyoshi.mcavie.village.VillageNetwork;
import com.maitreyoshi.mcavie.work.WorkNetwork;
import dev.architectury.platform.Platform;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.registry.ReloadListenerRegistry;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MCAVie {
    public static final String MOD_ID = "mca_vie";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    private MCAVie() {
    }

    public static void init() {
        ReloadListenerRegistry.register(PackType.SERVER_DATA, new SurnameLists(), SurnameLists.ID);
        com.maitreyoshi.mcavie.calendar.WorldCalendar.register();
        SurnameNetwork.registerServer();
        registerCommand();
        com.maitreyoshi.mcavie.village.VillageTitleCommand.register();
        com.maitreyoshi.mcavie.village.VillageRankCommand.register();
        com.maitreyoshi.mcavie.life.AgeCommand.register();
        com.maitreyoshi.mcavie.tree.server.TreeMergeCommand.register();
        TreeServer.register();
        com.maitreyoshi.mcavie.path.LadderPaths.register();
        com.maitreyoshi.mcavie.village.BedClaims.register();
        com.maitreyoshi.mcavie.path.HomeEntrance.register();
        com.maitreyoshi.mcavie.work.block.WorkBlocks.register();
        com.maitreyoshi.mcavie.work.JobProfessions.register();
        WorkNetwork.register();
        VillageConfig.load();
        SocialConfig.load();
        SocialNetwork.register();
        VillageNetwork.register();
        PhoneServer.register();
        com.maitreyoshi.mcavie.life.TombstonePortraits.register();
        com.maitreyoshi.mcavie.life.TombReservations.register();
        com.maitreyoshi.mcavie.life.TombstoneBlocks.register();
        com.maitreyoshi.mcavie.aging.Aging.register();
        com.maitreyoshi.mcavie.life.FamilyAges.register();
        com.maitreyoshi.mcavie.book.HappinessBook.register();
        com.maitreyoshi.mcavie.book.GuideBooks.register();
        com.maitreyoshi.mcavie.item.BetterMcaItemGroup.register();
        if (Platform.isDevelopmentEnvironment()) {
            DevTestCommand.register();
        }
    }

    private static void registerCommand() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("surname")
                        // Pas de requires() : sinon « fill » serait pris comme nom de famille par un non-opérateur.
                        .then(Commands.literal("fill")
                                .executes(ctx -> {
                                    if (!ctx.getSource().hasPermission(2)) {
                                        ctx.getSource().sendFailure(Component.translatable("command.mca_vie.fill_denied"));
                                        return 0;
                                    }
                                    int filled = Surnames.fillMissing(ctx.getSource().getServer().overworld());
                                    ctx.getSource().sendSuccess(() -> Component.translatable("command.mca_vie.filled", filled), true);
                                    return filled;
                                }))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String surname = SurnameRegistry.get(ctx.getSource().getServer()).get(player.getUUID());
                            if (surname == null) {
                                ctx.getSource().sendSuccess(() -> Component.translatable("command.mca_vie.none"), false);
                            } else {
                                ctx.getSource().sendSuccess(() -> Component.translatable("command.mca_vie.current", surname), false);
                            }
                            return 1;
                        })
                        .then(Commands.argument("nom", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String surname = SurnameText.sanitize(StringArgumentType.getString(ctx, "nom"));
                                    if (surname.isEmpty()) {
                                        return 0;
                                    }
                                    Surnames.setPlayerSurname(player, surname, true);
                                    ctx.getSource().sendSuccess(() -> Component.translatable("command.mca_vie.set", surname), false);
                                    return 1;
                                }))
        ));
    }
}
