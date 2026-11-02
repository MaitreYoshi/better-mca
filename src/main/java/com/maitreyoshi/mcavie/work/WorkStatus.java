package com.maitreyoshi.mcavie.work;

import net.minecraft.text.Text;

// ordinal envoyé au client : ajouter seulement à la fin
public enum WorkStatus {
    SEARCHING("searching"),
    TRAVELING("traveling"),
    WORKING("working"),
    NOTHING_TO_DO("nothing"),
    NO_GRIEFING("no_griefing"),
    DEPOSITING("depositing"),
    INVENTORY_FULL("full"),
    NO_WORKSTATION("no_workstation");

    private static final WorkStatus[] VALUES = values();

    private final String id;

    WorkStatus(String id) {
        this.id = id;
    }

    public Text getText() {
        return Text.translatable("job.mca_vie.status." + id);
    }

    public static WorkStatus byId(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : SEARCHING;
    }
}
