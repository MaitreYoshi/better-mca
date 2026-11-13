package com.maitreyoshi.mcavie.tree.server;

import com.maitreyoshi.mcavie.tree.server.mixin.FamilyTreeAccessor;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Fusionne deux fiches de l'arbre qui désignent la même personne : la première disparaît au profit de la seconde.
public final class TreeMergeCommand {
    private TreeMergeCommand() {
    }

    public static void register() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("treemerge").requires(s -> s.hasPermissionLevel(2))
                        .then(CommandManager.literal("list")
                                .then(CommandManager.argument("name", StringArgumentType.greedyString())
                                        .executes(TreeMergeCommand::list)))
                        .then(CommandManager.argument("remove", StringArgumentType.string())
                                .then(CommandManager.argument("keep", StringArgumentType.string())
                                        .executes(TreeMergeCommand::merge)))));
    }

    private static int list(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        MinecraftServer server = source.getServer();
        FamilyGraph graph = new FamilyGraph(server, FamilyTree.get(server.getOverworld()));
        String name = StringArgumentType.getString(ctx, "name");
        String part = clean(name);
        List<FamilyTreeNode> found = new ArrayList<>();
        for (FamilyTreeNode node : graph.all()) {
            if ((" " + clean(graph.fullName(node)) + " ").contains(" " + part + " ")) {
                found.add(node);
            }
        }
        if (found.isEmpty()) {
            source.sendError(Text.translatable("command.mca_vie.treemerge.unknown", name));
            return 0;
        }
        for (FamilyTreeNode node : found) {
            String kind = !node.isPlayer() ? "villager" : server.getPlayerManager().getPlayer(node.id()) != null ? "online" : "player";
            List<String> names = new ArrayList<>();
            graph.spouses(node).forEach(id -> graph.node(id).map(graph::fullName).ifPresent(names::add));
            String spouse = names.isEmpty() ? "-" : String.join(", ", names);
            source.sendFeedback(() -> Text.translatable("command.mca_vie.treemerge.entry", shortId(node), graph.fullName(node),
                    Text.translatable("command.mca_vie.treemerge." + kind), node.children().size(), spouse), false);
        }
        return found.size();
    }

    // Les guillemets éventuels et les espaces multiples ne comptent pas.
    private static String clean(String text) {
        return FamilyGraph.normalize(text.replace("\"", "")).replaceAll("\\s+", " ");
    }

    private static String shortId(FamilyTreeNode node) {
        return node.id().toString().substring(0, 8);
    }

    private static List<FamilyTreeNode> named(FamilyGraph graph, String name) {
        String wanted = clean(name);
        List<FamilyTreeNode> found = new ArrayList<>();
        for (FamilyTreeNode node : graph.all()) {
            if (clean(graph.fullName(node)).equals(wanted)) {
                found.add(node);
            }
        }
        return found;
    }

    private static int merge(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        MinecraftServer server = source.getServer();
        FamilyTree tree = FamilyTree.get(server.getOverworld());
        FamilyGraph graph = new FamilyGraph(server, tree);
        FamilyTreeNode drop = find(source, graph, StringArgumentType.getString(ctx, "remove"));
        FamilyTreeNode keep = drop == null ? null : find(source, graph, StringArgumentType.getString(ctx, "keep"));
        if (drop == null || keep == null) {
            return 0;
        }
        String error = refusal(server, graph, drop, keep);
        if (error != null) {
            source.sendError(Text.translatable("command.mca_vie.treemerge." + error));
            return 0;
        }
        String dropName = graph.fullName(drop);
        String keepName = graph.fullName(keep);
        apply(server, tree, graph, drop, keep);
        source.sendFeedback(() -> Text.translatable("command.mca_vie.treemerge.done", dropName, keepName), true);
        return 1;
    }

    @Nullable
    private static FamilyTreeNode find(ServerCommandSource source, FamilyGraph graph, String name) {
        List<FamilyTreeNode> found = named(graph, name);
        if (found.isEmpty() && name.length() >= 8) {
            for (FamilyTreeNode node : graph.all()) {
                if (node.id().toString().startsWith(name.toLowerCase(java.util.Locale.ROOT))) {
                    found.add(node);
                }
            }
        }
        if (found.size() == 1) {
            return found.get(0);
        }
        source.sendError(found.isEmpty()
                ? Text.translatable("command.mca_vie.treemerge.unknown", name)
                : Text.translatable("command.mca_vie.treemerge.ambiguous", name, found.size()));
        return null;
    }

    @Nullable
    private static String refusal(MinecraftServer server, FamilyGraph graph, FamilyTreeNode drop, FamilyTreeNode keep) {
        if (drop.id().equals(keep.id()) || drop.gender() != keep.gender()) {
            return "invalid";
        }
        for (ServerWorld world : server.getWorlds()) {
            if (world.getEntity(drop.id()) != null) {
                return "alive";
            }
        }
        FamilyTreeNode dropSpouse = graph.node(drop.partner()).orElse(null);
        FamilyTreeNode keepSpouse = graph.node(keep.partner()).orElse(null);
        if (dropSpouse == null) {
            return null;
        }
        if (dropSpouse.id().equals(keep.id())) {
            return "spouses";
        }
        // Un couple en double (les deux époux ont chacun leur doublon) reste fusionnable, époux après époux.
        boolean sameSpouse = keepSpouse == null || keepSpouse.id().equals(dropSpouse.id())
                || FamilyGraph.normalize(graph.fullName(keepSpouse)).equals(FamilyGraph.normalize(graph.fullName(dropSpouse)));
        return sameSpouse ? null : "spouses";
    }

    private static void apply(MinecraftServer server, FamilyTree tree, FamilyGraph graph, FamilyTreeNode drop, FamilyTreeNode keep) {
        UUID dropId = drop.id();
        for (UUID childId : new ArrayList<>(drop.children())) {
            graph.node(childId).ifPresent(child -> {
                if (dropId.equals(child.father())) {
                    child.setFather(keep);
                }
                if (dropId.equals(child.mother())) {
                    child.setMother(keep);
                }
            });
        }
        graph.node(drop.father()).ifPresent(parent -> {
            parent.children().remove(dropId);
            if (!FamilyTreeNode.isValid(keep.father())) {
                keep.setFather(parent);
            }
        });
        graph.node(drop.mother()).ifPresent(parent -> {
            parent.children().remove(dropId);
            if (!FamilyTreeNode.isValid(keep.mother())) {
                keep.setMother(parent);
            }
        });
        if (!FamilyTreeNode.isValid(keep.partner()) || !graph.contains(keep.partner()) || keep.partner().equals(drop.partner())) {
            graph.node(drop.partner()).ifPresent(spouse -> {
                spouse.updatePartner(keep);
                keep.updatePartner(spouse);
            });
        }
        SpouseRegistry.flush(server);
        SpouseRegistry.get(server).replace(dropId, keep.id());
        ((FamilyTreeAccessor) tree).mcavie$getEntries().remove(dropId);
        tree.markDirty();
    }
}
