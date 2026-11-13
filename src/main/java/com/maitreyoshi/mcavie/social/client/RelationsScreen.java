package com.maitreyoshi.mcavie.social.client;

import com.maitreyoshi.mcavie.social.Social;
import com.maitreyoshi.mcavie.social.SocialNetwork;
import com.maitreyoshi.mcavie.social.client.RelationsClient.Entry;
import com.maitreyoshi.mcavie.social.client.RelationsClient.Relations;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public class RelationsScreen extends Screen {
    private static final ResourceLocation ICONS = new ResourceLocation("textures/gui/icons.png");
    private static final int ROW = 14;
    private static final int HALF_WIDTH = 150;
    private static final int TOP = 40;
    private static final int HEARTS = 5;

    private final UUID villager;
    private final Component villagerName;
    @Nullable
    private final Screen root;
    @Nullable
    private final RelationsScreen previousScreen;
    private int page;
    private long requestedAt;
    private Button previous;
    private Button next;

    private record Row(@Nullable Entry entry, int index) {
    }

    public RelationsScreen(Entity villager, @Nullable Screen parent) {
        this(villager.getUUID(), villager.getName(), parent);
    }

    public RelationsScreen(UUID villager, Component name, @Nullable Screen parent) {
        super(Component.translatable("gui.mca_vie.relations.title", name));
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
        previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> page--)
                .bounds(width / 2 - HALF_WIDTH, bottom, 20, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), b -> page++)
                .bounds(width / 2 + HALF_WIDTH - 20, bottom, 20, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(width / 2 - 50, bottom, 100, 20).build());
        if (previousScreen != null) {
            String name = font.plainSubstrByWidth(previousScreen.villagerName.getString(), 90);
            addRenderableWidget(Button.builder(Component.literal("‹ " + name), b -> {
                if (minecraft != null) {
                    minecraft.setScreen(previousScreen);
                }
            }).bounds(8, 8, font.width("‹ " + name) + 12, 20).build());
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
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);
        Relations relations = RelationsClient.get(villager);
        List<Row> rows = visibleRows(relations);
        super.render(context, mouseX, mouseY, delta);

        if (relations == null) {
            if (System.currentTimeMillis() - requestedAt > 1000) {
                RelationsClient.request(villager);
                requestedAt = System.currentTimeMillis();
            }
            context.drawCenteredString(font, Component.translatable("gui.mca_vie.relations.loading"), width / 2, TOP + 10, 0xAAAAAA);
            return;
        }
        if (relations.entries().isEmpty()) {
            context.drawCenteredString(font, Component.translatable("gui.mca_vie.relations.empty", villagerName), width / 2, TOP + 10, 0xAAAAAA);
            return;
        }
        int pages = pages(relations);
        if (pages > 1) {
            context.drawCenteredString(font, Component.translatable("gui.mca_vie.relations.page", page + 1, pages), width / 2, height - 40, 0xAAAAAA);
        }

        int left = width / 2 - HALF_WIDTH;
        Entry hovered = null;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int y = TOP + i * ROW;
            if (row.entry() == null) {
                context.drawString(font, Component.translatable("gui.mca_vie.relations.grudges").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), left, y + 3, 0xFFFFFF);
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
                context.drawString(font, Component.literal("★").withStyle(ChatFormatting.GOLD), x, y + 3, 0xFFFFFF);
            }
            x += 10;
            int limit = left + HALF_WIDTH * 2 - HEARTS * 9 - 6;
            String trimmed = font.plainSubstrByWidth(entry.name(), limit - x);
            MutableComponent name = Component.literal(trimmed).withStyle(over && entry.openable() ? ChatFormatting.YELLOW : ChatFormatting.WHITE);
            context.drawString(font, name, x, y + 3, 0xFFFFFF);
            x += font.width(name) + 6;
            if (entry.tag() != SocialNetwork.TAG_NONE) {
                MutableComponent tag = tag(entry).withStyle(ChatFormatting.GRAY);
                if (x + font.width(tag) <= limit) {
                    context.drawString(font, tag, x, y + 3, 0xFFFFFF);
                }
            }
            drawHearts(context, left + HALF_WIDTH * 2 - HEARTS * 9, y + 2, entry.score());
        }

        if (hovered != null) {
            context.renderComponentTooltip(font, tooltip(hovered, relations.today()), mouseX, mouseY);
        }
    }

    private static MutableComponent tag(Entry entry) {
        String key = "gui.mca_vie.relations.tag." + entry.tag();
        return Component.translatable(entry.female() ? key + ".female" : key);
    }

    private static void drawHearts(GuiGraphics context, int x, int y, int score) {
        int halves = Math.round(Math.min(100, Math.abs(score)) / 10f);
        int full = score >= 0 ? 52 : 124;
        for (int i = 0; i < HEARTS; i++) {
            int hx = x + i * 9;
            context.blit(ICONS, hx, y, 16, 0, 9, 9);
            if (halves >= i * 2 + 2) {
                context.blit(ICONS, hx, y, full, 0, 9, 9);
            } else if (halves == i * 2 + 1) {
                context.blit(ICONS, hx, y, full + 9, 0, 9, 9);
            }
        }
    }

    private List<Component> tooltip(Entry entry, long today) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(entry.name()).withStyle(ChatFormatting.WHITE));
        lines.add(Component.translatable(entry.score() >= 0 ? "gui.mca_vie.relations.affinity" : "gui.mca_vie.relations.grudge", Math.abs(entry.score()))
                .withStyle(entry.score() >= 0 ? ChatFormatting.RED : ChatFormatting.DARK_GRAY));
        long days = today - entry.lastSeen();
        lines.add((days <= 0 ? Component.translatable("gui.mca_vie.relations.seen_today")
                : days == 1 ? Component.translatable("gui.mca_vie.relations.seen_yesterday")
                : Component.translatable("gui.mca_vie.relations.seen_days", days)).withStyle(ChatFormatting.GRAY));
        if (entry.openable()) {
            lines.add(Component.translatable("gui.mca_vie.relations.click").withStyle(ChatFormatting.YELLOW));
        }
        return lines;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        Relations relations = RelationsClient.get(villager);
        if (button != 0 || relations == null || minecraft == null || minecraft.level == null) {
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
        minecraft.setScreen(new RelationsScreen(entry.uuid(), Component.literal(entry.name()), this));
        return true;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(root);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
