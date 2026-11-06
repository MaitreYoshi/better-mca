package com.maitreyoshi.mcavie.book;

import net.mca.client.book.pages.Page;
import net.mca.client.gui.ExtendedBookScreen;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionUtil;
import net.minecraft.registry.Registries;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;

import java.util.ArrayList;
import java.util.List;

/** Page de livre MCA avec un titre, des lignes « icône + texte + valeur » et une grille d'icônes. */
public class IllustratedPage extends Page {
    private static final int WIDTH = 114;
    private static final int ROW_HEIGHT = 19;
    private static final int LINE_HEIGHT = 11;
    private static final int LAST_LINE_Y = 148;
    private static final int TITLE_COLOR = 0x6B3A0E;
    private static final int TEXT_COLOR = 0x2B1D0E;
    private static final int NOTE_COLOR = 0x6E5A3C;
    private static final int GAIN_COLOR = 0x1E7A1E;
    private static final int LOSS_COLOR = 0xB01818;
    private static final int INFO_COLOR = 0x8A5A00;

    private record Row(String icon, String key, String value, int color) {
    }

    private final String titleKey;
    private final List<Row> rows = new ArrayList<>();
    private final List<String> grid = new ArrayList<>();
    private String noteKey;
    private String introKey;

    public IllustratedPage(String titleKey) {
        this.titleKey = titleKey;
    }

    public IllustratedPage gain(String icon, String key, int value) {
        rows.add(new Row(icon, key, "+" + value, GAIN_COLOR));
        return this;
    }

    public IllustratedPage loss(String icon, String key, int value) {
        rows.add(new Row(icon, key, "−" + Math.abs(value), LOSS_COLOR));
        return this;
    }

    public IllustratedPage good(String icon, String key, String value) {
        rows.add(new Row(icon, key, value, GAIN_COLOR));
        return this;
    }

    public IllustratedPage bad(String icon, String key, String value) {
        rows.add(new Row(icon, key, value, LOSS_COLOR));
        return this;
    }

    public IllustratedPage info(String icon, String key, String value) {
        rows.add(new Row(icon, key, value, INFO_COLOR));
        return this;
    }

    public IllustratedPage line(String key, String value) {
        rows.add(new Row(null, key, value, INFO_COLOR));
        return this;
    }

    public IllustratedPage intro(String key) {
        this.introKey = key;
        return this;
    }

    public IllustratedPage grid(String... icons) {
        grid.addAll(List.of(icons));
        return this;
    }

    public IllustratedPage note(String key) {
        this.noteKey = key;
        return this;
    }

    private static ItemStack stack(String id) {
        int at = id.indexOf('@');
        if (at < 0) {
            return new ItemStack(Registries.ITEM.get(new Identifier(id)));
        }
        ItemStack stack = new ItemStack(Registries.ITEM.get(new Identifier(id.substring(0, at))));
        return PotionUtil.setPotion(stack, Registries.POTION.get(new Identifier(id.substring(at + 1))));
    }

    @Override
    public void render(ExtendedBookScreen screen, DrawContext context, int mouseX, int mouseY, float delta) {
        TextRenderer font = screen.getTextRenderer();
        int left = (screen.width - 192) / 2 + 36;

        Text title = Text.translatable(titleKey).formatted(Formatting.BOLD);
        context.drawText(font, title, left + (WIDTH - font.getWidth(title)) / 2, 30, TITLE_COLOR, false);
        context.fill(left + 10, 41, left + WIDTH - 10, 42, 0x55000000 | TITLE_COLOR);

        int y = 46;
        if (introKey != null) {
            for (OrderedText line : font.wrapLines(Text.translatable(introKey), WIDTH)) {
                context.drawText(font, line, left, y, TEXT_COLOR, false);
                y += 9;
            }
            y += 4;
        }
        Row hovered = null;
        for (Row row : rows) {
            if (row.icon == null) {
                Text value = Text.literal(row.value).formatted(Formatting.BOLD);
                context.drawText(font, Text.literal("\u2022 ").append(Text.translatable(row.key)), left + 4, y, TEXT_COLOR, false);
                context.drawText(font, value, left + WIDTH - font.getWidth(value), y, row.color, false);
                y += LINE_HEIGHT;
                continue;
            }
            context.drawItem(stack(row.icon), left, y);
            Text value = Text.literal(row.value).formatted(Formatting.BOLD);
            int valueWidth = font.getWidth(value);
            context.drawText(font, value, left + WIDTH - valueWidth, y + 4, row.color, false);
            List<OrderedText> lines = font.wrapLines(Text.translatable(row.key), WIDTH - 20 - valueWidth - 3);
            if (lines.size() == 1) {
                context.drawText(font, lines.get(0), left + 19, y + 4, TEXT_COLOR, false);
            } else {
                for (int i = 0; i < Math.min(2, lines.size()); i++) {
                    context.drawText(font, lines.get(i), left + 19, y + i * 9, TEXT_COLOR, false);
                }
            }
            if (mouseX >= left && mouseX < left + WIDTH && mouseY >= y - 1 && mouseY < y + ROW_HEIGHT - 1) {
                hovered = row;
            }
            y += ROW_HEIGHT;
        }

        if (!grid.isEmpty()) {
            int perLine = WIDTH / 19;
            int gridLeft = left + (WIDTH - perLine * 19 + 3) / 2;
            for (int i = 0; i < grid.size(); i++) {
                context.drawItem(stack(grid.get(i)), gridLeft + (i % perLine) * 19, y + (i / perLine) * 18);
            }
            y += ((grid.size() + perLine - 1) / perLine) * 18 + 2;
        }

        if (noteKey != null) {
            for (OrderedText line : font.wrapLines(Text.translatable(noteKey), WIDTH)) {
                if (y > LAST_LINE_Y) {
                    break;
                }
                context.drawText(font, line, left, y, NOTE_COLOR, false);
                y += 9;
            }
        }

        if (hovered != null && Language.getInstance().hasTranslation(hovered.key + ".tip")) {
            context.drawOrderedTooltip(font, font.wrapLines(Text.translatable(hovered.key + ".tip"), 170), mouseX, mouseY);
        }
    }
}
