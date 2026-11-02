package com.maitreyoshi.mcavie.work;

// geste de travail joué par le modèle du PNJ ; ordinal envoyé au client : ajouter seulement à la fin
public enum WorkGesture {
    NONE,
    CHOP,
    HOE,
    FISH,
    TEND,
    HEAL,
    HAMMER,
    KNEAD,
    TEACH;

    private static final WorkGesture[] VALUES = values();

    public static WorkGesture byId(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : NONE;
    }
}
