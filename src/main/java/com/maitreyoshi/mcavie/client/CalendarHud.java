package com.maitreyoshi.mcavie.client;

import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.calendar.WorldCalendar.CalendarDate;
import com.maitreyoshi.mcavie.compat.client.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

public final class CalendarHud {
    private static final int TOP = 4;
    private static final int PADDING = 3;

    private static CalendarDate cachedDate;
    private static Component cachedText;

    private CalendarHud() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register(CalendarHud::render);
    }

    private static void render(GuiGraphics context, float tickDelta) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.level == null || client.options.hideGui) {
            return;
        }
        if (!player.getMainHandItem().is(Items.CLOCK) && !player.getOffhandItem().is(Items.CLOCK)) {
            return;
        }

        CalendarDate date = WorldCalendar.flowingNow(client.level);
        if (cachedText == null || !date.equals(cachedDate)) {
            cachedDate = date;
            cachedText = format(date);
        }
        Component text = cachedText;

        Font font = client.font;
        int width = font.width(text);
        int x = (context.guiWidth() - width) / 2;
        context.fill(x - PADDING, TOP - PADDING, x + width + PADDING, TOP + font.lineHeight + PADDING - 1, 0x80000000);
        context.drawString(font, text, x, TOP, 0xFFFFFF);
    }

    private static Component format(CalendarDate date) {
        Component dayOfMonth = date.day() == 1
                ? Component.translatable("calendar.mca_vie.first_day")
                : Component.literal(String.valueOf(date.day()));
        return Component.translatable("hud.mca_vie.calendar",
                Component.translatable("calendar.mca_vie.day." + date.dayOfWeek().getValue()),
                dayOfMonth,
                Component.translatable("calendar.mca_vie.month." + date.month()),
                date.year(),
                String.format("%02d:%02d", date.hour(), date.minute()));
    }
}
