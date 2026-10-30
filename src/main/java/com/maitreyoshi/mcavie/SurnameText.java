package com.maitreyoshi.mcavie;

import net.mca.entity.ai.relationship.Gender;

public final class SurnameText {
    public static final int MAX_LENGTH = 32;

    private SurnameText() {
    }

    public static String display(String canonical, Gender gender) {
        String[] parts = canonical.split("/", 2);
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
        if (trimmed.isEmpty()) {
            return surname;
        }
        if (trimmed.equals(surname) || trimmed.endsWith(" " + surname)) {
            return trimmed;
        }
        return trimmed + " " + surname;
    }

    public static String firstName(String name, String surname) {
        String trimmed = name == null ? "" : name.trim();
        if (surname != null && !surname.isBlank() && trimmed.endsWith(" " + surname)) {
            return trimmed.substring(0, trimmed.length() - surname.length() - 1).trim();
        }
        return trimmed;
    }

    public static String sanitize(String input) {
        if (input == null) {
            return "";
        }
        String cleaned = input.replace("§", "").replace("/", "").replaceAll("\\s+", " ").trim();
        if (cleaned.length() > MAX_LENGTH) {
            cleaned = cleaned.substring(0, MAX_LENGTH).trim();
        }
        return cleaned;
    }
}
