package com.maitreyoshi.mcavie.village;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

// ordinal sauvegardé : ne jamais réordonner, seulement ajouter à la fin
public enum VillageRank {
    HAMLET,
    VILLAGE,
    TOWN,
    CITY,
    METROPOLIS;

    private static final VillageRank[] VALUES = values();

    public static VillageRank byIndex(int index) {
        return VALUES[Math.max(0, Math.min(VALUES.length - 1, index))];
    }

    public VillageRank next() {
        return ordinal() + 1 < VALUES.length ? VALUES[ordinal() + 1] : null;
    }

    public VillageRank previous() {
        return ordinal() > 0 ? VALUES[ordinal() - 1] : null;
    }

    public boolean isAtLeast(VillageRank other) {
        return ordinal() >= other.ordinal();
    }

    public VillageConfig.RankRule rule() {
        return VillageConfig.get().rank(this);
    }

    public MutableText getName() {
        return Text.translatable("village.mca_vie.rank." + name().toLowerCase(java.util.Locale.ROOT));
    }
}
