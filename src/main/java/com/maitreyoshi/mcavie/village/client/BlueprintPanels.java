package com.maitreyoshi.mcavie.village.client;

import com.maitreyoshi.mcavie.village.BuildingQuality;
import com.maitreyoshi.mcavie.village.CivilRegistry;
import com.maitreyoshi.mcavie.village.Prestige;
import com.maitreyoshi.mcavie.village.VillageConfig;
import com.maitreyoshi.mcavie.village.VillageEconomy;
import com.maitreyoshi.mcavie.village.VillageRank;
import com.maitreyoshi.mcavie.village.VillageState;
import net.mca.resources.Rank;
import net.mca.resources.data.BuildingType;
import net.mca.resources.data.tasks.Task;
import net.mca.server.world.data.Building;
import net.mca.server.world.data.Village;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import java.util.List;
import java.util.Set;

public final class BlueprintPanels {
    private static final int WHITE = 0xffffffff;
    private static final int GOLD = 0xffffcc33;
    private static final int GRAY = 0xffaaaaaa;
    private static final int GREEN = 0xff88ff88;
    private static final int RED = 0xffff5555;
    private static final int LINE = 11;

    private BlueprintPanels() {
    }

    public static String format(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    public static MutableComponent buildingName(String type) {
        if (VillageConfig.KEY_MAGNIFICENT.equals(type)) {
            return Component.translatable("gui.mca_vie.village.key.magnificent");
        }
        return Component.translatable("buildingType." + type);
    }

    public static int wrapped(GuiGraphics context, Font text, Component line, int x, int y, int width, int color) {
        for (FormattedCharSequence part : text.split(line, Math.max(40, width))) {
            context.drawString(text, part, x, y, color);
            y += LINE;
        }
        return y;
    }

    public static int renderPersonal(GuiGraphics context, Font text, Rank rank, int reputation,
                                     List<Task> tasks, Set<String> completed, int x, int y, int width) {
        if (rank == null) {
            return y;
        }
        y = wrapped(context, text, Component.translatable("gui.mca_vie.plan.title_now", Component.translatable(rank.getTranslationKey())), x, y, width, rank.ordinal() == 0 ? RED : GOLD);
        y = wrapped(context, text, Component.translatable("gui.mca_vie.plan.reputation", reputation), x, y, width, WHITE);
        if (tasks == null || tasks.isEmpty()) {
            return y;
        }
        y += 6;
        y = wrapped(context, text, Component.translatable("gui.mca_vie.village.personal_next", Component.translatable(rank.promote().getTranslationKey())), x, y, width, WHITE);
        for (Task task : tasks) {
            boolean done = completed != null && completed.contains(task.getId());
            MutableComponent line = Component.literal(done ? "✔ " : "✘ ").append(task.getTranslatable());
            y = wrapped(context, text, line, x + 4, y, width - 4, done ? GREEN : RED);
        }
        return y;
    }

    public static int renderRank(GuiGraphics context, Font text, Village village, int x, int y, int width) {
        VillageState state = VillageState.of(village);
        Prestige.Result result = Prestige.evaluateCached(village, state.rank);

        y = wrapped(context, text, Component.translatable("gui.mca_vie.village.rank", state.rank.getName()), x, y, width, GOLD);
        y += 2;
        y = wrapped(context, text, Component.translatable("gui.mca_vie.village.prestige", format(result.prestige())), x, y, width, WHITE);
        y = wrapped(context, text, Component.translatable("gui.mca_vie.village.prestige_hint"), x + 6, y, width - 6, GRAY);
        if (result.residential() > 0) {
            int share = (int) Math.round(Math.max(0, Math.min(0.99, VillageConfig.get().residentialShare)) * 100);
            boolean capped = result.residential() > result.residentialCap();
            y = wrapped(context, text, Component.translatable("gui.mca_vie.village.residential",
                    format(Math.min(result.residential(), result.residentialCap())), format(result.residential())), x + 6, y, width - 6, capped ? RED : GRAY);
            if (capped) {
                y = wrapped(context, text, Component.translatable("gui.mca_vie.village.residential_hint", share), x + 6, y, width - 6, GRAY);
            }
        }
        y += 2;
        y = wrapped(context, text, Component.translatable("gui.mca_vie.village.population", result.population()), x, y, width, WHITE);

        if (state.descentDaysLeft >= 0) {
            y = wrapped(context, text, Component.translatable("gui.mca_vie.village.descent", state.descentDaysLeft), x, y, width, RED);
        }
        y += 6;

        VillageRank next = state.rank.next();
        if (next == null) {
            return wrapped(context, text, Component.translatable("gui.mca_vie.village.max"), x, y, width, GREEN);
        }
        VillageConfig.RankRule rule = next.rule();
        y = wrapped(context, text, Component.translatable("gui.mca_vie.village.next", next.getName()), x, y, width, WHITE);
        y = condition(context, text, x, y, width, result.prestige() >= rule.prestige,
                Component.translatable("gui.mca_vie.village.need.prestige", format(result.prestige()), rule.prestige));
        y = condition(context, text, x, y, width, result.population() >= rule.population,
                Component.translatable("gui.mca_vie.village.need.population", result.population(), rule.population));
        if (rule.keyBuilding != null && !rule.keyBuilding.isEmpty()) {
            y = condition(context, text, x, y, width, Prestige.hasKeyBuilding(village, next, result),
                    Component.translatable("gui.mca_vie.village.need.key", buildingName(rule.keyBuilding)));
        }
        return y;
    }

    private static int condition(GuiGraphics context, Font text, int x, int y, int width, boolean ok, MutableComponent line) {
        return wrapped(context, text, Component.literal(ok ? "✔ " : "✘ ").append(line), x + 4, y, width - 4, ok ? GREEN : RED);
    }

    public static void appendTooltip(List<Component> lines, Village village, Building building) {
        VillageState state = VillageState.of(village);
        Prestige.Score score = Prestige.evaluateCached(village, state.rank).score(building);
        if (score == null) {
            return;
        }
        VillageConfig.BuildingRule rule = score.rule();
        if (score.status() == Prestige.Status.NOT_RATED || rule == null) {
            lines.add(Component.translatable("gui.mca_vie.building.not_rated").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        lines.add(Component.translatable("gui.mca_vie.building.points", format(score.points())).withStyle(score.counts() ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("gui.mca_vie.building.rank", rule.requiredRank().getName()).withStyle(ChatFormatting.GRAY));
        if (rule.minSurface > 0 && !building.getBuildingType().grouped()) {
            appendQuality(lines, building, score, rule);
        }
        BuildingQuality.Measure measured = BuildingQuality.measureOf(building);
        if (rule.minSurface > 0 && !building.getBuildingType().grouped()) {
            lines.add(Component.translatable("gui.mca_vie.building.surface", score.surface()).withStyle(ChatFormatting.GRAY));
        }
        if (measured != null && measured.storeys() > 0 && !building.getBuildingType().grouped()) {
            int upper = measured.storeys() - 1;
            lines.add((upper == 0 ? Component.translatable("gui.mca_vie.building.ground_floor")
                    : Component.translatable(upper == 1 ? "gui.mca_vie.building.storey" : "gui.mca_vie.building.storeys", upper)).withStyle(ChatFormatting.GRAY));
        }
        if (rule.upkeep > 0) {
            lines.add(Component.translatable("gui.mca_vie.building.upkeep", rule.upkeep).withStyle(ChatFormatting.GRAY));
        }
        switch (score.status()) {
            case NEXT_RANK -> lines.add(Component.translatable("gui.mca_vie.building.next_rank", rule.requiredRank().getName()).withStyle(ChatFormatting.YELLOW));
            case TOO_HIGH -> lines.add(Component.translatable("gui.mca_vie.building.too_high", rule.requiredRank().getName()).withStyle(ChatFormatting.RED));
            case TOO_SMALL -> lines.add(Component.translatable("gui.mca_vie.building.too_small", rule.minSurface).withStyle(ChatFormatting.RED));
            case OVER_MAX -> lines.add(Component.translatable("gui.mca_vie.building.over_max").withStyle(ChatFormatting.RED));
            case DECAYED -> lines.add(Component.translatable("gui.mca_vie.building.decayed").withStyle(ChatFormatting.RED));
            default -> {
            }
        }
    }

    private static void appendQuality(List<Component> lines, Building building, Prestige.Score score, VillageConfig.BuildingRule rule) {
        BuildingQuality quality = score.quality();
        ChatFormatting color = switch (quality) {
            case RUDIMENTARY -> ChatFormatting.RED;
            case CORRECT -> ChatFormatting.WHITE;
            case BEAUTIFUL -> ChatFormatting.AQUA;
            case MAGNIFICENT -> ChatFormatting.LIGHT_PURPLE;
        };
        lines.add(Component.translatable("gui.mca_vie.quality.line", quality.getName(), format(quality.multiplier())).withStyle(color));
        BuildingQuality.Measure measure = BuildingQuality.measureOf(building);
        if (measure == null) {
            lines.add(Component.translatable("gui.mca_vie.quality.unmeasured").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        lines.add(Component.translatable("gui.mca_vie.quality.measure", measure.lights(), measure.windows(), measure.decor()).withStyle(ChatFormatting.GRAY));
        VillageConfig config = VillageConfig.get();
        if (quality == BuildingQuality.RUDIMENTARY) {
            lines.add(Component.translatable("gui.mca_vie.quality.need_basic").withStyle(ChatFormatting.YELLOW));
        } else if (quality == BuildingQuality.CORRECT) {
            lines.add(missing(BuildingQuality.BEAUTIFUL, (int) Math.ceil(config.beautifulSurface * rule.minSurface) - score.surface(),
                    config.beautifulWindows - measure.windows(), config.beautifulDecor - measure.decor()));
        } else if (quality == BuildingQuality.BEAUTIFUL) {
            lines.add(missing(BuildingQuality.MAGNIFICENT, (int) Math.ceil(config.magnificentSurface * rule.minSurface) - score.surface(),
                    config.magnificentWindows - measure.windows(), config.magnificentDecor - measure.decor()));
        }
    }

    private static Component missing(BuildingQuality target, int surface, int windows, int decor) {
        MutableComponent parts = Component.empty();
        int count = 0;
        for (MutableComponent part : new MutableComponent[]{
                surface > 0 ? Component.translatable("gui.mca_vie.quality.missing.surface", surface) : null,
                windows > 0 ? Component.translatable("gui.mca_vie.quality.missing.windows", windows) : null,
                decor > 0 ? Component.translatable("gui.mca_vie.quality.missing.decor", decor) : null}) {
            if (part != null) {
                if (count++ > 0) {
                    parts.append(", ");
                }
                parts.append(part);
            }
        }
        return Component.translatable("gui.mca_vie.quality.missing", target.getName(), parts).withStyle(ChatFormatting.YELLOW);
    }

    public static int renderCatalogInfo(GuiGraphics context, Font text, BuildingType type, int x, int y, int width) {
        VillageConfig.BuildingRule rule = VillageConfig.get().building(type.name());
        if (rule == null || rule.points <= 0) {
            return wrapped(context, text, Component.translatable("gui.mca_vie.building.not_rated"), x, y, width, GRAY);
        }
        y = wrapped(context, text, Component.translatable("gui.mca_vie.catalog.points", rule.points), x, y, width, GOLD);
        y = wrapped(context, text, rule.maxCount <= 1 ? Component.translatable("gui.mca_vie.catalog.count_one")
                : Component.translatable("gui.mca_vie.catalog.count_many", rule.maxCount), x, y, width, GRAY);
        y = wrapped(context, text, Component.translatable("gui.mca_vie.catalog.rank", rule.requiredRank().getName()), x, y, width, GRAY);
        if (rule.minSurface > 0) {
            y = wrapped(context, text, Component.translatable("gui.mca_vie.catalog.surface", rule.minSurface), x, y, width, WHITE);
            if (!type.grouped()) {
                VillageConfig config = VillageConfig.get();
                y = wrapped(context, text, Component.translatable("gui.mca_vie.catalog.quality",
                        (int) Math.ceil(config.beautifulSurface * rule.minSurface), (int) Math.ceil(config.magnificentSurface * rule.minSurface)), x, y, width, GRAY);
            }
        }
        return wrapped(context, text, Component.translatable("gui.mca_vie.building.upkeep", rule.upkeep), x, y, width, WHITE);
    }

    public static int renderTreasury(GuiGraphics context, Font text, Village village, int x, int y, int width) {
        VillageState state = VillageState.of(village);
        VillageConfig config = VillageConfig.get();
        Prestige.Result result = Prestige.evaluateCached(village, state.rank);

        y = wrapped(context, text, Component.translatable("gui.mca_vie.treasury.balance", state.treasury), x, y, width, state.treasury < 0 ? RED : GOLD);
        y += 2;
        if (!state.economyActive) {
            return wrapped(context, text, Component.translatable("gui.mca_vie.treasury.inactive"), x, y, width, GRAY);
        }
        y = wrapped(context, text, Component.translatable("gui.mca_vie.treasury.rate", VillageEconomy.appliedRate(village), Math.round(village.getTaxes() * 100)), x, y, width, WHITE);
        y = wrapped(context, text, Component.translatable("gui.mca_vie.treasury.upkeep", VillageEconomy.weeklyUpkeep(village, result)), x, y, width, WHITE);
        if (state.overdraftWeeks > 0) {
            y = wrapped(context, text, Component.translatable("gui.mca_vie.treasury.overdraft", state.overdraftWeeks, config.overdraftGraceWeeks), x, y, width, RED);
        }
        if (!state.decayed.isEmpty()) {
            long decayed = village.getBuildings().values().stream().filter(b -> state.isDecayed(village, b)).count();
            y = wrapped(context, text, Component.translatable("gui.mca_vie.treasury.decayed", decayed), x, y, width, RED);
        }
        y = wrapped(context, text, Component.translatable("gui.mca_vie.treasury.share", VillageEconomy.availableShare(state), config.playerSharePercent), x, y, width, GOLD);
        y += 4;
        y = wrapped(context, text, Component.translatable("gui.mca_vie.treasury.history"), x, y, width, GRAY);
        if (state.history.isEmpty()) {
            y = wrapped(context, text, Component.translatable("gui.mca_vie.treasury.no_history"), x + 6, y, width - 6, GRAY);
        }
        for (VillageState.WeekRecord week : state.history) {
            y = wrapped(context, text, Component.translatable("gui.mca_vie.treasury.week", week.week() + 1, week.income(), week.upkeep() + week.decrees(), week.withdrawn(), week.balance()), x + 6, y, width - 6, WHITE);
            if (week.nature() > 0 || week.deposited() > 0) {
                y = wrapped(context, text, Component.translatable("gui.mca_vie.treasury.week_extra", format(Math.round(week.nature() * 10) / 10.0), week.deposited()), x + 12, y, width - 12, GRAY);
            }
        }
        if (!state.journal.isEmpty()) {
            y += 4;
            for (VillageState.Withdrawal w : state.journal) {
                y = wrapped(context, text, Component.translatable(w.deposit() ? "gui.mca_vie.treasury.journal_deposit" : "gui.mca_vie.treasury.journal", w.player(), w.amount(),
                        com.maitreyoshi.mcavie.tree.client.TreeTexts.date(w.day())), x + 6, y, width - 6, GRAY);
            }
        }
        return y;
    }

    public static List<String> marketProducts() {
        VillageConfig config = VillageConfig.get();
        return config.natureUnitsPerEmerald.keySet().stream()
                .filter(id -> config.unitsPerEmerald(id) > 0)
                .filter(id -> {
                    net.minecraft.resources.ResourceLocation parsed = net.minecraft.resources.ResourceLocation.tryParse(id);
                    return parsed != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(parsed);
                })
                .toList();
    }

    private static net.minecraft.world.item.Item item(String id) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.tryParse(id));
    }

    public static Component productLabel(String id) {
        VillageConfig config = VillageConfig.get();
        return Component.translatable("gui.mca_vie.market.product", config.unitsPerEmerald(id), item(id).getDescription(), config.orderLotPrice);
    }

    public static Component productTooltip(VillageState state, String id) {
        return Component.translatable("gui.mca_vie.market.product_tooltip", state.stock.getOrDefault(id, 0), VillageConfig.get().maxLotsPerOrder);
    }

    public static MutableComponent registryLine(CivilRegistry.Entry e) {
        return switch (e.kind()) {
            case CivilRegistry.BIRTH -> e.other().isEmpty() ? Component.translatable("gui.mca_vie.registry.birth", e.name())
                    : Component.translatable("gui.mca_vie.registry.birth_of", e.name(), e.other());
            case CivilRegistry.MARRIAGE -> Component.translatable("gui.mca_vie.registry.marriage", e.name(), e.other());
            case CivilRegistry.JUSTICE -> e.age() > 0 ? Component.translatable("gui.mca_vie.registry.prison", e.name(), e.age())
                    : Component.translatable("gui.mca_vie.registry.pardon", e.name());
            default -> CivilRegistry.ZOMBIE.equals(e.other()) ? Component.translatable("gui.mca_vie.registry.zombie", e.name())
                    : e.age() < 0 ? Component.translatable("gui.mca_vie.registry.death", e.name())
                    : Component.translatable("gui.mca_vie.registry.death_age", e.name(), e.age());
        };
    }

    public static int registryColor(CivilRegistry.Entry e) {
        return switch (e.kind()) {
            case CivilRegistry.BIRTH -> GREEN;
            case CivilRegistry.MARRIAGE -> GOLD;
            case CivilRegistry.JUSTICE -> RED;
            default -> WHITE;
        };
    }
}
