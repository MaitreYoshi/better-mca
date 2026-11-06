package com.maitreyoshi.mcavie.village;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.Locale;

// ordinal sauvegardé : ne jamais réordonner, seulement ajouter à la fin
public enum Decree {
    FEAST(VillageRank.VILLAGE, Kind.MONTHLY),
    BIRTH_BONUS(VillageRank.VILLAGE, Kind.TOGGLE),
    RENOVATION(VillageRank.VILLAGE, Kind.ON_DEMAND),
    SCHOLARSHIP(VillageRank.TOWN, Kind.TOGGLE),
    SETTLERS_CALL(VillageRank.TOWN, Kind.MONTHLY),
    FREE_CARE(VillageRank.CITY, Kind.TOGGLE),
    RECRUIT_GUARD(VillageRank.CITY, Kind.ON_DEMAND),
    GRAND_FESTIVAL(VillageRank.METROPOLIS, Kind.SEASONAL);

    private static final Decree[] VALUES = values();

    public enum Kind {
        MONTHLY,
        SEASONAL,
        TOGGLE,
        ON_DEMAND
    }

    public final VillageRank rank;
    public final Kind kind;

    Decree(VillageRank rank, Kind kind) {
        this.rank = rank;
        this.kind = kind;
    }

    public static Decree byIndex(int index) {
        return index >= 0 && index < VALUES.length ? VALUES[index] : null;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public MutableText getName() {
        return Text.translatable("decree.mca_vie." + id());
    }

    public MutableText getDescription() {
        return Text.translatable("decree.mca_vie." + id() + ".description");
    }
}
