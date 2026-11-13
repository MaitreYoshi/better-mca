package com.maitreyoshi.mcavie;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.maitreyoshi.mcavie.life.WellbeingRegistry;
import com.maitreyoshi.mcavie.tree.FamilyPerson;
import com.maitreyoshi.mcavie.tree.RelationInfo;
import com.maitreyoshi.mcavie.tree.TreeProtocol;
import com.maitreyoshi.mcavie.tree.server.TreeServer;
import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkManager;
import com.mojang.brigadier.arguments.StringArgumentType;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.maitreyoshi.mcavie.social.Social;
import com.maitreyoshi.mcavie.social.SocialRegistry;
import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.life.LifeCycle;
import com.maitreyoshi.mcavie.life.LifeRegistry;
import com.maitreyoshi.mcavie.life.Orientation;
import com.maitreyoshi.mcavie.life.OrientationRegistry;
import net.mca.block.BlocksMCA;
import net.mca.block.TombstoneBlock;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.VillagerFactory;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.relationship.Gender;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.mca.util.WorldUtils;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class DevTestCommand {
    private DevTestCommand() {
    }

    static void register() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("surnametest").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("family").executes(ctx -> {
                            ServerLevel world = ctx.getSource().getLevel();
                            BlockPos pos = world.getSharedSpawnPos();
                            VillagerEntityMCA father = VillagerFactory.newVillager(world).withGender(Gender.MALE).withAge(0).withPosition(pos).spawn(MobSpawnType.COMMAND);
                            VillagerEntityMCA mother = VillagerFactory.newVillager(world).withGender(Gender.FEMALE).withAge(0).withPosition(pos).spawn(MobSpawnType.COMMAND);
                            world.getServer().execute(() -> {
                                VillagerEntityMCA child = mother.getRelationships().getPregnancy().createChild(Gender.FEMALE, father);
                                child.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
                                WorldUtils.spawnEntity(world, child, MobSpawnType.BREEDING);
                            });
                            ctx.getSource().sendSuccess(() -> Component.literal("Famille créée en " + pos.toShortString()), false);
                            return 1;
                        }))
                        .then(Commands.literal("calendar").then(Commands.argument("ticks", LongArgumentType.longArg(0)).executes(ctx -> {
                            WorldCalendar.CalendarDate d = WorldCalendar.fromTicks(LongArgumentType.getLong(ctx, "ticks"));
                            ctx.getSource().sendSuccess(() -> Component.literal(d.toString()), false);
                            return 1;
                        })))
                        .then(Commands.literal("tomb").executes(ctx -> {
                            ServerLevel world = ctx.getSource().getLevel();
                            BlockPos pos = world.getSharedSpawnPos();
                            java.util.List<VillagerEntityMCA> nearby = world.getEntitiesOfClass(VillagerEntityMCA.class, new AABB(pos).inflate(32), e -> true);
                            if (nearby.isEmpty()) {
                                ctx.getSource().sendFailure(Component.literal("Aucun villageois proche"));
                                return 0;
                            }
                            VillagerEntityMCA v = nearby.get(0);
                            BlockPos tombPos = pos.offset(3, 0, 0);
                            world.setBlockAndUpdate(tombPos, BlocksMCA.GRAVELLING_HEADSTONE.get().defaultBlockState());
                            TombstoneBlock.Data.of(world.getBlockEntity(tombPos)).ifPresent(tomb -> {
                                tomb.setEntity(v);
                                String name = tomb.getEntityName().map(Component::getString).orElse("(vide)");
                                ctx.getSource().sendSuccess(() -> Component.literal("Tombe : " + name), false);
                            });
                            return 1;
                        }))
                        .then(Commands.literal("tombs").executes(ctx -> {
                            ServerLevel world = ctx.getSource().getLevel();
                            BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
                            java.util.List<VillagerEntityMCA> nearby = world.getEntitiesOfClass(VillagerEntityMCA.class, new AABB(pos).inflate(32), e -> true);
                            if (nearby.isEmpty()) {
                                ctx.getSource().sendFailure(Component.literal("Aucun villageois proche"));
                                return 0;
                            }
                            VillagerEntityMCA v = nearby.get(0);
                            for (int tier = 0; tier < 3; tier++) {
                                BlockPos tombPos = pos.offset(2 * tier - 2, 0, 3);
                                world.setBlockAndUpdate(tombPos, com.maitreyoshi.mcavie.life.TombstoneBlocks.forTier(tier).defaultBlockState());
                                TombstoneBlock.Data.of(world.getBlockEntity(tombPos)).ifPresent(tomb -> tomb.setEntity(v));
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal("Tombes pauvre / moyenne / riche pour " + v.getName().getString()), false);
                            return 1;
                        }))
                        .then(Commands.literal("rep").then(Commands.argument("value", IntegerArgumentType.integer(-5000, 5000)).executes(ctx -> {
                            net.minecraft.server.level.ServerPlayer player = ctx.getSource().getPlayerOrException();
                            int value = IntegerArgumentType.getInteger(ctx, "value");
                            net.mca.server.world.data.Village.findNearest(player).ifPresent(v -> {
                                v.pushHearts(player, value);
                                ctx.getSource().sendSuccess(() -> Component.literal("Réputation à " + v.getName() + " : " + v.getReputation(player)), false);
                            });
                            return 1;
                        })))
                        .then(Commands.literal("levy").executes(ctx -> {
                            net.minecraft.server.level.ServerPlayer player = ctx.getSource().getPlayerOrException();
                            net.mca.server.world.data.Village.findNearest(player).ifPresent(v ->
                                    com.maitreyoshi.mcavie.village.VillageEconomy.devLevy(v, ctx.getSource().getLevel(), WorldCalendar.now(ctx.getSource().getLevel()).dayIndex()));
                            return 1;
                        }))
                        .then(Commands.literal("market").executes(ctx -> {
                            net.minecraft.server.level.ServerPlayer player = ctx.getSource().getPlayerOrException();
                            net.mca.server.world.data.Village.findNearest(player).ifPresent(v ->
                                    com.maitreyoshi.mcavie.village.VillageMarket.devHold(v, ctx.getSource().getLevel()));
                            return 1;
                        }))
                        .then(Commands.literal("crime").executes(ctx -> {
                            net.minecraft.server.level.ServerPlayer player = ctx.getSource().getPlayerOrException();
                            net.mca.server.world.data.Village.findNearest(player).ifPresent(v ->
                                    com.maitreyoshi.mcavie.village.VillageJustice.devCase(v, ctx.getSource().getLevel()));
                            return 1;
                        }))
                        .then(Commands.literal("village").executes(ctx -> {
                            net.minecraft.server.level.ServerPlayer player = ctx.getSource().getPlayerOrException();
                            net.mca.server.world.data.Village.findNearest(player).ifPresent(v -> {
                                com.maitreyoshi.mcavie.village.VillageState state = com.maitreyoshi.mcavie.village.VillageState.of(v);
                                com.maitreyoshi.mcavie.village.Prestige.Result r = com.maitreyoshi.mcavie.village.Prestige.evaluate(v, state.rank);
                                ctx.getSource().sendSuccess(() -> Component.literal(v.getName() + " : " + state.rank + ", prestige " + r.prestige()
                                        + " (résidentiel " + r.residential() + "/" + r.residentialCap() + "), habitants " + r.population()
                                        + ", descente " + state.descentDaysLeft + ", impôt " + v.getTaxes()), false);
                                v.getBuildings().values().forEach(b -> {
                                    com.maitreyoshi.mcavie.village.Prestige.Score s = r.score(b);
                                    ctx.getSource().sendSuccess(() -> Component.literal("  " + b.getType() + (s == null ? " (incomplet)" : " " + s.status() + " " + s.points() + " pts, surface " + s.surface())), false);
                                });
                            });
                            return 1;
                        }))
                        .then(Commands.literal("sethappy").then(Commands.argument("value", IntegerArgumentType.integer(0, 100)).executes(ctx -> {
                            ServerLevel world = ctx.getSource().getLevel();
                            int value = IntegerArgumentType.getInteger(ctx, "value");
                            WellbeingRegistry registry = WellbeingRegistry.get(world.getServer());
                            for (VillagerEntityMCA v : world.getEntitiesOfClass(VillagerEntityMCA.class, new AABB(world.getSharedSpawnPos()).inflate(32), e -> true)) {
                                Optional.ofNullable(registry.get(v.getUUID())).ifPresent(r -> {
                                    r.happiness = value;
                                    r.lastSentHappiness = value;
                                    r.lastSentSick = r.sick;
                                    SurnameNetwork.sendHappiness(v, value, r.sick);
                                });
                            }
                            registry.setDirty();
                            return 1;
                        })))
                        .then(Commands.literal("links").executes(ctx -> {
                            ServerLevel world = ctx.getSource().getLevel();
                            List<UUID> ids = new java.util.ArrayList<>(((com.maitreyoshi.mcavie.tree.server.mixin.FamilyTreeAccessor) FamilyTree.get(world)).mcavie$getEntries().keySet());
                            ids.sort(Comparator.naturalOrder());
                            java.util.Random random = new java.util.Random(1);
                            int pairs = 0;
                            int linked = 0;
                            int several = 0;
                            long slowest = 0;
                            long total = 0;
                            long oldTotal = 0;
                            long oldMax = 0;
                            UUID[] best = null;
                            int bestCount = 0;
                            for (int i = 0; i < 500 && ids.size() > 1; i++) {
                                UUID a = ids.get(random.nextInt(ids.size()));
                                UUID b = ids.get(random.nextInt(ids.size()));
                                long start = System.nanoTime();
                                TreeProtocol.Response r = TreeServer.handle(world.getServer(), world, a, TreeProtocol.Request.path(a, b, 0));
                                long time = System.nanoTime() - start;
                                long startOld = System.nanoTime();
                                new com.maitreyoshi.mcavie.tree.server.FamilyGraph(world.getServer(), FamilyTree.get(world)).shortestPaths(a, Integer.MAX_VALUE, b);
                                long old = System.nanoTime() - startOld;
                                oldTotal += old;
                                oldMax = Math.max(oldMax, old);
                                pairs++;
                                total += time;
                                slowest = Math.max(slowest, time);
                                if (r.count() > 0) {
                                    linked++;
                                }
                                if (r.count() > 1) {
                                    several++;
                                }
                                if (r.count() > bestCount && r.relation() != null && !r.relation().code().equals("relative_by_marriage")) {
                                    bestCount = r.count();
                                    best = new UUID[]{a, b};
                                }
                            }
                            StringBuilder out = new StringBuilder(ids.size() + " personnes, " + pairs + " paires, " + linked + " liées, " + several
                                    + " avec plusieurs liens, moyenne " + total / Math.max(1, pairs) / 1000 + " µs, max " + slowest / 1000 + " µs ; plus court seul : moyenne " + oldTotal / Math.max(1, pairs) / 1000 + " µs, max " + oldMax / 1000 + " µs\n");
                            if (best != null) {
                                out.append(" max ").append(bestCount).append(" liens\n");
                                for (int i = 0; i < bestCount; i += Math.max(1, bestCount / 12)) {
                                    TreeProtocol.Response r = TreeServer.handle(world.getServer(), world, best[0], TreeProtocol.Request.path(best[0], best[1], i));
                                    out.append(" ").append(i + 1).append(". ").append(r.relation() == null ? "?" : r.relation().code() + "/" + r.relation().count())
                                            .append(" (").append(r.path().size()).append(" personnes)\n");
                                }
                            }
                            String text = out.toString();
                            ctx.getSource().sendSuccess(() -> Component.literal(text), false);
                            return 1;
                        }))
                        .then(Commands.literal("tree").executes(ctx -> {
                            ServerLevel world = ctx.getSource().getLevel();
                            VillagerEntityMCA youngest = world.getEntitiesOfClass(VillagerEntityMCA.class, new AABB(world.getSharedSpawnPos()).inflate(32), e -> true).stream()
                                    .min(java.util.Comparator.comparingInt(VillagerEntityMCA::getAge)).orElse(null);
                            if (youngest == null) {
                                ctx.getSource().sendFailure(Component.literal("Aucun villageois proche"));
                                return 0;
                            }
                            TreeProtocol.Response response = TreeServer.handle(world.getServer(), world, youngest.getUUID(),
                                    TreeProtocol.Request.tree(youngest.getUUID(), 2, 2));
                            StringBuilder out = new StringBuilder("Arbre de " + youngest.getName().getString() + " : " + response.persons().size() + " personnes\n");
                            for (FamilyPerson p : response.persons()) {
                                RelationInfo r = p.relationToViewer();
                                out.append(" - ").append(p.name()).append(" = ").append(r == null ? "?" : r.code() + "/" + r.count())
                                        .append(" conjoints=").append(p.spouses().size()).append(" enfants=").append(p.children().size()).append('\n');
                            }
                            String text = out.toString();
                            ctx.getSource().sendSuccess(() -> Component.literal(text), false);
                            return 1;
                        }))
                        .then(Commands.literal("job").then(Commands.argument("job", StringArgumentType.word()).executes(ctx -> {
                            ServerLevel world = ctx.getSource().getLevel();
                            String jobName = StringArgumentType.getString(ctx, "job").toUpperCase(java.util.Locale.ROOT);
                            Job job;
                            try {
                                job = Job.valueOf(jobName);
                            } catch (IllegalArgumentException e) {
                                ctx.getSource().sendFailure(Component.literal("Métier inconnu : " + jobName));
                                return 0;
                            }
                            BlockPos spawn = world.getSharedSpawnPos();
                            world.getEntitiesOfClass(VillagerEntityMCA.class, new AABB(spawn).inflate(32), v -> v.getAge() >= 0 && WorkManager.getJob(v) == Job.NONE).stream()
                                    .min(java.util.Comparator.comparingDouble(v -> v.distanceToSqr(spawn.getX(), spawn.getY(), spawn.getZ())))
                                    .ifPresent(v -> {
                                        WorkManager.setJob(v, job);
                                        ctx.getSource().sendSuccess(() -> Component.literal(v.getName().getString() + " -> " + job), false);
                                    });
                            return 1;
                        })))
                        .then(Commands.literal("depot").executes(ctx -> {
                            ServerLevel world = ctx.getSource().getLevel();
                            BlockPos chest = world.getSharedSpawnPos().offset(0, 0, 3);
                            world.setBlockAndUpdate(chest, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
                            for (VillagerEntityMCA v : world.getEntitiesOfClass(VillagerEntityMCA.class, new AABB(world.getSharedSpawnPos()).inflate(48), e -> WorkManager.getJob(e) != Job.NONE)) {
                                WorkManager.getRecord(v).depot = chest;
                                WorkManager.markDirty(v);
                            }
                            return 1;
                        }))
                        .then(Commands.literal("chest").executes(ctx -> {
                            ServerLevel world = ctx.getSource().getLevel();
                            StringBuilder out = new StringBuilder("Coffre : ");
                            if (world.getBlockEntity(world.getSharedSpawnPos().offset(0, 0, 3)) instanceof net.minecraft.world.Container inv) {
                                for (int i = 0; i < inv.getContainerSize(); i++) {
                                    if (!inv.getItem(i).isEmpty()) {
                                        out.append(inv.getItem(i).getCount()).append(' ').append(inv.getItem(i).getItem()).append(", ");
                                    }
                                }
                            }
                            String text = out.toString();
                            ctx.getSource().sendSuccess(() -> Component.literal(text), false);
                            return 1;
                        }))
                        .then(Commands.literal("couples").executes(ctx -> {
                            ServerLevel world = ctx.getSource().getLevel();
                            OrientationRegistry orientations = OrientationRegistry.get(world.getServer());
                            FamilyTree tree = FamilyTree.get(world);
                            int couples = 0;
                            int unknown = 0;
                            StringBuilder out = new StringBuilder();
                            for (FamilyTreeNode node : ((com.maitreyoshi.mcavie.tree.server.mixin.FamilyTreeAccessor) tree).mcavie$getEntries().values()) {
                                if (node.isDeceased() || node.getRelationshipState() != net.mca.entity.ai.relationship.RelationshipState.MARRIED_TO_VILLAGER
                                        || node.id().compareTo(node.partner()) > 0) {
                                    continue;
                                }
                                Optional<FamilyTreeNode> partner = tree.getOrEmpty(node.partner());
                                if (partner.isEmpty() || partner.get().isDeceased()) {
                                    continue;
                                }
                                couples++;
                                Orientation a = orientations.get(node.id());
                                Orientation b = orientations.get(partner.get().id());
                                if (a == null || b == null) {
                                    unknown++;
                                    continue;
                                }
                                if (!Orientation.compatible(a, node.gender(), b, partner.get().gender())) {
                                    out.append(" - ").append(socialName(world, node.id())).append(" (").append(node.gender()).append(", ").append(a)
                                            .append(") + ").append(socialName(world, partner.get().id())).append(" (").append(partner.get().gender())
                                            .append(", ").append(b).append(")\n");
                                }
                            }
                            String text = "Couples PNJ : " + couples + ", orientation inconnue : " + unknown
                                    + (out.isEmpty() ? ", aucun couple incompatible" : ", incompatibles :\n" + out);
                            ctx.getSource().sendSuccess(() -> Component.literal(text), false);
                            return 1;
                        }))
                        .then(Commands.literal("social")
                                .executes(ctx -> {
                                    ServerLevel world = ctx.getSource().getLevel();
                                    List<VillagerEntityMCA> near = nearestVillagers(world, ctx.getSource().getPosition(), 1);
                                    if (near.isEmpty()) {
                                        ctx.getSource().sendFailure(Component.literal("Aucun villageois proche"));
                                        return 0;
                                    }
                                    UUID owner = near.get(0).getUUID();
                                    StringBuilder out = new StringBuilder("Relations de " + socialName(world, owner) + " :\n");
                                    SocialRegistry.get(world.getServer()).links(owner).entrySet().stream()
                                            .sorted((a, b) -> Integer.compare(b.getValue().score, a.getValue().score))
                                            .forEach(e -> out.append(" - ").append(socialName(world, e.getKey())).append(" : ").append(e.getValue().score)
                                                    .append(e.getValue().family ? " (famille)" : "").append(", vu le jour ").append(e.getValue().lastSeen).append('\n'));
                                    String text = out.toString();
                                    ctx.getSource().sendSuccess(() -> Component.literal(text), false);
                                    return 1;
                                })
                                .then(Commands.literal("talk").executes(ctx -> {
                                    ServerLevel world = ctx.getSource().getLevel();
                                    List<VillagerEntityMCA> near = nearestVillagers(world, ctx.getSource().getPosition(), 2);
                                    if (near.size() < 2) {
                                        ctx.getSource().sendFailure(Component.literal("Il faut deux villageois proches"));
                                        return 0;
                                    }
                                    UUID a = near.get(0).getUUID();
                                    UUID b = near.get(1).getUUID();
                                    int delta = Social.talk(world, a, b, Social.Mode.FORCED);
                                    SocialRegistry registry = SocialRegistry.get(world.getServer());
                                    String text = socialName(world, a) + " et " + socialName(world, b) + (delta < 0 ? " se disputent" : " discutent")
                                            + " : " + Social.score(registry, a, b) + " / " + Social.score(registry, b, a);
                                    ctx.getSource().sendSuccess(() -> Component.literal(text), false);
                                    return 1;
                                }))
                                .then(Commands.literal("set").then(Commands.argument("note", IntegerArgumentType.integer(Social.MIN_SCORE, Social.MAX_SCORE)).executes(ctx -> {
                                    ServerLevel world = ctx.getSource().getLevel();
                                    List<VillagerEntityMCA> near = nearestVillagers(world, ctx.getSource().getPosition(), 2);
                                    if (near.size() < 2) {
                                        ctx.getSource().sendFailure(Component.literal("Il faut deux villageois proches"));
                                        return 0;
                                    }
                                    int note = IntegerArgumentType.getInteger(ctx, "note");
                                    UUID a = near.get(0).getUUID();
                                    UUID b = near.get(1).getUUID();
                                    SocialRegistry registry = SocialRegistry.get(world.getServer());
                                    long today = WorldCalendar.now(world).dayIndex();
                                    Social.change(world, a, b, note - Social.score(registry, a, b), today, true);
                                    Social.change(world, b, a, note - Social.score(registry, b, a), today, true);
                                    String text = socialName(world, a) + " et " + socialName(world, b) + " : " + Social.score(registry, a, b) + " / " + Social.score(registry, b, a);
                                    ctx.getSource().sendSuccess(() -> Component.literal(text), false);
                                    return 1;
                                })))
                                .then(Commands.literal("day").executes(ctx -> {
                                    ServerLevel world = ctx.getSource().getLevel();
                                    List<VillagerEntityMCA> near = nearestVillagers(world, ctx.getSource().getPosition(), 1);
                                    Optional<net.mca.server.world.data.Village> village = near.isEmpty() ? Optional.empty() : near.get(0).getResidency().getHomeVillage();
                                    if (village.isEmpty()) {
                                        ctx.getSource().sendFailure(Component.literal("Aucun village proche"));
                                        return 0;
                                    }
                                    Social.daily(village.get(), world, WorldCalendar.now(world).dayIndex());
                                    ctx.getSource().sendSuccess(() -> Component.literal("Journée simulée pour " + village.get().getName()), false);
                                    return 1;
                                })))
                        .then(Commands.literal("age").then(Commands.argument("years", IntegerArgumentType.integer(18, 120)).executes(ctx -> {
                            ServerLevel world = ctx.getSource().getLevel();
                            List<VillagerEntityMCA> near = nearestVillagers(world, ctx.getSource().getPosition(), 1);
                            if (near.isEmpty()) {
                                ctx.getSource().sendFailure(Component.literal("Aucun villageois proche"));
                                return 0;
                            }
                            VillagerEntityMCA v = near.get(0);
                            int years = IntegerArgumentType.getInteger(ctx, "years");
                            long today = WorldCalendar.now(world).dayIndex();
                            LifeRegistry.get(world.getServer()).setBirthDay(v.getUUID(), WorldCalendar.gameDayOf(WorldCalendar.calendarDay(today) - (long) Math.ceil(years * LifeCycle.DAYS_PER_YEAR) - 1));
                            com.maitreyoshi.mcavie.aging.Aging.refresh(v);
                            LifeCycle.update(v);
                            WorkManager.update(v);
                            int now = com.maitreyoshi.mcavie.aging.Aging.years(v);
                            ctx.getSource().sendSuccess(() -> Component.literal(v.getName().getString() + " a maintenant " + now + " ans (vitesse "
                                    + String.format("%.3f", v.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)) + ", vie max " + v.getMaxHealth() + ", métier " + WorkManager.getJob(v) + ")"), false);
                            return 1;
                        })))
                        .then(Commands.literal("work").executes(ctx -> {
                            ServerLevel world = ctx.getSource().getLevel();
                            StringBuilder out = new StringBuilder("Heure de travail : " + WorkManager.isWorkTime(world) + "\n");
                            for (VillagerEntityMCA v : world.getEntitiesOfClass(VillagerEntityMCA.class, new AABB(world.getSharedSpawnPos()).inflate(48), e -> true)) {
                                Job job = WorkManager.getJob(v);
                                if (job == Job.NONE) {
                                    continue;
                                }
                                StringBuilder inv = new StringBuilder();
                                for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
                                    var s = v.getInventory().getItem(i);
                                    if (!s.isEmpty()) {
                                        inv.append(s.getCount()).append(' ').append(s.getItem()).append(", ");
                                    }
                                }
                                out.append(v.getName().getString()).append(" ").append(job).append(" travaille=").append(WorkManager.isWorking(v)).append(" état=").append(WorkManager.getStatus(v)).append(" geste=").append(WorkManager.getGesture(v)).append(" centre=").append(String.valueOf(WorkManager.getRecord(v).center)).append(" poste=").append(String.valueOf(WorkManager.getRecord(v).depot))
                                        .append(" activité=").append(v.getBrain().getActiveNonCoreActivity().map(Object::toString).orElse("?"))
                                        .append(" xp=").append(WorkManager.getRecord(v).experience)
                                        .append(" pos=").append(v.blockPosition().toShortString())
                                        .append(" inv=[").append(inv).append("]\n");
                            }
                            String text = out.toString();
                            ctx.getSource().sendSuccess(() -> Component.literal(text), false);
                            return 1;
                        }))
                        .then(Commands.literal("graves").executes(ctx -> {
                            ServerLevel world = ctx.getSource().getLevel();
                            BlockPos center = world.getSharedSpawnPos();
                            for (BlockPos p : BlockPos.betweenClosed(center.offset(-12, -4, -12), center.offset(12, 4, 12))) {
                                TombstoneBlock.Data.of(world.getBlockEntity(p)).filter(TombstoneBlock.Data::hasEntity).ifPresent(tomb -> {
                                    String line = "Tombe en " + p.toShortString() + " : " + tomb.getEntityName().map(Component::getString).orElse("?");
                                    ctx.getSource().sendSuccess(() -> Component.literal(line + "\n"), false);
                                });
                            }
                            return 1;
                        }))
                        .then(Commands.literal("list").executes(ctx -> {
                            ServerLevel world = ctx.getSource().getLevel();
                            BlockPos pos = world.getSharedSpawnPos();
                            SurnameRegistry registry = SurnameRegistry.get(world.getServer());
                            FamilyTree tree = FamilyTree.get(world);
                            for (VillagerEntityMCA v : world.getEntitiesOfClass(VillagerEntityMCA.class, new AABB(pos).inflate(32), e -> true)) {
                                FamilyTreeNode node = v.getRelationships().getFamilyEntry();
                                String father = tree.getOrEmpty(node.father()).map(FamilyTreeNode::getName).orElse("?");
                                String mother = tree.getOrEmpty(node.mother()).map(FamilyTreeNode::getName).orElse("?");
                                Long birth = LifeRegistry.get(world.getServer()).getBirthDay(v.getUUID());
                                long today = WorldCalendar.now(world).dayIndex();
                                String age = birth == null ? "?" : LifeCycle.ageInYears(birth, today) + " ans, né en " + WorldCalendar.displayYearOfDay(birth);
                                String line = v.getTrackedValue(VillagerLike.VILLAGER_NAME)
                                        + " [" + v.getGenetics().getGender() + ", nom=" + registry.get(v.getUUID()) + "]"
                                        + " " + age + ", taille=" + v.getAgeState() + " (" + v.getAge() + ")"
                                        + Optional.ofNullable(WellbeingRegistry.get(world.getServer()).get(v.getUUID()))
                                        .map(r -> ", bonheur=" + Math.round(r.happiness) + (r.sick ? " MALADE" : "")).orElse(", bonheur=?")
                                        + " père=" + father + " mère=" + mother;
                                ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                            }
                            return 1;
                        }))
        ));
    }

    private static List<VillagerEntityMCA> nearestVillagers(ServerLevel world, Vec3 pos, int count) {
        return world.getEntitiesOfClass(VillagerEntityMCA.class, new AABB(pos, pos).inflate(32), VillagerEntityMCA::isAlive).stream()
                .sorted(Comparator.comparingDouble(v -> v.distanceToSqr(pos)))
                .limit(count)
                .toList();
    }

    private static String socialName(ServerLevel world, UUID uuid) {
        return Social.displayName(world, uuid);
    }
}
