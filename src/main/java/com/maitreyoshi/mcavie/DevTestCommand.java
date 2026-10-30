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
import net.minecraft.util.math.Vec3d;
import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.life.LifeCycle;
import com.maitreyoshi.mcavie.life.LifeRegistry;
import net.mca.block.BlocksMCA;
import net.mca.block.TombstoneBlock;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.VillagerFactory;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.relationship.Gender;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.mca.util.WorldUtils;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

final class DevTestCommand {
    private DevTestCommand() {
    }

    static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("surnametest").requires(s -> s.hasPermissionLevel(2))
                        .then(CommandManager.literal("family").executes(ctx -> {
                            ServerWorld world = ctx.getSource().getWorld();
                            BlockPos pos = world.getSpawnPos();
                            VillagerEntityMCA father = VillagerFactory.newVillager(world).withGender(Gender.MALE).withAge(0).withPosition(pos).spawn(SpawnReason.COMMAND);
                            VillagerEntityMCA mother = VillagerFactory.newVillager(world).withGender(Gender.FEMALE).withAge(0).withPosition(pos).spawn(SpawnReason.COMMAND);
                            world.getServer().execute(() -> {
                                VillagerEntityMCA child = mother.getRelationships().getPregnancy().createChild(Gender.FEMALE, father);
                                child.setPosition(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
                                WorldUtils.spawnEntity(world, child, SpawnReason.BREEDING);
                            });
                            ctx.getSource().sendFeedback(() -> Text.literal("Famille créée en " + pos.toShortString()), false);
                            return 1;
                        }))
                        .then(CommandManager.literal("calendar").then(CommandManager.argument("ticks", LongArgumentType.longArg(0)).executes(ctx -> {
                            WorldCalendar.CalendarDate d = WorldCalendar.fromTicks(LongArgumentType.getLong(ctx, "ticks"));
                            ctx.getSource().sendFeedback(() -> Text.literal(d.toString()), false);
                            return 1;
                        })))
                        .then(CommandManager.literal("tomb").executes(ctx -> {
                            ServerWorld world = ctx.getSource().getWorld();
                            BlockPos pos = world.getSpawnPos();
                            java.util.List<VillagerEntityMCA> nearby = world.getEntitiesByClass(VillagerEntityMCA.class, new Box(pos).expand(32), e -> true);
                            if (nearby.isEmpty()) {
                                ctx.getSource().sendError(Text.literal("Aucun villageois proche"));
                                return 0;
                            }
                            VillagerEntityMCA v = nearby.get(0);
                            BlockPos tombPos = pos.add(3, 0, 0);
                            world.setBlockState(tombPos, BlocksMCA.GRAVELLING_HEADSTONE.get().getDefaultState());
                            TombstoneBlock.Data.of(world.getBlockEntity(tombPos)).ifPresent(tomb -> {
                                tomb.setEntity(v);
                                String name = tomb.getEntityName().map(Text::getString).orElse("(vide)");
                                ctx.getSource().sendFeedback(() -> Text.literal("Tombe : " + name), false);
                            });
                            return 1;
                        }))
                        .then(CommandManager.literal("tombs").executes(ctx -> {
                            ServerWorld world = ctx.getSource().getWorld();
                            BlockPos pos = BlockPos.ofFloored(ctx.getSource().getPosition());
                            java.util.List<VillagerEntityMCA> nearby = world.getEntitiesByClass(VillagerEntityMCA.class, new Box(pos).expand(32), e -> true);
                            if (nearby.isEmpty()) {
                                ctx.getSource().sendError(Text.literal("Aucun villageois proche"));
                                return 0;
                            }
                            VillagerEntityMCA v = nearby.get(0);
                            for (int tier = 0; tier < 3; tier++) {
                                BlockPos tombPos = pos.add(2 * tier - 2, 0, 3);
                                world.setBlockState(tombPos, com.maitreyoshi.mcavie.life.TombstoneBlocks.forTier(tier).getDefaultState());
                                TombstoneBlock.Data.of(world.getBlockEntity(tombPos)).ifPresent(tomb -> tomb.setEntity(v));
                            }
                            ctx.getSource().sendFeedback(() -> Text.literal("Tombes pauvre / moyenne / riche pour " + v.getName().getString()), false);
                            return 1;
                        }))
                        .then(CommandManager.literal("rep").then(CommandManager.argument("value", IntegerArgumentType.integer(-5000, 5000)).executes(ctx -> {
                            net.minecraft.server.network.ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            int value = IntegerArgumentType.getInteger(ctx, "value");
                            net.mca.server.world.data.Village.findNearest(player).ifPresent(v -> {
                                v.pushHearts(player, value);
                                ctx.getSource().sendFeedback(() -> Text.literal("Réputation à " + v.getName() + " : " + v.getReputation(player)), false);
                            });
                            return 1;
                        })))
                        .then(CommandManager.literal("levy").executes(ctx -> {
                            net.minecraft.server.network.ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            net.mca.server.world.data.Village.findNearest(player).ifPresent(v ->
                                    com.maitreyoshi.mcavie.village.VillageEconomy.devLevy(v, ctx.getSource().getWorld(), WorldCalendar.now(ctx.getSource().getWorld()).dayIndex()));
                            return 1;
                        }))
                        .then(CommandManager.literal("market").executes(ctx -> {
                            net.minecraft.server.network.ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            net.mca.server.world.data.Village.findNearest(player).ifPresent(v ->
                                    com.maitreyoshi.mcavie.village.VillageMarket.devHold(v, ctx.getSource().getWorld()));
                            return 1;
                        }))
                        .then(CommandManager.literal("crime").executes(ctx -> {
                            net.minecraft.server.network.ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            net.mca.server.world.data.Village.findNearest(player).ifPresent(v ->
                                    com.maitreyoshi.mcavie.village.VillageJustice.devCase(v, ctx.getSource().getWorld()));
                            return 1;
                        }))
                        .then(CommandManager.literal("village").executes(ctx -> {
                            net.minecraft.server.network.ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            net.mca.server.world.data.Village.findNearest(player).ifPresent(v -> {
                                com.maitreyoshi.mcavie.village.VillageState state = com.maitreyoshi.mcavie.village.VillageState.of(v);
                                com.maitreyoshi.mcavie.village.Prestige.Result r = com.maitreyoshi.mcavie.village.Prestige.evaluate(v, state.rank);
                                ctx.getSource().sendFeedback(() -> Text.literal(v.getName() + " : " + state.rank + ", prestige " + r.prestige()
                                        + " (résidentiel " + r.residential() + "/" + r.residentialCap() + "), habitants " + r.population()
                                        + ", descente " + state.descentDaysLeft + ", impôt " + v.getTaxes()), false);
                                v.getBuildings().values().forEach(b -> {
                                    com.maitreyoshi.mcavie.village.Prestige.Score s = r.score(b);
                                    ctx.getSource().sendFeedback(() -> Text.literal("  " + b.getType() + (s == null ? " (incomplet)" : " " + s.status() + " " + s.points() + " pts, surface " + s.surface())), false);
                                });
                            });
                            return 1;
                        }))
                        .then(CommandManager.literal("sethappy").then(CommandManager.argument("value", IntegerArgumentType.integer(0, 100)).executes(ctx -> {
                            ServerWorld world = ctx.getSource().getWorld();
                            int value = IntegerArgumentType.getInteger(ctx, "value");
                            WellbeingRegistry registry = WellbeingRegistry.get(world.getServer());
                            for (VillagerEntityMCA v : world.getEntitiesByClass(VillagerEntityMCA.class, new Box(world.getSpawnPos()).expand(32), e -> true)) {
                                Optional.ofNullable(registry.get(v.getUuid())).ifPresent(r -> {
                                    r.happiness = value;
                                    r.lastSentHappiness = value;
                                    r.lastSentSick = r.sick;
                                    SurnameNetwork.sendHappiness(v, value, r.sick);
                                });
                            }
                            registry.markDirty();
                            return 1;
                        })))
                        .then(CommandManager.literal("links").executes(ctx -> {
                            ServerWorld world = ctx.getSource().getWorld();
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
                            ctx.getSource().sendFeedback(() -> Text.literal(text), false);
                            return 1;
                        }))
                        .then(CommandManager.literal("tree").executes(ctx -> {
                            ServerWorld world = ctx.getSource().getWorld();
                            VillagerEntityMCA youngest = world.getEntitiesByClass(VillagerEntityMCA.class, new Box(world.getSpawnPos()).expand(32), e -> true).stream()
                                    .min(java.util.Comparator.comparingInt(VillagerEntityMCA::getBreedingAge)).orElse(null);
                            if (youngest == null) {
                                ctx.getSource().sendError(Text.literal("Aucun villageois proche"));
                                return 0;
                            }
                            TreeProtocol.Response response = TreeServer.handle(world.getServer(), world, youngest.getUuid(),
                                    TreeProtocol.Request.tree(youngest.getUuid(), 2, 2));
                            StringBuilder out = new StringBuilder("Arbre de " + youngest.getName().getString() + " : " + response.persons().size() + " personnes\n");
                            for (FamilyPerson p : response.persons()) {
                                RelationInfo r = p.relationToViewer();
                                out.append(" - ").append(p.name()).append(" = ").append(r == null ? "?" : r.code() + "/" + r.count())
                                        .append(" conjoints=").append(p.spouses().size()).append(" enfants=").append(p.children().size()).append('\n');
                            }
                            String text = out.toString();
                            ctx.getSource().sendFeedback(() -> Text.literal(text), false);
                            return 1;
                        }))
                        .then(CommandManager.literal("job").then(CommandManager.argument("job", StringArgumentType.word()).executes(ctx -> {
                            ServerWorld world = ctx.getSource().getWorld();
                            String jobName = StringArgumentType.getString(ctx, "job").toUpperCase(java.util.Locale.ROOT);
                            Job job;
                            try {
                                job = Job.valueOf(jobName);
                            } catch (IllegalArgumentException e) {
                                ctx.getSource().sendError(Text.literal("Métier inconnu : " + jobName));
                                return 0;
                            }
                            BlockPos spawn = world.getSpawnPos();
                            world.getEntitiesByClass(VillagerEntityMCA.class, new Box(spawn).expand(32), v -> v.getBreedingAge() >= 0 && WorkManager.getJob(v) == Job.NONE).stream()
                                    .min(java.util.Comparator.comparingDouble(v -> v.squaredDistanceTo(spawn.getX(), spawn.getY(), spawn.getZ())))
                                    .ifPresent(v -> {
                                        WorkManager.setJob(v, job);
                                        ctx.getSource().sendFeedback(() -> Text.literal(v.getName().getString() + " -> " + job), false);
                                    });
                            return 1;
                        })))
                        .then(CommandManager.literal("depot").executes(ctx -> {
                            ServerWorld world = ctx.getSource().getWorld();
                            BlockPos chest = world.getSpawnPos().add(0, 0, 3);
                            world.setBlockState(chest, net.minecraft.block.Blocks.CHEST.getDefaultState());
                            for (VillagerEntityMCA v : world.getEntitiesByClass(VillagerEntityMCA.class, new Box(world.getSpawnPos()).expand(48), e -> WorkManager.getJob(e) != Job.NONE)) {
                                WorkManager.getRecord(v).depot = chest;
                                WorkManager.markDirty(v);
                            }
                            return 1;
                        }))
                        .then(CommandManager.literal("chest").executes(ctx -> {
                            ServerWorld world = ctx.getSource().getWorld();
                            StringBuilder out = new StringBuilder("Coffre : ");
                            if (world.getBlockEntity(world.getSpawnPos().add(0, 0, 3)) instanceof net.minecraft.inventory.Inventory inv) {
                                for (int i = 0; i < inv.size(); i++) {
                                    if (!inv.getStack(i).isEmpty()) {
                                        out.append(inv.getStack(i).getCount()).append(' ').append(inv.getStack(i).getItem()).append(", ");
                                    }
                                }
                            }
                            String text = out.toString();
                            ctx.getSource().sendFeedback(() -> Text.literal(text), false);
                            return 1;
                        }))
                        .then(CommandManager.literal("social")
                                .executes(ctx -> {
                                    ServerWorld world = ctx.getSource().getWorld();
                                    List<VillagerEntityMCA> near = nearestVillagers(world, ctx.getSource().getPosition(), 1);
                                    if (near.isEmpty()) {
                                        ctx.getSource().sendError(Text.literal("Aucun villageois proche"));
                                        return 0;
                                    }
                                    UUID owner = near.get(0).getUuid();
                                    StringBuilder out = new StringBuilder("Relations de " + socialName(world, owner) + " :\n");
                                    SocialRegistry.get(world.getServer()).links(owner).entrySet().stream()
                                            .sorted((a, b) -> Integer.compare(b.getValue().score, a.getValue().score))
                                            .forEach(e -> out.append(" - ").append(socialName(world, e.getKey())).append(" : ").append(e.getValue().score)
                                                    .append(e.getValue().family ? " (famille)" : "").append(", vu le jour ").append(e.getValue().lastSeen).append('\n'));
                                    String text = out.toString();
                                    ctx.getSource().sendFeedback(() -> Text.literal(text), false);
                                    return 1;
                                })
                                .then(CommandManager.literal("talk").executes(ctx -> {
                                    ServerWorld world = ctx.getSource().getWorld();
                                    List<VillagerEntityMCA> near = nearestVillagers(world, ctx.getSource().getPosition(), 2);
                                    if (near.size() < 2) {
                                        ctx.getSource().sendError(Text.literal("Il faut deux villageois proches"));
                                        return 0;
                                    }
                                    UUID a = near.get(0).getUuid();
                                    UUID b = near.get(1).getUuid();
                                    int delta = Social.talk(world, a, b, Social.Mode.FORCED);
                                    SocialRegistry registry = SocialRegistry.get(world.getServer());
                                    String text = socialName(world, a) + " et " + socialName(world, b) + (delta < 0 ? " se disputent" : " discutent")
                                            + " : " + Social.score(registry, a, b) + " / " + Social.score(registry, b, a);
                                    ctx.getSource().sendFeedback(() -> Text.literal(text), false);
                                    return 1;
                                }))
                                .then(CommandManager.literal("set").then(CommandManager.argument("note", IntegerArgumentType.integer(Social.MIN_SCORE, Social.MAX_SCORE)).executes(ctx -> {
                                    ServerWorld world = ctx.getSource().getWorld();
                                    List<VillagerEntityMCA> near = nearestVillagers(world, ctx.getSource().getPosition(), 2);
                                    if (near.size() < 2) {
                                        ctx.getSource().sendError(Text.literal("Il faut deux villageois proches"));
                                        return 0;
                                    }
                                    int note = IntegerArgumentType.getInteger(ctx, "note");
                                    UUID a = near.get(0).getUuid();
                                    UUID b = near.get(1).getUuid();
                                    SocialRegistry registry = SocialRegistry.get(world.getServer());
                                    long today = WorldCalendar.now(world).dayIndex();
                                    Social.change(world, a, b, note - Social.score(registry, a, b), today, true);
                                    Social.change(world, b, a, note - Social.score(registry, b, a), today, true);
                                    String text = socialName(world, a) + " et " + socialName(world, b) + " : " + Social.score(registry, a, b) + " / " + Social.score(registry, b, a);
                                    ctx.getSource().sendFeedback(() -> Text.literal(text), false);
                                    return 1;
                                })))
                                .then(CommandManager.literal("day").executes(ctx -> {
                                    ServerWorld world = ctx.getSource().getWorld();
                                    List<VillagerEntityMCA> near = nearestVillagers(world, ctx.getSource().getPosition(), 1);
                                    Optional<net.mca.server.world.data.Village> village = near.isEmpty() ? Optional.empty() : near.get(0).getResidency().getHomeVillage();
                                    if (village.isEmpty()) {
                                        ctx.getSource().sendError(Text.literal("Aucun village proche"));
                                        return 0;
                                    }
                                    Social.daily(village.get(), world, WorldCalendar.now(world).dayIndex());
                                    ctx.getSource().sendFeedback(() -> Text.literal("Journée simulée pour " + village.get().getName()), false);
                                    return 1;
                                })))
                        .then(CommandManager.literal("age").then(CommandManager.argument("years", IntegerArgumentType.integer(18, 120)).executes(ctx -> {
                            ServerWorld world = ctx.getSource().getWorld();
                            List<VillagerEntityMCA> near = nearestVillagers(world, ctx.getSource().getPosition(), 1);
                            if (near.isEmpty()) {
                                ctx.getSource().sendError(Text.literal("Aucun villageois proche"));
                                return 0;
                            }
                            VillagerEntityMCA v = near.get(0);
                            int years = IntegerArgumentType.getInteger(ctx, "years");
                            long today = WorldCalendar.now(world).dayIndex();
                            LifeRegistry.get(world.getServer()).setBirthDay(v.getUuid(), WorldCalendar.gameDayOf(WorldCalendar.calendarDay(today) - (long) Math.ceil(years * LifeCycle.DAYS_PER_YEAR) - 1));
                            com.maitreyoshi.mcavie.aging.Aging.refresh(v);
                            LifeCycle.update(v);
                            WorkManager.update(v);
                            int now = com.maitreyoshi.mcavie.aging.Aging.years(v);
                            ctx.getSource().sendFeedback(() -> Text.literal(v.getName().getString() + " a maintenant " + now + " ans (vitesse "
                                    + String.format("%.3f", v.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED)) + ", vie max " + v.getMaxHealth() + ", métier " + WorkManager.getJob(v) + ")"), false);
                            return 1;
                        })))
                        .then(CommandManager.literal("work").executes(ctx -> {
                            ServerWorld world = ctx.getSource().getWorld();
                            StringBuilder out = new StringBuilder("Heure de travail : " + WorkManager.isWorkTime(world) + "\n");
                            for (VillagerEntityMCA v : world.getEntitiesByClass(VillagerEntityMCA.class, new Box(world.getSpawnPos()).expand(48), e -> true)) {
                                Job job = WorkManager.getJob(v);
                                if (job == Job.NONE) {
                                    continue;
                                }
                                StringBuilder inv = new StringBuilder();
                                for (int i = 0; i < v.getInventory().size(); i++) {
                                    var s = v.getInventory().getStack(i);
                                    if (!s.isEmpty()) {
                                        inv.append(s.getCount()).append(' ').append(s.getItem()).append(", ");
                                    }
                                }
                                out.append(v.getName().getString()).append(" ").append(job).append(" travaille=").append(WorkManager.isWorking(v))
                                        .append(" activité=").append(v.getBrain().getFirstPossibleNonCoreActivity().map(Object::toString).orElse("?"))
                                        .append(" xp=").append(WorkManager.getRecord(v).experience)
                                        .append(" pos=").append(v.getBlockPos().toShortString())
                                        .append(" inv=[").append(inv).append("]\n");
                            }
                            String text = out.toString();
                            ctx.getSource().sendFeedback(() -> Text.literal(text), false);
                            return 1;
                        }))
                        .then(CommandManager.literal("graves").executes(ctx -> {
                            ServerWorld world = ctx.getSource().getWorld();
                            BlockPos center = world.getSpawnPos();
                            for (BlockPos p : BlockPos.iterate(center.add(-12, -4, -12), center.add(12, 4, 12))) {
                                TombstoneBlock.Data.of(world.getBlockEntity(p)).filter(TombstoneBlock.Data::hasEntity).ifPresent(tomb -> {
                                    String line = "Tombe en " + p.toShortString() + " : " + tomb.getEntityName().map(Text::getString).orElse("?");
                                    ctx.getSource().sendFeedback(() -> Text.literal(line + "\n"), false);
                                });
                            }
                            return 1;
                        }))
                        .then(CommandManager.literal("list").executes(ctx -> {
                            ServerWorld world = ctx.getSource().getWorld();
                            BlockPos pos = world.getSpawnPos();
                            SurnameRegistry registry = SurnameRegistry.get(world.getServer());
                            FamilyTree tree = FamilyTree.get(world);
                            for (VillagerEntityMCA v : world.getEntitiesByClass(VillagerEntityMCA.class, new Box(pos).expand(32), e -> true)) {
                                FamilyTreeNode node = v.getRelationships().getFamilyEntry();
                                String father = tree.getOrEmpty(node.father()).map(FamilyTreeNode::getName).orElse("?");
                                String mother = tree.getOrEmpty(node.mother()).map(FamilyTreeNode::getName).orElse("?");
                                Long birth = LifeRegistry.get(world.getServer()).getBirthDay(v.getUuid());
                                long today = WorldCalendar.now(world).dayIndex();
                                String age = birth == null ? "?" : LifeCycle.ageInYears(birth, today) + " ans, né en " + WorldCalendar.displayYearOfDay(birth);
                                String line = v.getTrackedValue(VillagerLike.VILLAGER_NAME)
                                        + " [" + v.getGenetics().getGender() + ", nom=" + registry.get(v.getUuid()) + "]"
                                        + " " + age + ", taille=" + v.getAgeState() + " (" + v.getBreedingAge() + ")"
                                        + Optional.ofNullable(WellbeingRegistry.get(world.getServer()).get(v.getUuid()))
                                        .map(r -> ", bonheur=" + Math.round(r.happiness) + (r.sick ? " MALADE" : "")).orElse(", bonheur=?")
                                        + " père=" + father + " mère=" + mother;
                                ctx.getSource().sendFeedback(() -> Text.literal(line), false);
                            }
                            return 1;
                        }))
        ));
    }

    private static List<VillagerEntityMCA> nearestVillagers(ServerWorld world, Vec3d pos, int count) {
        return world.getEntitiesByClass(VillagerEntityMCA.class, new Box(pos, pos).expand(32), VillagerEntityMCA::isAlive).stream()
                .sorted(Comparator.comparingDouble(v -> v.squaredDistanceTo(pos)))
                .limit(count)
                .toList();
    }

    private static String socialName(ServerWorld world, UUID uuid) {
        return Social.displayName(world, uuid);
    }
}
