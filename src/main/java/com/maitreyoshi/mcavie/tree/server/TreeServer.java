package com.maitreyoshi.mcavie.tree.server;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.tree.FamilyPerson;
import com.maitreyoshi.mcavie.tree.RelationInfo;
import com.maitreyoshi.mcavie.tree.TreeProtocol;
import com.maitreyoshi.mcavie.tree.TreeSelection;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TreeServer {
    private static final int MAX_PERSONS = 400;
    private static final int MAX_SEARCH_RESULTS = 50;
    private static final int MAX_LINKS = Integer.MAX_VALUE;
    private static final int MAX_VIEWER_STEPS = Integer.MAX_VALUE;
    private static final int REQUEST_INTERVAL = 5;
    private static final Map<UUID, Throttle> THROTTLES = new HashMap<>();

    private TreeServer() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(TreeProtocol.REQUEST, (server, player, handler, buf, responseSender) -> {
            TreeProtocol.Request request;
            try {
                request = TreeProtocol.Request.read(buf);
            } catch (RuntimeException e) {
                MCAVie.LOGGER.warn("Requête d'arbre illisible de {}", player.getName().getString());
                return;
            }
            server.execute(() -> {
                Throttle throttle = THROTTLES.computeIfAbsent(player.getUuid(), id -> new Throttle());
                if (server.getTicks() - throttle.lastTick < REQUEST_INTERVAL) {
                    throttle.pending.put(request.mode(), request);
                    return;
                }
                throttle.lastTick = server.getTicks();
                respond(server, player, request);
            });
        });
        ServerTickEvents.END_SERVER_TICK.register(TreeServer::flushThrottled);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> THROTTLES.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> THROTTLES.clear());

        ServerTickEvents.END_SERVER_TICK.register(SpouseRegistry::flush);
        ServerLifecycleEvents.SERVER_STOPPING.register(SpouseRegistry::flush);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SpouseRegistry.clearPending());
    }

    private static final class Throttle {
        private int lastTick = Integer.MIN_VALUE / 2;
        private final Map<Integer, TreeProtocol.Request> pending = new LinkedHashMap<>();
    }

    private static void flushThrottled(MinecraftServer server) {
        if (THROTTLES.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Throttle>> it = THROTTLES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Throttle> entry = it.next();
            Throttle throttle = entry.getValue();
            if (throttle.pending.isEmpty() || server.getTicks() - throttle.lastTick < REQUEST_INTERVAL) {
                continue;
            }
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            Iterator<TreeProtocol.Request> requests = throttle.pending.values().iterator();
            TreeProtocol.Request request = requests.next();
            requests.remove();
            throttle.lastTick = server.getTicks();
            respond(server, player, request);
        }
    }

    private static void respond(MinecraftServer server, ServerPlayerEntity player, TreeProtocol.Request request) {
        try {
            TreeProtocol.Response response = handle(server, player, request);
            PacketByteBuf out = PacketByteBufs.create();
            response.write(out);
            ServerPlayNetworking.send(player, TreeProtocol.RESPONSE, out);
        } catch (RuntimeException e) {
            MCAVie.LOGGER.error("Erreur en préparant l'arbre généalogique", e);
        }
    }

    private static TreeProtocol.Response handle(MinecraftServer server, ServerPlayerEntity player, TreeProtocol.Request request) {
        FamilyTree.get(player.getServerWorld()).getOrCreate(player);
        return handle(server, player.getServerWorld(), player.getUuid(), request);
    }

    public static TreeProtocol.Response handle(MinecraftServer server, ServerWorld world, UUID viewerId, TreeProtocol.Request request) {
        SpouseRegistry.flush(server);
        FamilyGraph graph = new FamilyGraph(server, FamilyTree.get(world));
        Viewer viewer = new Viewer(graph, viewerId);

        return switch (request.mode()) {
            case TreeProtocol.MODE_TREE -> new TreeProtocol.Response(request.mode(), request.root(),
                    tree(graph, viewer, request.root(), request.up(), request.down()), List.of(), null, viewer.id, "");
            case TreeProtocol.MODE_PATH -> path(graph, viewer, request.root(), request.other(), request.up());
            case TreeProtocol.MODE_SEARCH -> new TreeProtocol.Response(request.mode(), Util.NIL_UUID,
                    search(graph, viewer, request.text()), List.of(), null, viewer.id, "");
            case TreeProtocol.MODE_LINEAGE -> new TreeProtocol.Response(request.mode(), Util.NIL_UUID,
                    lineage(graph, viewer, request.text()), List.of(), null, viewer.id, request.text());
            default -> new TreeProtocol.Response(request.mode(), Util.NIL_UUID, List.of(), List.of(), null, viewer.id, "");
        };
    }

    private static final class Viewer {
        private final FamilyGraph graph;
        private final UUID id;
        private FamilyGraph.Paths paths;

        Viewer(FamilyGraph graph, UUID id) {
            this.graph = graph;
            this.id = id;
        }

        @Nullable
        RelationInfo relationTo(UUID person) {
            if (id.equals(person)) {
                return Kinship.SELF;
            }
            if (paths == null) {
                paths = graph.shortestPaths(id, MAX_VIEWER_STEPS, null);
            }
            return paths.relation(person);
        }

        FamilyPerson person(FamilyTreeNode node) {
            return graph.person(node, relationTo(node.id()));
        }

        List<FamilyPerson> persons(Iterable<UUID> ids) {
            List<FamilyPerson> list = new ArrayList<>();
            for (UUID uuid : ids) {
                graph.node(uuid).ifPresent(node -> list.add(person(node)));
            }
            return list;
        }
    }

    private static List<FamilyPerson> tree(FamilyGraph graph, Viewer viewer, UUID root, int up, int down) {
        if (!graph.contains(root)) {
            return List.of();
        }
        TreeSelection.Graph selection = new TreeSelection.Graph() {
            @Override
            public List<UUID> parents(UUID id) {
                return graph.node(id).map(graph::parents).orElse(List.of());
            }

            @Override
            public List<UUID> children(UUID id) {
                return graph.node(id).map(graph::children).orElse(List.of());
            }

            @Override
            public List<UUID> spouses(UUID id) {
                return graph.node(id).map(graph::spouses).orElse(List.of());
            }
        };
        return viewer.persons(TreeSelection.select(selection, root, up, down, MAX_PERSONS));
    }

    private static TreeProtocol.Response path(FamilyGraph graph, Viewer viewer, UUID a, UUID b, int index) {
        if (a.equals(b) && graph.contains(a)) {
            return new TreeProtocol.Response(TreeProtocol.MODE_PATH, a, viewer.persons(List.of(a)), List.of(a), Kinship.SELF, viewer.id, "", 0, 1);
        }
        List<FamilyGraph.Link> links = graph.links(a, b, MAX_LINKS);
        if (links.isEmpty()) {
            List<UUID> both = a.equals(b) ? List.of(a) : List.of(a, b);
            return new TreeProtocol.Response(TreeProtocol.MODE_PATH, a, viewer.persons(both), List.of(), RelationInfo.NONE, viewer.id, "");
        }

        int chosen = Math.max(0, Math.min(index, links.size() - 1));
        List<UUID> nodes = links.get(chosen).nodes();
        List<Kinship.Step> steps = links.get(chosen).steps();
        Set<UUID> shown = new LinkedHashSet<>(nodes);

        for (int i = 1; i < nodes.size() - 1; i++) {
            if (steps.get(i - 1) == Kinship.Step.UP && steps.get(i) == Kinship.Step.DOWN) {
                UUID top = nodes.get(i);
                addOtherParent(graph, shown, nodes.get(i - 1), top);
                addOtherParent(graph, shown, nodes.get(i + 1), top);
            }
        }

        RelationInfo relation = Kinship.classify(nodes, steps, graph::node);
        return new TreeProtocol.Response(TreeProtocol.MODE_PATH, a, viewer.persons(shown), nodes, relation, viewer.id, "", chosen, links.size());
    }

    private static void addOtherParent(FamilyGraph graph, Set<UUID> shown, UUID child, UUID knownParent) {
        graph.node(child).ifPresent(node -> graph.parents(node).stream()
                .filter(parent -> !parent.equals(knownParent))
                .forEach(shown::add));
    }

    private static List<FamilyPerson> search(FamilyGraph graph, Viewer viewer, String text) {
        String query = FamilyGraph.normalize(text);
        if (query.isEmpty()) {
            return List.of();
        }
        record Match(FamilyTreeNode node, String name) {
        }
        List<Match> matches = new ArrayList<>();
        for (FamilyTreeNode node : graph.all()) {
            String fullName = FamilyGraph.normalize(graph.fullName(node));
            if (fullName.contains(query)
                    || FamilyGraph.normalize(graph.firstName(node)).contains(query)
                    || FamilyGraph.normalize(graph.surname(node.id())).contains(query)) {
                matches.add(new Match(node, fullName));
            }
        }
        matches.sort(Comparator.comparing((Match match) -> match.node().isDeceased())
                .thenComparing(match -> !match.name().startsWith(query))
                .thenComparing(Match::name));

        List<FamilyPerson> result = new ArrayList<>();
        for (Match match : matches) {
            if (result.size() >= MAX_SEARCH_RESULTS) {
                break;
            }
            result.add(viewer.person(match.node()));
        }
        return result;
    }

    private static List<FamilyPerson> lineage(FamilyGraph graph, Viewer viewer, String text) {
        String query = FamilyGraph.normalize(text);
        String queryMale = maleForm(query);
        if (query.isEmpty()) {
            return List.of();
        }
        List<FamilyPerson> result = new ArrayList<>();
        for (FamilyTreeNode node : graph.all()) {
            String surname = FamilyGraph.normalize(graph.surname(node.id()));
            if (surname.isEmpty()) {
                continue;
            }
            if (surname.equals(query) || maleForm(surname).equals(queryMale) || femaleForm(surname).equals(query)) {
                result.add(viewer.person(node));
                if (result.size() >= MAX_PERSONS) {
                    break;
                }
            }
        }
        result.sort(Comparator.comparing((FamilyPerson p) -> p.birthDay() == null ? Long.MAX_VALUE : p.birthDay())
                .thenComparing(FamilyPerson::name));
        return result;
    }

    private static String femaleForm(String surname) {
        int slash = surname.indexOf('/');
        return slash < 0 ? surname : surname.substring(slash + 1).trim();
    }

    private static String maleForm(String surname) {
        int slash = surname.indexOf('/');
        return (slash < 0 ? surname : surname.substring(0, slash)).trim();
    }
}
