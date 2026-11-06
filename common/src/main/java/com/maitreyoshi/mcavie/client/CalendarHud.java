package com.maitreyoshi.mcavie.client;

import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.calendar.WorldCalendar.CalendarDate;
import com.maitreyoshi.mcavie.compat.client.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

public final class CalendarHud {
    private static final int TOP = 4;
    private static final int PADDING = 3;

    private static CalendarDate cachedDate;
    private static Text cachedText;

    private CalendarHud() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register(CalendarHud::render);
    }

    private static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.options.hudHidden) {
            return;
        }
        if (!player.getMainHandStack().isOf(Items.CLOCK) && !player.getOffHandStack().isOf(Items.CLOCK)) {
            return;
        }

        CalendarDate date = WorldCalendar.flowingNow(client.world);
        if (cachedText == null || !date.equals(cachedDate)) {
            cachedDate = date;
            cachedText = format(date);
        }
        Text text = cachedText;

        TextRenderer font = client.textRenderer;
        int width = font.getWidth(text);
        int x = (context.getScaledWindowWidth() - width) / 2;
        context.fill(x - PADDING, TOP - PADDING, x + width + PADDING, TOP + font.fontHeight + PADDING - 1, 0x80000000);
        context.drawTextWithShadow(font, text, x, TOP, 0xFFFFFF);
    }

    private static Text format(CalendarDate date) {
        Text dayOfMonth = date.day() == 1
                ? Text.translatable("calendar.mca_vie.first_day")
                : Text.literal(String.valueOf(date.day()));
        return Text.translatable("hud.mca_vie.calendar",
                Text.translatable("calendar.mca_vie.day." + date.dayOfWeek().getValue()),
                dayOfMonth,
                Text.translatable("calendar.mca_vie.month." + date.month()),
                date.year(),
                String.format("%02d:%02d", date.hour(), date.minute()));
    }
}
