package com.maitreyoshi.mcavie.phone;

import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.compat.ServerTickEvents;
import com.maitreyoshi.mcavie.phone.PhoneRegistry.AgendaEvent;
import com.maitreyoshi.mcavie.phone.PhoneRegistry.Phone;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
            if (server.getTicks() % CHECK_INTERVAL == 0 && server.getOverworld() != null) {
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
        long today = WorldCalendar.exactCalendarDay(server.getOverworld());
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
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(phone.owner);
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
            registry.markDirty();
        }
    }

    private static void notify(ServerPlayerEntity player, AgendaEvent event, long day, long today) {
        LocalDate date = date(day);
        Text when = day == today
                ? Text.translatable("msg.mca_vie.phone.agenda_today")
                : Text.literal(date.getDayOfMonth() + "/" + date.getMonthValue());
        String key = event.yearly() ? "msg.mca_vie.phone.agenda_reminder_yearly" : "msg.mca_vie.phone.agenda_reminder";
        player.sendMessage(Text.translatable(key, when, Text.literal(event.title()).formatted(Formatting.WHITE)).formatted(Formatting.GOLD), false);
        player.sendMessage(Text.translatable("msg.mca_vie.phone.agenda_overlay", event.title()), true);
    }
}
