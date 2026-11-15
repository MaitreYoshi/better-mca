package com.maitreyoshi.mcavie.tree.client;

import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.life.LifeCycle;
import com.maitreyoshi.mcavie.tree.FamilyPerson;
import com.maitreyoshi.mcavie.tree.RelationInfo;
import com.maitreyoshi.mcavie.work.WorkRegistry;
import net.mca.entity.ai.relationship.Gender;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;

public final class TreeTexts {
    private static final String PREFIX = "tree.mca_vie.";

    private TreeTexts() {
    }

    public static boolean isFemale(int gender) {
        return Gender.byId(gender) == Gender.FEMALE;
    }

    public static String g(int gender) {
        return isFemale(gender) ? "f" : "m";
    }

    public static int nameColor(int gender) {
        return Gender.byId(gender).getColor();
    }

    public static String name(FamilyPerson person) {
        return person.name().isBlank() ? "?" : person.name();
    }

    public static String surname(FamilyPerson person) {
        String surname = person.surname();
        int slash = surname.indexOf('/');
        return (slash >= 0 ? surname.substring(0, slash) : surname).trim();
    }

    public static String relation(RelationInfo info, int gender) {
        String code = info.code();
        String gen = g(gender);
        String key;
        int greats = 0;
        switch (code) {
            case "great_grandparent", "great_grandchild" -> {
                key = PREFIX + "rel." + code + "." + gen;
                greats = Math.max(1, info.count());
            }
            case "grand_uncle", "grand_nephew" -> {
                key = PREFIX + "rel." + code + "." + gen;
                greats = Math.max(0, info.count());
            }
            case "cousin" -> key = PREFIX + "rel.cousin." + Math.min(3, Math.max(1, info.count())) + "." + gen;
            default -> key = PREFIX + "rel." + code + "." + gen;
        }
        if (!I18n.hasTranslation(key)) {
            key = PREFIX + "rel.distant_relative." + gen;
        }
        String label = I18n.translate(key);
        return I18n.translate(PREFIX + "great").repeat(greats) + label;
    }

    public static String yourRelation(RelationInfo info, int gender) {
        String label = relation(info, gender);
        return I18n.translate(PREFIX + "your." + articleForm(label, gender), label);
    }

    public static String theRelation(RelationInfo info, int gender) {
        String label = relation(info, gender);
        return I18n.translate(PREFIX + "the." + articleForm(label, gender), label);
    }

    public static boolean isShown(@Nullable RelationInfo info) {
        return info != null && !"none".equals(info.code()) && !"self".equals(info.code());
    }

    public static boolean isShownOnCard(@Nullable RelationInfo info) {
        return isShown(info) && !"relative_by_marriage".equals(info.code());
    }

    private static String articleForm(String label, int gender) {
        String elision = I18n.translate(PREFIX + "elision");
        if (!label.isEmpty() && elision.indexOf(Character.toLowerCase(label.charAt(0))) >= 0
                && !(PREFIX + "elision").equals(elision)) {
            return "vowel";
        }
        return g(gender);
    }

    public static Text age(int years) {
        return Text.translatable(years <= 1 ? "gui.mca_vie.age_one" : "gui.mca_vie.age", years);
    }

    // âge affiché : date de naissance exacte comparée à la date qui défile, pour qu'il change le jour de
    // l'anniversaire même en vitesse rapide (« 1 jour = 1 an » fait passer toute l'année dans un seul jour de jeu)
    public static int liveAge(@Nullable Long exactBirth, long birth, long today) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        net.minecraft.client.world.ClientWorld world = client == null ? null : client.world;
        if (world == null) {
            return LifeCycle.ageInYears(birth, today);
        }
        return ageBetween(exactBirth, birth, WorldCalendar.exactCalendarDay(world));
    }

    public static int ageBetween(@Nullable Long exactBirth, long birth, long calendarDay) {
        java.time.LocalDate born = exactBirth != null ? WorldCalendar.dateOfCalendarDay(exactBirth) : WorldCalendar.dateOfDay(birth);
        return Math.max(0, java.time.Period.between(born, WorldCalendar.dateOfCalendarDay(calendarDay)).getYears());
    }

    public static String year(long day) {
        return String.valueOf(WorldCalendar.displayYearOfDay(day));
    }

    @Nullable
    public static Text yearsLine(FamilyPerson person, long today) {
        Long birth = person.birthDay();
        Long death = person.deathDay();
        if (person.deceased() || death != null) {
            if (birth == null && death == null) {
                return null;
            }
            String unknown = I18n.translate(PREFIX + "unknown");
            return Text.translatable(PREFIX + "years", birth == null ? unknown : year(birth), death == null ? unknown : year(death));
        }
        if (birth == null) {
            return null;
        }
        return Text.translatable(PREFIX + "born_in." + g(person.gender()), year(birth), age(liveAge(person.exactBirth(), birth, today)));
    }

    public static Text date(long day) {
        return dateOf(WorldCalendar.dateOfDay(day));
    }

    public static Text date(@Nullable Long calendarDay, long day) {
        return calendarDay != null ? dateOf(WorldCalendar.dateOfCalendarDay(calendarDay)) : date(day);
    }

    public static Text calendarDate(long calendarDay) {
        return dateOf(WorldCalendar.dateOfCalendarDay(calendarDay));
    }

    private static Text dateOf(LocalDate date) {
        Text dayOfMonth = date.getDayOfMonth() == 1
                ? Text.translatable("calendar.mca_vie.first_day")
                : Text.literal(String.valueOf(date.getDayOfMonth()));
        return Text.translatable(PREFIX + "date",
                dayOfMonth,
                Text.translatable("calendar.mca_vie.month." + date.getMonthValue()),
                date.getYear() <= 0 ? date.getYear() - 1 : date.getYear());
    }

    public static String profession(FamilyPerson person) {
        String key = person.professionKey();
        if (key != null && key.startsWith(WorkRegistry.RETIRED_PREFIX)) {
            String former = key.substring(WorkRegistry.RETIRED_PREFIX.length());
            return I18n.hasTranslation(former) ? I18n.translate("job.mca_vie.retired_as", I18n.translate(former)) : I18n.translate("job.mca_vie.retired");
        }
        if (key == null || key.isBlank() || !I18n.hasTranslation(key)) {
            return "";
        }
        return I18n.translate(key);
    }

    public static String trim(TextRenderer font, String text, int maxWidth) {
        if (font.getWidth(text) <= maxWidth) {
            return text;
        }
        return font.trimToWidth(text, maxWidth - font.getWidth("…")) + "…";
    }
}
