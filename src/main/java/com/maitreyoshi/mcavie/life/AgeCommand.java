package com.maitreyoshi.mcavie.life;

import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.mca.entity.VillagerEntityMCA;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.mca.server.world.data.Village;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class AgeCommand {
    private static final int MAX_YEARS = 150;
    private static final double LOOK_RANGE = 32;
    private static final String FERTILITY = "command.mca_vie.age.fertility";

    private AgeCommand() {
    }

    public static void register() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("age").requires(s -> s.hasPermissionLevel(2))
                        .then(CommandManager.literal("set")
                                // L'entier doit rester avant le sélecteur : « 40 » serait aussi un nom de joueur valide.
                                .then(CommandManager.argument("years", IntegerArgumentType.integer(0, MAX_YEARS))
                                        .executes(ctx -> set(ctx, looked(ctx))))
                                .then(CommandManager.argument("targets", EntityArgumentType.entities())
                                        .then(CommandManager.argument("years", IntegerArgumentType.integer(0, MAX_YEARS))
                                                .executes(ctx -> set(ctx, selected(ctx))))))
                        .then(CommandManager.literal("get")
                                .executes(ctx -> get(ctx, looked(ctx)))
                                .then(CommandManager.argument("targets", EntityArgumentType.entities())
                                        .executes(ctx -> get(ctx, selected(ctx)))))
                        .then(CommandManager.literal("fertility")
                                .executes(ctx -> fertility(ctx, looked(ctx)))
                                .then(CommandManager.argument("targets", EntityArgumentType.entities())
                                        .executes(ctx -> fertility(ctx, selected(ctx)))))));
    }

    // diagnostic des conditions de grossesse MCA + addon (VillageProcreationManager, Pregnancy, PregnancyMixin)
    private static int fertility(CommandContext<ServerCommandSource> ctx, List<VillagerEntityMCA> villagers) {
        if (villagers.isEmpty()) {
            ctx.getSource().sendError(Text.translatable("command.mca_vie.age.no_villager"));
            return 0;
        }
        for (VillagerEntityMCA v : villagers) {
            if (!(v.getWorld() instanceof ServerWorld world)) {
                continue;
            }
            FamilyTreeNode node = v.getRelationships().getFamilyEntry();
            Long body = LifeRegistry.get(world.getServer()).getBodyBirthDay(v.getUuid());
            String age = body == null ? "?" : String.valueOf(LifeCycle.ageInYears(body, WorldCalendar.now(world).dayIndex()));
            Optional<Entity> partner = v.getRelationships().getPartner();
            Text partnerInfo = !FamilyTreeNode.isValid(node.partner()) ? Text.translatable(FERTILITY + ".no_partner")
                    : partner.map(p -> p instanceof VillagerEntityMCA pv
                            ? Text.translatable(FERTILITY + ".partner", pv.getName(), String.valueOf(pv.getGenetics().getGender()),
                            FamilyTree.get(world).getOrEmpty(node.partner())
                                    .<Text>map(n -> Text.translatable(FERTILITY + ".partner_tree", String.valueOf(n.gender())))
                                    .orElse(Text.empty()))
                            : Text.translatable(FERTILITY + ".not_villager", p.getName()))
                    .orElse(Text.translatable(FERTILITY + ".unloaded"));
            Optional<Village> home = v.getResidency().getHomeVillage();
            Text village = home.<Text>map(h -> Text.translatable(FERTILITY + ".village", h.getName(), h.getPopulation(), h.getMaxPopulation(),
                            Math.round(h.getPopulationThreshold() * 100) + "%",
                            Text.translatable(FERTILITY + (h.getPopulation() < h.getMaxPopulation() * h.getPopulationThreshold() ? ".ok" : ".full")),
                            h.getResidentsUUIDs().anyMatch(v.getUuid()::equals) ? Text.empty() : Text.translatable(FERTILITY + ".not_resident")))
                    .orElse(Text.translatable(FERTILITY + ".no_village"));
            Text text = Text.translatable(FERTILITY, v.getName(), String.valueOf(v.getGenetics().getGender()), String.valueOf(node.gender()),
                    age, LifeCycle.isMenopausal(v) ? Text.translatable(FERTILITY + ".menopause") : Text.empty(),
                    String.valueOf(node.getRelationshipState()), partnerInfo,
                    Text.translatable(v.getRelationships().getPregnancy().isPregnant() ? "gui.yes" : "gui.no"),
                    node.children().size(), village);
            ctx.getSource().sendFeedback(() -> text, false);
        }
        return villagers.size();
    }

    private static List<VillagerEntityMCA> selected(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Collection<? extends Entity> entities = EntityArgumentType.getEntities(ctx, "targets");
        List<VillagerEntityMCA> villagers = new ArrayList<>();
        for (Entity entity : entities) {
            if (entity instanceof VillagerEntityMCA villager) {
                villagers.add(villager);
            }
        }
        return villagers;
    }

    private static List<VillagerEntityMCA> looked(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        Vec3d eyes = player.getEyePos();
        Vec3d reach = player.getRotationVec(1f).multiply(LOOK_RANGE);
        HitResult block = player.raycast(LOOK_RANGE, 1f, false);
        double limit = block.getType() == HitResult.Type.MISS ? LOOK_RANGE * LOOK_RANGE : eyes.squaredDistanceTo(block.getPos());
        EntityHitResult hit = ProjectileUtil.raycast(player, eyes, eyes.add(reach), player.getBoundingBox().stretch(reach).expand(1),
                entity -> entity instanceof VillagerEntityMCA, limit);
        return hit != null && hit.getEntity() instanceof VillagerEntityMCA villager ? List.of(villager) : List.of();
    }

    private static int set(CommandContext<ServerCommandSource> ctx, List<VillagerEntityMCA> villagers) {
        if (villagers.isEmpty()) {
            ctx.getSource().sendError(Text.translatable("command.mca_vie.age.no_villager"));
            return 0;
        }
        int years = IntegerArgumentType.getInteger(ctx, "years");
        List<VillagerEntityMCA> changed = new ArrayList<>();
        for (VillagerEntityMCA villager : villagers) {
            if (villager.getWorld() instanceof ServerWorld world && LifeCycle.setAge(villager, world, years)) {
                changed.add(villager);
            } else {
                ctx.getSource().sendError(Text.translatable("command.mca_vie.age.too_young", villager.getDisplayName(), years));
            }
        }
        if (changed.size() == 1) {
            ctx.getSource().sendFeedback(() -> Text.translatable("command.mca_vie.age.set", changed.get(0).getDisplayName(), years), true);
        } else if (!changed.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.translatable("command.mca_vie.age.set_many", changed.size(), years), true);
        }
        return changed.size();
    }

    private static int get(CommandContext<ServerCommandSource> ctx, List<VillagerEntityMCA> villagers) {
        if (villagers.isEmpty()) {
            ctx.getSource().sendError(Text.translatable("command.mca_vie.age.no_villager"));
            return 0;
        }
        int found = 0;
        for (VillagerEntityMCA villager : villagers) {
            if (!(villager.getWorld() instanceof ServerWorld world)) {
                continue;
            }
            Long birth = LifeRegistry.get(world.getServer()).getBirthDay(villager.getUuid());
            if (birth == null) {
                ctx.getSource().sendError(Text.translatable("command.mca_vie.age.unknown", villager.getDisplayName()));
                continue;
            }
            int years = LifeCycle.ageInYears(birth, WorldCalendar.now(world).dayIndex());
            ctx.getSource().sendFeedback(() -> Text.translatable("command.mca_vie.age.get", villager.getDisplayName(), years), false);
            found++;
        }
        return found;
    }
}
