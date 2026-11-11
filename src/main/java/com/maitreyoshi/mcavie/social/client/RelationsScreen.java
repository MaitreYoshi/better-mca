package com.maitreyoshi.mcavie.social.client;

import com.maitreyoshi.mcavie.social.Social;
import com.maitreyoshi.mcavie.social.SocialNetwork;
import com.maitreyoshi.mcavie.social.client.RelationsClient.Entry;
import com.maitreyoshi.mcavie.social.client.RelationsClient.Relations;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.Entity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RelationsScreen extends Screen {
    private static final Identifier ICONS = new Identifier("textures/gui/icons.png");
    private static final int ROW = 14;
    private static final int HALF_WIDTH = 150;
    private static final int TOP = 40;
    private static final int HEARTS = 5;

    private final UUID villager;
    private final Text villagerName;
    @Nullable
    private final Screen root;
    @Nullable
    private final RelationsScreen previousScreen;
    private int page;
    private long requestedAt;
    private ButtonWidget previous;
    private ButtonWidget next;

    private record Row(@Nullable Entry entry, int index) {
    }

    public RelationsScreen(Entity villager, @Nullable Screen parent) {
        this(villager.getUuid(), villager.getName(), parent);
    }

    public RelationsScreen(UUID villager, Text name, @Nullable Screen parent) {
        super(Text.translatable("gui.mca_vie.relations.title", name));
        this.villager = villager;
        this.villagerName = name;
        this.previousScreen = parent instanceof RelationsScreen relations ? relations : null;
        this.root = previousScreen != null ? previousScreen.root : parent;
    }

    @Override
    protected void init() {
        RelationsClient.request(villager);
        requestedAt = System.currentTimeMillis();
        int bottom = height - 28;
        previous = addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> page--)
                .dimensions(width / 2 - HALF_WIDTH, bottom, 20, 20).build());
        next = addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> page++)
                .dimensions(width / 2 + HALF_WIDTH - 20, bottom, 20, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"), b -> close())
                .dimensions(width / 2 - 50, bottom, 100, 20).build());
        if (previousScreen != null) {
            String name = textRenderer.trimToWidth(previousScreen.villagerName.getString(), 90);
            addDrawableChild(ButtonWidget.builder(Text.literal("‹ " + name), b -> {
                if (client != null) {
                    client.setScreen(previousScreen);
                }
            }).dimensions(8, 8, textRenderer.getWidth("‹ " + name) + 12, 20).build());
        }
    }

    private int rowsPerPage() {
        return Math.max(4, (height - TOP - 44) / ROW);
    }

    private static List<Row> rows(Relations relations) {
        List<Row> rows = new ArrayList<>();
        boolean grudges = false;
        for (int i = 0; i < relations.entries().size(); i++) {
            Entry entry = relations.entries().get(i);
            if (entry.score() < 0 && !grudges) {
                grudges = true;
                rows.add(new Row(null, i));
            }
            rows.add(new Row(entry, i));
        }
        return rows;
    }

    private int pages(Relations relations) {
        int perPage = rowsPerPage();
        return Math.max(1, (rows(relations).size() + perPage - 1) / perPage);
    }

    private List<Row> visibleRows(@Nullable Relations relations) {
        if (relations == null) {
            previous.active = false;
            next.active = false;
            return List.of();
        }
        List<Row> rows = rows(relations);
        int perPage = rowsPerPage();
        int pages = pages(relations);
        page = Math.max(0, Math.min(page, pages - 1));
        previous.active = page > 0;
        next.active = page < pages - 1;
        return rows.subList(page * perPage, Math.min(rows.size(), (page + 1) * perPage));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 16, 0xFFFFFF);
        Relations relations = RelationsClient.get(villager);
        List<Row> rows = visibleRows(relations);
        super.render(context, mouseX, mouseY, delta);

        if (relations == null) {
            if (System.currentTimeMillis() - requestedAt > 1000) {
                RelationsClient.request(villager);
                requestedAt = System.currentTimeMillis();
            }
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.mca_vie.relations.loading"), width / 2, TOP + 10, 0xAAAAAA);
            return;
        }
        if (relations.entries().isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.mca_vie.relations.empty", villagerName), width / 2, TOP + 10, 0xAAAAAA);
            return;
        }
        int pages = pages(relations);
        if (pages > 1) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.mca_vie.relations.page", page + 1, pages), width / 2, height - 40, 0xAAAAAA);
        }

        int left = width / 2 - HALF_WIDTH;
        Entry hovered = null;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int y = TOP + i * ROW;
            if (row.entry() == null) {
                context.drawTextWithShadow(textRenderer, Text.translatable("gui.mca_vie.relations.grudges").formatted(Formatting.DARK_RED, Formatting.BOLD), left, y + 3, 0xFFFFFF);
                continue;
            }
            Entry entry = row.entry();
            boolean over = mouseX >= left && mouseX < left + HALF_WIDTH * 2 && mouseY >= y && mouseY < y + ROW;
            if (over) {
                context.fill(left - 2, y, left + HALF_WIDTH * 2 + 2, y + ROW, 0x33FFFFFF);
                hovered = entry;
            }
            int x = left;
            if (row.index() == 0 && entry.score() >= Social.FRIEND) {
                context.drawTextWithShadow(textRenderer, Text.literal("★").formatted(Formatting.GOLD), x, y + 3, 0xFFFFFF);
            }
            x += 10;
            int limit = left + HALF_WIDTH * 2 - HEARTS * 9 - 6;
            String trimmed = textRenderer.trimToWidth(entry.name(), limit - x);
            MutableText name = Text.literal(trimmed).formatted(over && entry.openable() ? Formatting.YELLOW : Formatting.WHITE);
            context.drawTextWithShadow(textRenderer, name, x, y + 3, 0xFFFFFF);
            x += textRenderer.getWidth(name) + 6;
            if (entry.tag() != SocialNetwork.TAG_NONE) {
                MutableText tag = tag(entry).formatted(Formatting.GRAY);
                if (x + textRenderer.getWidth(tag) <= limit) {
                    context.drawTextWithShadow(textRenderer, tag, x, y + 3, 0xFFFFFF);
                }
            }
            drawHearts(context, left + HALF_WIDTH * 2 - HEARTS * 9, y + 2, entry.score());
        }

        if (hovered != null) {
            context.drawTooltip(textRenderer, tooltip(hovered, relations.today()), mouseX, mouseY);
        }
    }

    private static MutableText tag(Entry entry) {
        String key = "gui.mca_vie.relations.tag." + entry.tag();
        return Text.translatable(entry.female() ? key + ".female" : key);
    }

    private static void drawHearts(DrawContext context, int x, int y, int score) {
        int halves = Math.round(Math.min(100, Math.abs(score)) / 10f);
        int full = score >= 0 ? 52 : 124;
        for (int i = 0; i < HEARTS; i++) {
            int hx = x + i * 9;
            context.drawTexture(ICONS, hx, y, 16, 0, 9, 9);
            if (halves >= i * 2 + 2) {
                context.drawTexture(ICONS, hx, y, full, 0, 9, 9);
            } else if (halves == i * 2 + 1) {
                context.drawTexture(ICONS, hx, y, full + 9, 0, 9, 9);
            }
        }
    }

    private List<Text> tooltip(Entry entry, long today) {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.literal(entry.name()).formatted(Formatting.WHITE));
        lines.add(Text.translatable(entry.score() >= 0 ? "gui.mca_vie.relations.affinity" : "gui.mca_vie.relations.grudge", Math.abs(entry.score()))
                .formatted(entry.score() >= 0 ? Formatting.RED : Formatting.DARK_GRAY));
        long days = today - entry.lastSeen();
        lines.add((days <= 0 ? Text.translatable("gui.mca_vie.relations.seen_today")
                : days == 1 ? Text.translatable("gui.mca_vie.relations.seen_yesterday")
                : Text.translatable("gui.mca_vie.relations.seen_days", days)).formatted(Formatting.GRAY));
        if (entry.openable()) {
            lines.add(Text.translatable("gui.mca_vie.relations.click").formatted(Formatting.YELLOW));
        }
        return lines;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        Relations relations = RelationsClient.get(villager);
        if (button != 0 || relations == null || client == null || client.world == null) {
            return false;
        }
        int left = width / 2 - HALF_WIDTH;
        List<Row> rows = visibleRows(relations);
        int i = (int) Math.floor((mouseY - TOP) / ROW);
        if (mouseX < left || mouseX >= left + HALF_WIDTH * 2 || mouseY < TOP || i < 0 || i >= rows.size()) {
            return false;
        }
        Entry entry = rows.get(i).entry();
        if (entry == null || !entry.openable()) {
            return false;
        }
        client.setScreen(new RelationsScreen(entry.uuid(), Text.literal(entry.name()), this));
        return true;
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(root);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
