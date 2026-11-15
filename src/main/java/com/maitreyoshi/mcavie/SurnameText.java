package com.maitreyoshi.mcavie;

import net.mca.entity.ai.relationship.Gender;

import java.util.Locale;

public final class SurnameText {
    public static final int MAX_LENGTH = 32;

    private SurnameText() {
    }

    /** Les noms de famille sont toujours en majuscules. */
    public static String upper(String surname) {
        return surname == null ? null : surname.toUpperCase(Locale.ROOT);
    }

    public static String display(String canonical, Gender gender) {
        String[] parts = upper(canonical).split("/", 2);
        if (gender == Gender.FEMALE && parts.length > 1 && !parts[1].isBlank()) {
            return parts[1].trim();
        }
        return parts[0].trim();
    }

    public static String withSurname(String name, String surname) {
        String trimmed = name == null ? "" : name.trim();
        if (surname == null || surname.isBlank()) {
            return trimmed;
        }
        surname = upper(surname.trim());
        if (trimmed.isEmpty()) {
            return surname;
        }
        if (trimmed.equals(surname)) {
            return trimmed;
        }
        if (endsWithIgnoreCase(trimmed, " " + surname)) {
            return trimmed.substring(0, trimmed.length() - surname.length()) + surname;
        }
        return trimmed + " " + surname;
    }

    public static String firstName(String name, String surname) {
        String trimmed = name == null ? "" : name.trim();
        if (surname != null && !surname.isBlank()) {
            String s = surname.trim();
            if (endsWithIgnoreCase(trimmed, " " + s)) {
                return trimmed.substring(0, trimmed.length() - s.length() - 1).trim();
            }
        }
        return trimmed;
    }

    private static boolean endsWithIgnoreCase(String text, String suffix) {
        return text.length() >= suffix.length()
                && text.regionMatches(true, text.length() - suffix.length(), suffix, 0, suffix.length());
    }

    public static String sanitize(String input) {
        if (input == null) {
            return "";
        }
        String cleaned = input.replace("§", "").replace("/", "").replaceAll("\\s+", " ").trim();
        if (cleaned.length() > MAX_LENGTH) {
            cleaned = cleaned.substring(0, MAX_LENGTH).trim();
        }
        return upper(cleaned);
    }
}
