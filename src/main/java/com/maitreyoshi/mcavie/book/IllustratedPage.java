package com.maitreyoshi.mcavie.book;

import net.conczin.mca.client.book.pages.Page;
import net.conczin.mca.client.gui.ExtendedBookScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionUtils;
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
            return new ItemStack(BuiltInRegistries.ITEM.get(Identifier.parse(id)));
        }
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(Identifier.parse(id.substring(0, at))));
        return PotionUtils.setPotion(stack, BuiltInRegistries.POTION.get(Identifier.parse(id.substring(at + 1))));
    }

    @Override
    public void render(ExtendedBookScreen screen, GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        Font font = screen.getTextRenderer();
        int left = (screen.width - 192) / 2 + 36;

        if (image != null) {
            int h = 124;
            int w = imageWidth * h / imageHeight;
            context.blit(image, left + (WIDTH - w) / 2, 28, w, h, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
            return;
        }
        Component title = Component.translatable(titleKey).withStyle(ChatFormatting.BOLD);
        int titleWidth = font.width(title);
        if (titleWidth > WIDTH) {
            float scale = WIDTH / (float) titleWidth;
            context.pose().pushPose();
            context.pose().translate(left, 30 + 4 * (1 - scale), 0);
            context.pose().scale(scale, scale, 1);
            context.drawString(font, title, 0, 0, TITLE_COLOR, false);
            context.pose().popPose();
        } else {
            context.drawString(font, title, left + (WIDTH - titleWidth) / 2, 30, TITLE_COLOR, false);
        }
        context.fill(left + 10, 41, left + WIDTH - 10, 42, 0x55000000 | TITLE_COLOR);

        int y = 46;
        if (introKey != null) {
            for (FormattedCharSequence line : font.split(Component.translatable(introKey), WIDTH)) {
                context.drawString(font, line, left, y, TEXT_COLOR, false);
                y += 9;
            }
            y += 4;
        }
        if (bigIcon != null) {
            context.pose().pushPose();
            context.pose().translate(left + (WIDTH - 48) / 2f, y, 0);
            context.pose().scale(3, 3, 1);
            context.renderItem(stack(bigIcon), 0, 0);
            context.pose().popPose();
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
                    context.renderItem(stack(recipe[i]), sx + 1, sy + 1);
                }
            }
            int arrowX = gridLeft + 54 + 4;
            context.drawString(font, Component.literal("→").withStyle(ChatFormatting.BOLD), arrowX + 3, y + 23, TITLE_COLOR, false);
            int rx = arrowX + 18;
            context.fill(rx, y + 18, rx + 18, y + 36, 0x40000000 | TITLE_COLOR);
            context.fill(rx + 1, y + 19, rx + 17, y + 35, 0xFFE9DDBF);
            context.renderItem(stack(recipeResult), rx + 1, y + 19);
            y += 58;
        }
        Row hovered = null;
        for (Row row : rows) {
            if (row.icon == null) {
                Component value = Component.literal(row.value).withStyle(ChatFormatting.BOLD);
                context.drawString(font, Component.literal("\u2022 ").append(Component.translatable(row.key)), left + 4, y, TEXT_COLOR, false);
                context.drawString(font, value, left + WIDTH - font.width(value), y, row.color, false);
                y += LINE_HEIGHT;
                continue;
            }
            context.renderItem(stack(row.icon), left, y);
            Component value = Component.literal(row.value).withStyle(ChatFormatting.BOLD);
            int valueWidth = font.width(value);
            context.drawString(font, value, left + WIDTH - valueWidth, y + 4, row.color, false);
            List<FormattedCharSequence> lines = font.split(Component.translatable(row.key), WIDTH - 20 - valueWidth - 3);
            int rowHeight = Math.max(ROW_HEIGHT, lines.size() * 9 + 2);
            if (lines.size() == 1) {
                context.drawString(font, lines.get(0), left + 19, y + 4, TEXT_COLOR, false);
            } else {
                for (int i = 0; i < lines.size(); i++) {
                    context.drawString(font, lines.get(i), left + 19, y + i * 9, TEXT_COLOR, false);
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
                context.renderItem(stack(grid.get(i)), gridLeft + (i % perLine) * 19, y + (i / perLine) * 18);
            }
            y += ((grid.size() + perLine - 1) / perLine) * 18 + 2;
        }

        if (noteKey != null) {
            for (FormattedCharSequence line : font.split(Component.translatable(noteKey), WIDTH)) {
                if (y > LAST_LINE_Y) {
                    break;
                }
                context.drawString(font, line, left, y, NOTE_COLOR, false);
                y += 9;
            }
        }

        if (hovered != null && Language.getInstance().has(hovered.key + ".tip")) {
            context.renderTooltip(font, font.split(Component.translatable(hovered.key + ".tip"), 170), mouseX, mouseY);
        }
    }
}
