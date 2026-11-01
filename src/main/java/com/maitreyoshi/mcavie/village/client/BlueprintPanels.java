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
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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

    public static MutableText buildingName(String type) {
        if (VillageConfig.KEY_MAGNIFICENT.equals(type)) {
            return Text.translatable("gui.mca_vie.village.key.magnificent");
        }
        return Text.translatable("buildingType." + type);
    }

    public static int wrapped(DrawContext context, TextRenderer text, Text line, int x, int y, int width, int color) {
        for (OrderedText part : text.wrapLines(line, Math.max(40, width))) {
            context.drawTextWithShadow(text, part, x, y, color);
            y += LINE;
        }
        return y;
    }

    public static int renderPersonal(DrawContext context, TextRenderer text, Rank rank, int reputation,
                                     List<Task> tasks, Set<String> completed, int x, int y, int width) {
        if (rank == null) {
            return y;
        }
        y = wrapped(context, text, Text.translatable("gui.mca_vie.plan.title_now", Text.translatable(rank.getTranslationKey())), x, y, width, rank.ordinal() == 0 ? RED : GOLD);
        y = wrapped(context, text, Text.translatable("gui.mca_vie.plan.reputation", reputation), x, y, width, WHITE);
        if (tasks == null || tasks.isEmpty()) {
            return y;
        }
        y += 6;
        y = wrapped(context, text, Text.translatable("gui.mca_vie.village.personal_next", Text.translatable(rank.promote().getTranslationKey())), x, y, width, WHITE);
        for (Task task : tasks) {
            boolean done = completed != null && completed.contains(task.getId());
            MutableText line = Text.literal(done ? "✔ " : "✘ ").append(task.getTranslatable());
            y = wrapped(context, text, line, x + 4, y, width - 4, done ? GREEN : RED);
        }
        return y;
    }

    public static int renderRank(DrawContext context, TextRenderer text, Village village, int x, int y, int width) {
        VillageState state = VillageState.of(village);
        Prestige.Result result = Prestige.evaluateCached(village, state.rank);

        y = wrapped(context, text, Text.translatable("gui.mca_vie.village.rank", state.rank.getName()), x, y, width, GOLD);
        y += 2;
        y = wrapped(context, text, Text.translatable("gui.mca_vie.village.prestige", format(result.prestige())), x, y, width, WHITE);
        y = wrapped(context, text, Text.translatable("gui.mca_vie.village.residential",
                format(Math.min(result.residential(), result.residentialCap())), format(result.residentialCap())), x + 6, y, width - 6, GRAY);
        y = wrapped(context, text, Text.translatable("gui.mca_vie.village.population", result.population()), x, y, width, WHITE);

        if (state.descentDaysLeft >= 0) {
            y = wrapped(context, text, Text.translatable("gui.mca_vie.village.descent", state.descentDaysLeft), x, y, width, RED);
        }
        y += 6;

        VillageRank next = state.rank.next();
        if (next == null) {
            return wrapped(context, text, Text.translatable("gui.mca_vie.village.max"), x, y, width, GREEN);
        }
        VillageConfig.RankRule rule = next.rule();
        y = wrapped(context, text, Text.translatable("gui.mca_vie.village.next", next.getName()), x, y, width, WHITE);
        y = condition(context, text, x, y, width, result.prestige() >= rule.prestige,
                Text.translatable("gui.mca_vie.village.need.prestige", format(result.prestige()), rule.prestige));
        y = condition(context, text, x, y, width, result.population() >= rule.population,
                Text.translatable("gui.mca_vie.village.need.population", result.population(), rule.population));
        if (rule.keyBuilding != null && !rule.keyBuilding.isEmpty()) {
            y = condition(context, text, x, y, width, Prestige.hasKeyBuilding(village, next, result),
                    Text.translatable("gui.mca_vie.village.need.key", buildingName(rule.keyBuilding)));
        }
        return y;
    }

    private static int condition(DrawContext context, TextRenderer text, int x, int y, int width, boolean ok, MutableText line) {
        return wrapped(context, text, Text.literal(ok ? "✔ " : "✘ ").append(line), x + 4, y, width - 4, ok ? GREEN : RED);
    }

    public static void appendTooltip(List<Text> lines, Village village, Building building) {
        VillageState state = VillageState.of(village);
        Prestige.Score score = Prestige.evaluateCached(village, state.rank).score(building);
        if (score == null) {
            return;
        }
        VillageConfig.BuildingRule rule = score.rule();
        if (score.status() == Prestige.Status.NOT_RATED || rule == null) {
            lines.add(Text.translatable("gui.mca_vie.building.not_rated").formatted(Formatting.DARK_GRAY));
            return;
        }
        lines.add(Text.translatable("gui.mca_vie.building.points", format(score.points())).formatted(score.counts() ? Formatting.GOLD : Formatting.DARK_GRAY));
        lines.add(Text.translatable("gui.mca_vie.building.rank", rule.requiredRank().getName()).formatted(Formatting.GRAY));
        if (rule.minSurface > 0 && !building.getBuildingType().grouped()) {
            appendQuality(lines, building, score, rule);
        }
        BuildingQuality.Measure measured = BuildingQuality.measureOf(building);
        if (rule.minSurface > 0 && !building.getBuildingType().grouped() && (measured == null || measured.openings() == 0)) {
            lines.add(Text.translatable("gui.mca_vie.building.surface", score.surface()).formatted(Formatting.GRAY));
        }
        if (measured != null && measured.storeys() > 0 && !building.getBuildingType().grouped()) {
            lines.add(Text.translatable(measured.storeys() == 1 ? "gui.mca_vie.building.storey" : "gui.mca_vie.building.storeys", measured.storeys()).formatted(Formatting.GRAY));
        }
        if (rule.upkeep > 0) {
            lines.add(Text.translatable("gui.mca_vie.building.upkeep", rule.upkeep).formatted(Formatting.GRAY));
        }
        switch (score.status()) {
            case NEXT_RANK -> lines.add(Text.translatable("gui.mca_vie.building.next_rank", rule.requiredRank().getName()).formatted(Formatting.YELLOW));
            case TOO_HIGH -> lines.add(Text.translatable("gui.mca_vie.building.too_high", rule.requiredRank().getName()).formatted(Formatting.RED));
            case TOO_SMALL -> lines.add(Text.translatable("gui.mca_vie.building.too_small", rule.minSurface).formatted(Formatting.RED));
            case OVER_MAX -> lines.add(Text.translatable("gui.mca_vie.building.over_max").formatted(Formatting.RED));
            case DECAYED -> lines.add(Text.translatable("gui.mca_vie.building.decayed").formatted(Formatting.RED));
            default -> {
            }
        }
    }

    private static void appendQuality(List<Text> lines, Building building, Prestige.Score score, VillageConfig.BuildingRule rule) {
        BuildingQuality quality = score.quality();
        Formatting color = switch (quality) {
            case RUDIMENTARY -> Formatting.RED;
            case CORRECT -> Formatting.WHITE;
            case BEAUTIFUL -> Formatting.AQUA;
            case MAGNIFICENT -> Formatting.LIGHT_PURPLE;
        };
        lines.add(Text.translatable("gui.mca_vie.quality.line", quality.getName(), format(quality.multiplier())).formatted(color));
        BuildingQuality.Measure measure = BuildingQuality.measureOf(building);
        if (measure == null) {
            lines.add(Text.translatable("gui.mca_vie.quality.unmeasured").formatted(Formatting.DARK_GRAY));
            return;
        }
        lines.add(Text.translatable("gui.mca_vie.quality.measure", measure.lights(), measure.windows(), measure.decor()).formatted(Formatting.GRAY));
        if (measure.openings() > 0) {
            lines.add(Text.translatable("gui.mca_vie.quality.openings").formatted(Formatting.YELLOW));
        }
        VillageConfig config = VillageConfig.get();
        if (quality == BuildingQuality.RUDIMENTARY) {
            lines.add(Text.translatable("gui.mca_vie.quality.need_basic").formatted(Formatting.YELLOW));
        } else if (quality == BuildingQuality.CORRECT) {
            lines.add(missing(BuildingQuality.BEAUTIFUL, (int) Math.ceil(config.beautifulSurface * rule.minSurface) - score.surface(),
                    config.beautifulWindows - measure.windows(), config.beautifulDecor - measure.decor(), measure.openings() > 0));
        } else if (quality == BuildingQuality.BEAUTIFUL) {
            lines.add(missing(BuildingQuality.MAGNIFICENT, (int) Math.ceil(config.magnificentSurface * rule.minSurface) - score.surface(),
                    config.magnificentWindows - measure.windows(), config.magnificentDecor - measure.decor(), measure.openings() > 0));
        }
    }

    private static Text missing(BuildingQuality target, int surface, int windows, int decor, boolean open) {
        MutableText parts = Text.empty();
        int count = 0;
        for (MutableText part : new MutableText[]{
                surface > 0 ? Text.translatable("gui.mca_vie.quality.missing.surface", surface) : null,
                windows > 0 ? Text.translatable("gui.mca_vie.quality.missing.windows", windows) : null,
                decor > 0 ? Text.translatable("gui.mca_vie.quality.missing.decor", decor) : null,
                open ? Text.translatable("gui.mca_vie.quality.missing.open") : null}) {
            if (part != null) {
                if (count++ > 0) {
                    parts.append(", ");
                }
                parts.append(part);
            }
        }
        return Text.translatable("gui.mca_vie.quality.missing", target.getName(), parts).formatted(Formatting.YELLOW);
    }

    public static int renderCatalogInfo(DrawContext context, TextRenderer text, BuildingType type, int x, int y, int width) {
        VillageConfig.BuildingRule rule = VillageConfig.get().building(type.name());
        if (rule == null || rule.points <= 0) {
            return wrapped(context, text, Text.translatable("gui.mca_vie.building.not_rated"), x, y, width, GRAY);
        }
        context.drawTextWithShadow(text, Text.translatable("gui.mca_vie.building.rank", rule.requiredRank().getName()), x, y, GOLD);
        y += LINE;
        context.drawTextWithShadow(text, Text.translatable("gui.mca_vie.catalog.points", rule.points, rule.maxCount), x, y, WHITE);
        y += LINE;
        if (rule.minSurface > 0) {
            context.drawTextWithShadow(text, Text.translatable("gui.mca_vie.catalog.surface", rule.minSurface), x, y, WHITE);
            y += LINE;
            if (!type.grouped()) {
                VillageConfig config = VillageConfig.get();
                context.drawTextWithShadow(text, Text.translatable("gui.mca_vie.catalog.quality",
                        (int) Math.ceil(config.beautifulSurface * rule.minSurface), (int) Math.ceil(config.magnificentSurface * rule.minSurface)), x, y, GRAY);
                y += LINE;
            }
        }
        context.drawTextWithShadow(text, Text.translatable("gui.mca_vie.building.upkeep", rule.upkeep), x, y, WHITE);
        return y + LINE;
    }

    /** Texte du trésor (les boutons sont placés par l'écran) ; renvoie le bas du texte. */
    public static int renderTreasury(DrawContext context, TextRenderer text, Village village, int x, int y, int width) {
        VillageState state = VillageState.of(village);
        VillageConfig config = VillageConfig.get();
        Prestige.Result result = Prestige.evaluateCached(village, state.rank);

        y = wrapped(context, text, Text.translatable("gui.mca_vie.treasury.balance", state.treasury), x, y, width, state.treasury < 0 ? RED : GOLD);
        y += 2;
        if (!state.economyActive) {
            return wrapped(context, text, Text.translatable("gui.mca_vie.treasury.inactive"), x, y, width, GRAY);
        }
        y = wrapped(context, text, Text.translatable("gui.mca_vie.treasury.rate", VillageEconomy.appliedRate(village), Math.round(village.getTaxes() * 100)), x, y, width, WHITE);
        y = wrapped(context, text, Text.translatable("gui.mca_vie.treasury.upkeep", VillageEconomy.weeklyUpkeep(village, result)), x, y, width, WHITE);
        if (state.overdraftWeeks > 0) {
            y = wrapped(context, text, Text.translatable("gui.mca_vie.treasury.overdraft", state.overdraftWeeks, config.overdraftGraceWeeks), x, y, width, RED);
        }
        if (!state.decayed.isEmpty()) {
            long decayed = village.getBuildings().values().stream().filter(b -> state.isDecayed(village, b)).count();
            y = wrapped(context, text, Text.translatable("gui.mca_vie.treasury.decayed", decayed), x, y, width, RED);
        }
        y = wrapped(context, text, Text.translatable("gui.mca_vie.treasury.share", VillageEconomy.availableShare(state), config.playerSharePercent), x, y, width, GOLD);
        y += 4;
        y = wrapped(context, text, Text.translatable("gui.mca_vie.treasury.history"), x, y, width, GRAY);
        if (state.history.isEmpty()) {
            y = wrapped(context, text, Text.translatable("gui.mca_vie.treasury.no_history"), x + 6, y, width - 6, GRAY);
        }
        for (VillageState.WeekRecord week : state.history) {
            y = wrapped(context, text, Text.translatable("gui.mca_vie.treasury.week", week.week() + 1, week.income(), week.upkeep() + week.decrees(), week.withdrawn(), week.balance()), x + 6, y, width - 6, WHITE);
            if (week.nature() > 0 || week.deposited() > 0) {
                y = wrapped(context, text, Text.translatable("gui.mca_vie.treasury.week_extra", format(Math.round(week.nature() * 10) / 10.0), week.deposited()), x + 12, y, width - 12, GRAY);
            }
        }
        if (!state.journal.isEmpty()) {
            y += 4;
            for (VillageState.Withdrawal w : state.journal) {
                y = wrapped(context, text, Text.translatable(w.deposit() ? "gui.mca_vie.treasury.journal_deposit" : "gui.mca_vie.treasury.journal", w.player(), w.amount()), x + 6, y, width - 6, GRAY);
            }
        }
        return y;
    }

    public static List<String> marketProducts() {
        VillageConfig config = VillageConfig.get();
        return config.natureUnitsPerEmerald.keySet().stream()
                .filter(id -> config.unitsPerEmerald(id) > 0)
                .filter(id -> {
                    net.minecraft.util.Identifier parsed = net.minecraft.util.Identifier.tryParse(id);
                    return parsed != null && net.minecraft.registry.Registries.ITEM.containsId(parsed);
                })
                .toList();
    }

    private static net.minecraft.item.Item item(String id) {
        return net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.tryParse(id));
    }

    public static Text productLabel(String id) {
        VillageConfig config = VillageConfig.get();
        return Text.translatable("gui.mca_vie.market.product", config.unitsPerEmerald(id), item(id).getName(), config.orderLotPrice);
    }

    public static Text productTooltip(VillageState state, String id) {
        return Text.translatable("gui.mca_vie.market.product_tooltip", state.stock.getOrDefault(id, 0), VillageConfig.get().maxLotsPerOrder);
    }

    /** Une ligne du registre d'état civil. */
    public static MutableText registryLine(CivilRegistry.Entry e) {
        return switch (e.kind()) {
            case CivilRegistry.BIRTH -> e.other().isEmpty() ? Text.translatable("gui.mca_vie.registry.birth", e.name())
                    : Text.translatable("gui.mca_vie.registry.birth_of", e.name(), e.other());
            case CivilRegistry.MARRIAGE -> Text.translatable("gui.mca_vie.registry.marriage", e.name(), e.other());
            case CivilRegistry.JUSTICE -> e.age() > 0 ? Text.translatable("gui.mca_vie.registry.prison", e.name(), e.age())
                    : Text.translatable("gui.mca_vie.registry.pardon", e.name());
            default -> CivilRegistry.ZOMBIE.equals(e.other()) ? Text.translatable("gui.mca_vie.registry.zombie", e.name())
                    : e.age() < 0 ? Text.translatable("gui.mca_vie.registry.death", e.name())
                    : Text.translatable("gui.mca_vie.registry.death_age", e.name(), e.age());
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
