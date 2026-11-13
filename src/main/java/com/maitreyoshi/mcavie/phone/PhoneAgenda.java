package com.maitreyoshi.mcavie.phone;

import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.compat.ServerTickEvents;
import com.maitreyoshi.mcavie.phone.PhoneRegistry.AgendaEvent;
import com.maitreyoshi.mcavie.phone.PhoneRegistry.Phone;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.time.LocalDate;

// Rappels de l'agenda : dès que le jour du calendrier change, prévient le propriétaire (s'il est connecté)
// des événements tombant ce jour-là. Les jours passés hors ligne sont rattrapés (au plus un an).
public final class PhoneAgenda {
    private static final int CHECK_INTERVAL = 20;
    private static final long MAX_CATCH_UP = 366;

    private PhoneAgenda() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % CHECK_INTERVAL == 0 && server.overworld() != null) {
                check(server);
            }
        });
    }

    // jour de calendrier -> date (et inverse), même origine que WorldCalendar
    public static LocalDate date(long calendarDay) {
        return WorldCalendar.dateOfCalendarDay(calendarDay);
    }

    public static long calendarDay(LocalDate date) {
        return date.toEpochDay() - WorldCalendar.dateOfCalendarDay(0).toEpochDay();
    }

    public static boolean occursOn(long eventDay, boolean yearly, long day) {
        if (!yearly || day < eventDay) {
            return eventDay == day;
        }
        LocalDate event = date(eventDay);
        LocalDate target = date(day);
        if (event.getMonthValue() != target.getMonthValue()) {
            return false;
        }
        // un 29 février est fêté le 28 les années non bissextiles
        int wanted = Math.min(event.getDayOfMonth(), target.lengthOfMonth());
        return target.getDayOfMonth() == wanted;
    }

    private static void check(MinecraftServer server) {
        long today = WorldCalendar.exactCalendarDay(server.overworld());
        PhoneRegistry registry = PhoneRegistry.get(server);
        boolean dirty = false;
        for (Phone phone : registry.all().values()) {
            if (phone.owner == null || phone.agendaDay == today) {
                continue;
            }
            if (phone.agendaDay < 0 || today < phone.agendaDay || phone.agenda.isEmpty()) {
                phone.agendaDay = today;
                dirty = true;
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(phone.owner);
            if (player == null) {
                continue;
            }
            boolean rang = false;
            for (long day = Math.max(phone.agendaDay + 1, today - MAX_CATCH_UP); day <= today; day++) {
                for (AgendaEvent event : phone.agenda) {
                    if (occursOn(event.day(), event.yearly(), day)) {
                        notify(player, event, day, today);
                        rang = true;
                    }
                }
            }
            if (rang) {
                PhoneServer.ring(player, PhoneNetwork.NOTIFY_REMINDER);
            }
            phone.agendaDay = today;
            dirty = true;
        }
        if (dirty) {
            registry.setDirty();
        }
    }

    private static void notify(ServerPlayer player, AgendaEvent event, long day, long today) {
        LocalDate date = date(day);
        Component when = day == today
                ? Component.translatable("msg.mca_vie.phone.agenda_today")
                : Component.literal(date.getDayOfMonth() + "/" + date.getMonthValue());
        String key = event.yearly() ? "msg.mca_vie.phone.agenda_reminder_yearly" : "msg.mca_vie.phone.agenda_reminder";
        player.displayClientMessage(Component.translatable(key, when, Component.literal(event.title()).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GOLD), false);
        player.displayClientMessage(Component.translatable("msg.mca_vie.phone.agenda_overlay", event.title()), true);
    }
}
