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
    private String bigIcon;
    private Identifier image;
    private int imageWidth;
    private int imageHeight;
    private String[] recipe;
    private String recipeResult;
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

    public IllustratedPage image(Identifier image, int width, int height) {
        this.image = image;
        this.imageWidth = width;
        this.imageHeight = height;
        return this;
    }

    public IllustratedPage big(String icon) {
        this.bigIcon = icon;
        return this;
    }

    public IllustratedPage recipe(String result, String... nine) {
        this.recipeResult = result;
        this.recipe = nine;
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

        if (image != null) {
            int h = 124;
            int w = imageWidth * h / imageHeight;
            context.drawTexture(image, left + (WIDTH - w) / 2, 28, w, h, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
            return;
        }
        Text title = Text.translatable(titleKey).formatted(Formatting.BOLD);
        int titleWidth = font.getWidth(title);
        if (titleWidth > WIDTH) {
            float scale = WIDTH / (float) titleWidth;
            context.getMatrices().push();
            context.getMatrices().translate(left, 30 + 4 * (1 - scale), 0);
            context.getMatrices().scale(scale, scale, 1);
            context.drawText(font, title, 0, 0, TITLE_COLOR, false);
            context.getMatrices().pop();
        } else {
            context.drawText(font, title, left + (WIDTH - titleWidth) / 2, 30, TITLE_COLOR, false);
        }
        context.fill(left + 10, 41, left + WIDTH - 10, 42, 0x55000000 | TITLE_COLOR);

        int y = 46;
        if (introKey != null) {
            for (OrderedText line : font.wrapLines(Text.translatable(introKey), WIDTH)) {
                context.drawText(font, line, left, y, TEXT_COLOR, false);
                y += 9;
            }
            y += 4;
        }
        if (bigIcon != null) {
            context.getMatrices().push();
            context.getMatrices().translate(left + (WIDTH - 48) / 2f, y, 0);
            context.getMatrices().scale(3, 3, 1);
            context.drawItem(stack(bigIcon), 0, 0);
            context.getMatrices().pop();
            y += 52;
        }
        if (recipe != null) {
            int gridLeft = left + (WIDTH - 54 - 22 - 18) / 2;
            for (int i = 0; i < 9; i++) {
                int sx = gridLeft + (i % 3) * 18;
                int sy = y + (i / 3) * 18;
                context.fill(sx, sy, sx + 18, sy + 18, 0x40000000 | TITLE_COLOR);
                context.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFFE9DDBF);
                if (i < recipe.length && !recipe[i].isEmpty()) {
                    context.drawItem(stack(recipe[i]), sx + 1, sy + 1);
                }
            }
            int arrowX = gridLeft + 54 + 4;
            context.drawText(font, Text.literal("→").formatted(Formatting.BOLD), arrowX + 3, y + 23, TITLE_COLOR, false);
            int rx = arrowX + 18;
            context.fill(rx, y + 18, rx + 18, y + 36, 0x40000000 | TITLE_COLOR);
            context.fill(rx + 1, y + 19, rx + 17, y + 35, 0xFFE9DDBF);
            context.drawItem(stack(recipeResult), rx + 1, y + 19);
            y += 58;
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
            int rowHeight = Math.max(ROW_HEIGHT, lines.size() * 9 + 2);
            if (lines.size() == 1) {
                context.drawText(font, lines.get(0), left + 19, y + 4, TEXT_COLOR, false);
            } else {
                for (int i = 0; i < lines.size(); i++) {
                    context.drawText(font, lines.get(i), left + 19, y + i * 9, TEXT_COLOR, false);
                }
            }
            if (mouseX >= left && mouseX < left + WIDTH && mouseY >= y - 1 && mouseY < y + rowHeight - 1) {
                hovered = row;
            }
            y += rowHeight;
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
