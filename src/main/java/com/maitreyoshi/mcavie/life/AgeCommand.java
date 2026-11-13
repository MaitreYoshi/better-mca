package com.maitreyoshi.mcavie.life;

import net.minecraft.server.permissions.Permissions;
import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
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
                Commands.literal("age").requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.literal("set")
                                // L'entier doit rester avant le sélecteur : « 40 » serait aussi un nom de joueur valide.
                                .then(Commands.argument("years", IntegerArgumentType.integer(0, MAX_YEARS))
                                        .executes(ctx -> set(ctx, looked(ctx))))
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .then(Commands.argument("years", IntegerArgumentType.integer(0, MAX_YEARS))
                                                .executes(ctx -> set(ctx, selected(ctx))))))
                        .then(Commands.literal("get")
                                .executes(ctx -> get(ctx, looked(ctx)))
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(ctx -> get(ctx, selected(ctx)))))
                        .then(Commands.literal("fertility")
                                .executes(ctx -> fertility(ctx, looked(ctx)))
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(ctx -> fertility(ctx, selected(ctx)))))));
    }

    // diagnostic des conditions de grossesse MCA + addon (VillageProcreationManager, Pregnancy, PregnancyMixin)
    private static int fertility(CommandContext<CommandSourceStack> ctx, List<VillagerEntityMCA> villagers) {
        if (villagers.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("command.mca_vie.age.no_villager"));
            return 0;
        }
        for (VillagerEntityMCA v : villagers) {
            if (!(v.level() instanceof ServerLevel world)) {
                continue;
            }
            FamilyTreeNode node = v.getRelationships().getFamilyEntry();
            Long body = LifeRegistry.get(world.getServer()).getBodyBirthDay(v.getUUID());
            String age = body == null ? "?" : String.valueOf(LifeCycle.ageInYears(body, WorldCalendar.now(world).dayIndex()));
            Optional<Entity> partner = v.getRelationships().getPartner();
            Component partnerInfo = !FamilyTreeNode.isValid(node.partner()) ? Component.translatable(FERTILITY + ".no_partner")
                    : partner.map(p -> p instanceof VillagerEntityMCA pv
                            ? Component.translatable(FERTILITY + ".partner", pv.getName(), String.valueOf(pv.getGenetics().getGender()),
                            FamilyTree.get(world).getOrEmpty(node.partner())
                                    .<Component>map(n -> Component.translatable(FERTILITY + ".partner_tree", String.valueOf(n.gender())))
                                    .orElse(Component.empty()))
                            : Component.translatable(FERTILITY + ".not_villager", p.getName()))
                    .orElse(Component.translatable(FERTILITY + ".unloaded"));
            Optional<Village> home = v.getResidency().getHomeVillage();
            Component village = home.<Component>map(h -> Component.translatable(FERTILITY + ".village", h.getName(), h.getPopulation(), h.getMaxPopulation(),
                            Math.round(h.getPopulationThreshold() * 100) + "%",
                            Component.translatable(FERTILITY + (h.getPopulation() < h.getMaxPopulation() * h.getPopulationThreshold() ? ".ok" : ".full")),
                            h.getResidentsUUIDs().anyMatch(v.getUUID()::equals) ? Component.empty() : Component.translatable(FERTILITY + ".not_resident")))
                    .orElse(Component.translatable(FERTILITY + ".no_village"));
            Component text = Component.translatable(FERTILITY, v.getName(), String.valueOf(v.getGenetics().getGender()), String.valueOf(node.gender()),
                    age, LifeCycle.isMenopausal(v) ? Component.translatable(FERTILITY + ".menopause") : Component.empty(),
                    String.valueOf(node.getRelationshipState()), partnerInfo,
                    Component.translatable(v.getRelationships().getPregnancy().isPregnant() ? "gui.yes" : "gui.no"),
                    node.children().size(), village);
            ctx.getSource().sendSuccess(() -> text, false);
        }
        return villagers.size();
    }

    private static List<VillagerEntityMCA> selected(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<? extends Entity> entities = EntityArgument.getEntities(ctx, "targets");
        List<VillagerEntityMCA> villagers = new ArrayList<>();
        for (Entity entity : entities) {
            if (entity instanceof VillagerEntityMCA villager) {
                villagers.add(villager);
            }
        }
        return villagers;
    }

    private static List<VillagerEntityMCA> looked(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Vec3 eyes = player.getEyePosition();
        Vec3 reach = player.getViewVector(1f).scale(LOOK_RANGE);
        HitResult block = player.pick(LOOK_RANGE, 1f, false);
        double limit = block.getType() == HitResult.Type.MISS ? LOOK_RANGE * LOOK_RANGE : eyes.distanceToSqr(block.getLocation());
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eyes, eyes.add(reach), player.getBoundingBox().expandTowards(reach).inflate(1),
                entity -> entity instanceof VillagerEntityMCA, limit);
        return hit != null && hit.getEntity() instanceof VillagerEntityMCA villager ? List.of(villager) : List.of();
    }

    private static int set(CommandContext<CommandSourceStack> ctx, List<VillagerEntityMCA> villagers) {
        if (villagers.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("command.mca_vie.age.no_villager"));
            return 0;
        }
        int years = IntegerArgumentType.getInteger(ctx, "years");
        List<VillagerEntityMCA> changed = new ArrayList<>();
        for (VillagerEntityMCA villager : villagers) {
            if (villager.level() instanceof ServerLevel world && LifeCycle.setAge(villager, world, years)) {
                changed.add(villager);
            } else {
                ctx.getSource().sendFailure(Component.translatable("command.mca_vie.age.too_young", villager.getDisplayName(), years));
            }
        }
        if (changed.size() == 1) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.mca_vie.age.set", changed.get(0).getDisplayName(), years), true);
        } else if (!changed.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.mca_vie.age.set_many", changed.size(), years), true);
        }
        return changed.size();
    }

    private static int get(CommandContext<CommandSourceStack> ctx, List<VillagerEntityMCA> villagers) {
        if (villagers.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("command.mca_vie.age.no_villager"));
            return 0;
        }
        int found = 0;
        for (VillagerEntityMCA villager : villagers) {
            if (!(villager.level() instanceof ServerLevel world)) {
                continue;
            }
            Long birth = LifeRegistry.get(world.getServer()).getBirthDay(villager.getUUID());
            if (birth == null) {
                ctx.getSource().sendFailure(Component.translatable("command.mca_vie.age.unknown", villager.getDisplayName()));
                continue;
            }
            int years = LifeCycle.ageInYears(birth, WorldCalendar.now(world).dayIndex());
            ctx.getSource().sendSuccess(() -> Component.translatable("command.mca_vie.age.get", villager.getDisplayName(), years), false);
            found++;
        }
        return found;
    }
}
