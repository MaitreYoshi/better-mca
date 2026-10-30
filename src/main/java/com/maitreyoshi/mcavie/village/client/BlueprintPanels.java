package com.maitreyoshi.mcavie.village.client;

import com.maitreyoshi.mcavie.village.BuildingQuality;
import com.maitreyoshi.mcavie.village.Decree;
import com.maitreyoshi.mcavie.village.Prestige;
import com.maitreyoshi.mcavie.village.VillageDecrees;
import com.maitreyoshi.mcavie.village.VillageConfig;
import com.maitreyoshi.mcavie.village.VillageEconomy;
import com.maitreyoshi.mcavie.village.VillageRank;
import com.maitreyoshi.mcavie.village.VillageRights;
import com.maitreyoshi.mcavie.village.VillageState;
import net.mca.resources.Rank;
import net.mca.resources.data.BuildingType;
import net.mca.resources.data.tasks.Task;
import net.mca.server.world.data.Building;
import net.mca.server.world.data.Village;
import net.mca.util.localization.FlowingText;
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

    public static int columnWidth(int screenWidth, int x, int max) {
        return Math.min(max, screenWidth - x - 6);
    }

    public static void renderPersonal(DrawContext context, TextRenderer text, Village village, Rank rank, int reputation,
                                      List<Task> tasks, Set<String> completed, int x, int y, int width) {
        if (rank == null) {
            return;
        }
        context.drawTextWithShadow(text, Text.translatable("gui.mca_vie.village.personal"), x, y, GRAY);
        y += LINE + 2;
        y = wrapped(context, text, Text.translatable("gui.blueprint.currentRank", Text.translatable(rank.getTranslationKey())), x, y, width, rank.ordinal() == 0 ? RED : GOLD);
        y = wrapped(context, text, Text.translatable("gui.blueprint.reputation", String.valueOf(reputation)), x, y, width, WHITE);
        y = wrapped(context, text, Text.translatable("gui.blueprint.population", village.getPopulation(), village.getMaxPopulation()), x, y, width, WHITE);
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        y += 6;
        y = wrapped(context, text, Text.translatable("gui.mca_vie.village.personal_next", Text.translatable(rank.promote().getTranslationKey())), x, y, width, WHITE);
        for (Task task : tasks) {
            boolean done = completed != null && completed.contains(task.getId());
            MutableText line = Text.literal(done ? "✔ " : "✘ ").append(task.getTranslatable());
            y = wrapped(context, text, line, x + 4, y, width - 4, done ? GREEN : RED);
        }
    }

    public static void renderRank(DrawContext context, TextRenderer text, Village village, int x, int y, int width) {
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
            wrapped(context, text, Text.translatable("gui.mca_vie.village.max"), x, y, width, GREEN);
            return;
        }
        VillageConfig.RankRule rule = next.rule();
        y = wrapped(context, text, Text.translatable("gui.mca_vie.village.next", next.getName()), x, y, width, WHITE);
        y = condition(context, text, x, y, width, result.prestige() >= rule.prestige,
                Text.translatable("gui.mca_vie.village.need.prestige", format(result.prestige()), rule.prestige));
        y = condition(context, text, x, y, width, result.population() >= rule.population,
                Text.translatable("gui.mca_vie.village.need.population", result.population(), rule.population));
        if (rule.keyBuilding != null && !rule.keyBuilding.isEmpty()) {
            condition(context, text, x, y, width, Prestige.hasKeyBuilding(village, next, result),
                    Text.translatable("gui.mca_vie.village.need.key", buildingName(rule.keyBuilding)));
        }
    }

    private static int condition(DrawContext context, TextRenderer text, int x, int y, int width, boolean ok, MutableText line) {
        return wrapped(context, text, Text.literal(ok ? "✔ " : "✘ ").append(line), x + 4, y, width - 4, ok ? GREEN : RED);
    }

    public static void renderMapHeader(DrawContext context, TextRenderer text, Village village, int centerX, int y) {
        VillageState state = VillageState.of(village);
        Prestige.Result result = Prestige.evaluateCached(village, state.rank);
        context.drawCenteredTextWithShadow(text, Text.translatable("gui.mca_vie.village.header", state.rank.getName(), format(result.prestige())), centerX, y, GOLD);
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

    public static void renderCatalogInfo(DrawContext context, TextRenderer text, BuildingType type, int x, int y) {
        VillageConfig.BuildingRule rule = VillageConfig.get().building(type.name());
        if (rule == null || rule.points <= 0) {
            context.drawTextWithShadow(text, Text.translatable("gui.mca_vie.building.not_rated"), x, y, GRAY);
            return;
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
    }

    public static void renderTaxCap(DrawContext context, TextRenderer text, Village village, int centerX, int y, int width) {
        VillageState state = VillageState.of(village);
        int max = Math.round(VillageRights.maxTaxes(state.rank) * 100);
        int equilibrium = VillageEconomy.equilibrium(village, Prestige.evaluateCached(village, state.rank), Math.round(village.getTaxes() * 100));
        y = centered(context, text, Text.translatable("gui.mca_vie.rules.tax_cap", max, state.rank.getName()), centerX, y, width, GRAY);
        centered(context, text, Text.translatable("gui.mca_vie.rules.equilibrium", equilibrium), centerX, y, width, GRAY);
    }

    public static void renderNature(DrawContext context, TextRenderer text, Village village, int centerX, int y, int width) {
        VillageState state = VillageState.of(village);
        context.drawCenteredTextWithShadow(text, Text.translatable("gui.mca_vie.rules.nature"), centerX, y, WHITE);
        context.drawCenteredTextWithShadow(text, Text.literal(state.natureShare + " %"), centerX, y + 16, GOLD);
        y += 36;
        boolean storage = Prestige.hasActive(Prestige.evaluateCached(village, state.rank), village, "storage");
        Text hint = storage ? Text.translatable("gui.mca_vie.rules.nature_hint") : Text.translatable("gui.mca_vie.rules.nature_storage");
        centered(context, text, hint, centerX, y, width, storage ? GRAY : RED);
    }

    private static int centered(DrawContext context, TextRenderer text, Text line, int centerX, int y, int width, int color) {
        for (OrderedText part : text.wrapLines(line, width)) {
            context.drawTextWithShadow(text, part, centerX - text.getWidth(part) / 2, y, color);
            y += LINE;
        }
        return y;
    }

    public static void renderDecrees(DrawContext context, TextRenderer text, Village village, Rank personalRank, long today,
                                     java.util.Map<Decree, net.minecraft.client.gui.widget.ButtonWidget> buttons, int x, int y) {
        VillageState state = VillageState.of(village);
        boolean noble = personalRank != null && personalRank.isAtLeast(VillageRights.DECREES);
        for (Decree decree : Decree.values()) {
            MutableText reason = VillageDecrees.unavailable(village, decree, today);
            boolean active = state.activeDecrees.contains(decree);
            net.minecraft.client.gui.widget.ButtonWidget button = buttons.get(decree);
            if (button != null) {
                button.active = noble && reason == null;
                button.setMessage(decree.kind == Decree.Kind.TOGGLE && active
                        ? Text.translatable("gui.mca_vie.decree.stop", decree.getName()) : decree.getName());
            }
            MutableText status;
            int color;
            if (reason != null) {
                status = reason;
                color = GRAY;
            } else if (decree.kind == Decree.Kind.TOGGLE) {
                status = Text.translatable(active ? "gui.mca_vie.decree.active" : "gui.mca_vie.decree.weekly");
                color = active ? GREEN : WHITE;
            } else if (decree == Decree.RENOVATION) {
                status = Text.translatable("gui.mca_vie.decree.renovation_cost", VillageConfig.get().renovationWeeks);
                color = WHITE;
            } else {
                status = Text.translatable("gui.mca_vie.decree.cost", VillageDecrees.launchCost(village, decree));
                color = WHITE;
            }
            context.drawTextWithShadow(text, status, x, y + 6, color);
            y += 22;
        }
        if (!noble) {
            context.drawTextWithShadow(text, Text.translatable("gui.mca_vie.decree.rank_low",
                    Text.translatable(VillageRights.DECREES.getTranslationKey())), x - 130, y + 4, RED);
        }
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

    public static void renderMarket(DrawContext context, TextRenderer text, Village village, java.util.UUID player,
                                    int centerX, int y, int bottomX, int bottomY) {
        VillageState state = VillageState.of(village);
        VillageConfig config = VillageConfig.get();
        if (!state.marketActive) {
            context.drawCenteredTextWithShadow(text, Text.translatable("gui.mca_vie.market.inactive"), centerX, y, RED);
        } else {
            Text day = Text.translatable("calendar.mca_vie.day." + config.marketDayOfWeek);
            context.drawCenteredTextWithShadow(text, Text.translatable("gui.mca_vie.market.day", day, config.marketStartHour, config.marketEndHour), centerX, y, GOLD);
        }
        if (state.lastMarketSales > 0 || state.lastMarketOrders > 0) {
            context.drawCenteredTextWithShadow(text, Text.translatable("gui.mca_vie.market.last", state.lastMarketSales, state.lastMarketOrders), centerX, y + LINE, GRAY);
        }
        long waiting = state.orders.stream().filter(o -> o.player.equals(player) && !o.ready).count();
        long ready = state.orders.stream().filter(o -> o.player.equals(player) && o.ready).count();
        context.drawTextWithShadow(text, net.minecraft.util.Language.getInstance().reorder(
                text.trimToWidth(Text.translatable("gui.mca_vie.market.orders", waiting, ready), centerX + 49 - bottomX)), bottomX, bottomY, WHITE);
    }

    private static final int REGISTRY_LINES = 11;
    public static final int JUSTICE_ROWS = 4;

    private static void line(DrawContext context, TextRenderer text, Text line, int x, int y, int width, int color) {
        context.drawTextWithShadow(text, net.minecraft.util.Language.getInstance().reorder(text.trimToWidth(line, width)), x, y, color);
    }

    public static void renderJustice(DrawContext context, TextRenderer text, Village village, Rank personalRank, long today, int x, int y, int width) {
        VillageState state = VillageState.of(village);
        VillageConfig config = VillageConfig.get();
        line(context, text, state.prisonActive ? Text.translatable("gui.mca_vie.justice.prison_ok", config.prisonDays)
                : Text.translatable("gui.mca_vie.justice.no_prison", VillageConfig.get().building("prison") == null ? "" : VillageConfig.get().building("prison").requiredRank().getName()),
                x, y, width + 90, state.prisonActive ? GREEN : GRAY);
        y += LINE + 11;
        if (state.cases.isEmpty()) {
            line(context, text, Text.translatable("gui.mca_vie.justice.no_case"), x, y + 6, width, GRAY);
        }
        for (int i = 0; i < Math.min(JUSTICE_ROWS, state.cases.size()); i++) {
            VillageState.Case c = state.cases.get(i);
            long left = Math.max(0, c.day + config.judgmentDays - today);
            line(context, text, Text.translatable("gui.mca_vie.justice.case", c.name, c.amount, left), x, y + 6 + i * 22, width, WHITE);
        }
        y += JUSTICE_ROWS * 22 + 4;
        if (personalRank == null || !personalRank.isAtLeast(VillageRights.JUSTICE)) {
            line(context, text, Text.translatable("gui.mca_vie.justice.rank_low", Text.translatable(VillageRights.JUSTICE.getTranslationKey())), x, y, width + 90, RED);
            y += LINE;
        }
        line(context, text, Text.translatable("gui.mca_vie.justice.prisoners", state.prisoners.size()), x, y, width + 90, GOLD);
        y += LINE;
        int shown = 0;
        for (VillageState.Prisoner p : state.prisoners.values()) {
            if (shown++ >= 3) {
                break;
            }
            line(context, text, Text.translatable("gui.mca_vie.justice.prisoner", p.name, Math.max(0, p.releaseDay - today)), x + 6, y, width + 84, GRAY);
            y += LINE;
        }
        line(context, text, Text.translatable("gui.mca_vie.justice.hint", config.crimeHappiness), x, y + 4, width + 90, GRAY);
    }

    private static List<com.maitreyoshi.mcavie.village.CivilRegistry.Entry> registryEntries(Village village, int filter) {
        return VillageState.of(village).registry.stream().filter(e -> filter < 0 || e.kind() == filter).toList();
    }

    public static int registryPages(Village village, int filter) {
        return Math.max(1, (registryEntries(village, filter).size() + REGISTRY_LINES - 1) / REGISTRY_LINES);
    }

    public static void renderRegistry(DrawContext context, TextRenderer text, Village village, int filter, int page, long today, int x, int y, int width) {
        List<com.maitreyoshi.mcavie.village.CivilRegistry.Entry> entries = registryEntries(village, filter);
        if (entries.isEmpty()) {
            context.drawTextWithShadow(text, Text.translatable("gui.mca_vie.registry.empty"), x, y, GRAY);
        }
        for (int i = 0; i < REGISTRY_LINES; i++) {
            int index = page * REGISTRY_LINES + i;
            if (index >= entries.size()) {
                break;
            }
            com.maitreyoshi.mcavie.village.CivilRegistry.Entry e = entries.get(index);
            MutableText line = Text.empty().append(com.maitreyoshi.mcavie.tree.client.TreeTexts.date(e.day())).append(" : ");
            int color;
            switch (e.kind()) {
                case com.maitreyoshi.mcavie.village.CivilRegistry.BIRTH -> {
                    line.append(e.other().isEmpty() ? Text.translatable("gui.mca_vie.registry.birth", e.name())
                            : Text.translatable("gui.mca_vie.registry.birth_of", e.name(), e.other()));
                    color = GREEN;
                }
                case com.maitreyoshi.mcavie.village.CivilRegistry.MARRIAGE -> {
                    line.append(Text.translatable("gui.mca_vie.registry.marriage", e.name(), e.other()));
                    color = GOLD;
                }
                case com.maitreyoshi.mcavie.village.CivilRegistry.JUSTICE -> {
                    line.append(e.age() > 0 ? Text.translatable("gui.mca_vie.registry.prison", e.name(), e.age())
                            : Text.translatable("gui.mca_vie.registry.pardon", e.name()));
                    color = RED;
                }
                default -> {
                    line.append(com.maitreyoshi.mcavie.village.CivilRegistry.ZOMBIE.equals(e.other()) ? Text.translatable("gui.mca_vie.registry.zombie", e.name())
                            : e.age() < 0 ? Text.translatable("gui.mca_vie.registry.death", e.name())
                            : Text.translatable("gui.mca_vie.registry.death_age", e.name(), e.age()));
                    color = GRAY;
                }
            }
            context.drawTextWithShadow(text, net.minecraft.util.Language.getInstance().reorder(text.trimToWidth(line, width)), x, y + i * LINE, color);
        }
        int year = com.maitreyoshi.mcavie.calendar.WorldCalendar.displayYearOfDay(today);
        int[] counts = new int[3];
        for (com.maitreyoshi.mcavie.village.CivilRegistry.Entry e : VillageState.of(village).registry) {
            if (e.kind() >= 0 && e.kind() < 3 && com.maitreyoshi.mcavie.calendar.WorldCalendar.displayYearOfDay(e.day()) == year) {
                counts[e.kind()]++;
            }
        }
        context.drawTextWithShadow(text, Text.translatable("gui.mca_vie.registry.year", year, counts[0], counts[1], counts[2]),
                x + 46, y + REGISTRY_LINES * LINE + 18, WHITE);
        context.drawTextWithShadow(text, Text.translatable("gui.mca_vie.registry.page", page + 1, registryPages(village, filter)),
                x + 46, y + REGISTRY_LINES * LINE + 7, GRAY);
    }

    public static void renderTaxRoll(DrawContext context, TextRenderer text, Village village, int pageNumber, int x, int y) {
        VillageState state = VillageState.of(village);
        List<java.util.Map.Entry<java.util.UUID, String>> residents = village.getResidentNames().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByValue()).toList();
        for (int i = 0; i < 9; i++) {
            int index = i + pageNumber * 9;
            if (index >= residents.size()) {
                break;
            }
            VillageState.TaxEntry entry = state.taxRoll.get(residents.get(index).getKey());
            if (entry == null) {
                continue;
            }
            com.maitreyoshi.mcavie.work.Job job = com.maitreyoshi.mcavie.work.Job.values()[Math.max(0, Math.min(com.maitreyoshi.mcavie.work.Job.values().length - 1, entry.job()))];
            Text line = Text.translatable("gui.mca_vie.villagers.tax", job.getName(),
                    Text.translatable("job.mca_vie.level." + entry.level()), format(Math.round(entry.amount() * 10) / 10.0));
            context.drawTextWithShadow(text, line, x, y + i * 11, GRAY);
        }
    }

    public static void renderTreasury(DrawContext context, TextRenderer text, Village village, Rank personalRank, int x, int y) {
        if (personalRank == null || !personalRank.isAtLeast(VillageRights.VIEW_TREASURY)) {
            context.drawTextWithShadow(text, Text.translatable("gui.mca_vie.treasury.rank_low", Text.translatable(VillageRights.VIEW_TREASURY.getTranslationKey())), x, y, RED);
            return;
        }
        int top = y;
        VillageState state = VillageState.of(village);
        VillageConfig config = VillageConfig.get();
        Prestige.Result result = Prestige.evaluateCached(village, state.rank);

        context.drawTextWithShadow(text, Text.translatable("gui.mca_vie.treasury.balance", state.treasury), x, y, state.treasury < 0 ? RED : GOLD);
        y += LINE + 2;
        if (!state.economyActive) {
            for (Text line : FlowingText.wrap(Text.translatable("gui.mca_vie.treasury.inactive"), 260)) {
                context.drawTextWithShadow(text, line, x, y, GRAY);
                y += LINE;
            }
            return;
        }
        context.drawTextWithShadow(text, Text.translatable("gui.mca_vie.treasury.rate", VillageEconomy.appliedRate(village), Math.round(village.getTaxes() * 100)), x, y, WHITE);
        y += LINE;
        context.drawTextWithShadow(text, Text.translatable("gui.mca_vie.treasury.upkeep", VillageEconomy.weeklyUpkeep(village, result)), x, y, WHITE);
        y += LINE;
        if (state.overdraftWeeks > 0) {
            context.drawTextWithShadow(text, Text.translatable("gui.mca_vie.treasury.overdraft", state.overdraftWeeks, config.overdraftGraceWeeks), x, y, RED);
            y += LINE;
        }
        if (!state.decayed.isEmpty()) {
            long decayed = village.getBuildings().values().stream().filter(b -> state.isDecayed(village, b)).count();
            context.drawTextWithShadow(text, Text.translatable("gui.mca_vie.treasury.decayed", decayed), x, y, RED);
            y += LINE;
        }
        y += 4;

        context.drawTextWithShadow(text, Text.translatable("gui.mca_vie.treasury.history"), x, y, GRAY);
        y += LINE;
        if (state.history.isEmpty()) {
            context.drawTextWithShadow(text, Text.translatable("gui.mca_vie.treasury.no_history"), x + 6, y, GRAY);
            y += LINE;
        }
        for (VillageState.WeekRecord week : state.history.subList(0, Math.min(4, state.history.size()))) {
            if (y + LINE > top + 124) {
                break;
            }
            context.drawTextWithShadow(text, Text.translatable("gui.mca_vie.treasury.week", week.week() + 1, week.income(), week.upkeep() + week.decrees(), week.withdrawn(), week.balance()), x + 6, y, WHITE);
            y += LINE;
            if ((week.nature() > 0 || week.deposited() > 0) && y + LINE <= top + 124) {
                context.drawTextWithShadow(text, Text.translatable("gui.mca_vie.treasury.week_extra", format(Math.round(week.nature() * 10) / 10.0), week.deposited()), x + 12, y, GRAY);
                y += LINE;
            }
        }

        y = Math.max(y + 4, top + 128);
        context.drawTextWithShadow(text, Text.translatable("gui.mca_vie.treasury.share", VillageEconomy.availableShare(state), config.playerSharePercent), x, y, GOLD);
        y += LINE;
        if (personalRank.isAtLeast(VillageRights.WITHDRAW)) {
            context.drawTextWithShadow(text, Text.translatable("gui.mca_vie.treasury.withdraw"), x, y + 6, WHITE);
        } else {
            context.drawTextWithShadow(text, Text.translatable("gui.mca_vie.treasury.withdraw_rank", Text.translatable(VillageRights.WITHDRAW.getTranslationKey())), x, y + 6, GRAY);
        }
        y += 22;
        if (personalRank.isAtLeast(VillageRights.DEPOSIT)) {
            context.drawTextWithShadow(text, Text.translatable("gui.mca_vie.treasury.deposit"), x, y + 6, WHITE);
            y += 24;
        }
        for (VillageState.Withdrawal w : state.journal.subList(0, Math.min(2, state.journal.size()))) {
            context.drawTextWithShadow(text, Text.translatable(w.deposit() ? "gui.mca_vie.treasury.journal_deposit" : "gui.mca_vie.treasury.journal", w.player(), w.amount()), x + 6, y, GRAY);
            y += LINE;
        }
    }
}
