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
import net.minecraft.resource.ResourceType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MCAVie {
    public static final String MOD_ID = "mca_vie";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    private MCAVie() {
    }

    public static void init() {
        ReloadListenerRegistry.register(ResourceType.SERVER_DATA, new SurnameLists(), SurnameLists.ID);
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
                CommandManager.literal("surname")
                        // Pas de requires() : sinon « fill » serait pris comme nom de famille par un non-opérateur.
                        .then(CommandManager.literal("fill")
                                .executes(ctx -> {
                                    if (!ctx.getSource().hasPermissionLevel(2)) {
                                        ctx.getSource().sendError(Text.translatable("command.mca_vie.fill_denied"));
                                        return 0;
                                    }
                                    int filled = Surnames.fillMissing(ctx.getSource().getServer().getOverworld());
                                    ctx.getSource().sendFeedback(() -> Text.translatable("command.mca_vie.filled", filled), true);
                                    return filled;
                                }))
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            String surname = SurnameRegistry.get(ctx.getSource().getServer()).get(player.getUuid());
                            if (surname == null) {
                                ctx.getSource().sendFeedback(() -> Text.translatable("command.mca_vie.none"), false);
                            } else {
                                ctx.getSource().sendFeedback(() -> Text.translatable("command.mca_vie.current", surname), false);
                            }
                            return 1;
                        })
                        .then(CommandManager.argument("nom", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                    String surname = SurnameText.sanitize(StringArgumentType.getString(ctx, "nom"));
                                    if (surname.isEmpty()) {
                                        return 0;
                                    }
                                    Surnames.setPlayerSurname(player, surname, true);
                                    ctx.getSource().sendFeedback(() -> Text.translatable("command.mca_vie.set", surname), false);
                                    return 1;
                                }))
        ));
    }
}
