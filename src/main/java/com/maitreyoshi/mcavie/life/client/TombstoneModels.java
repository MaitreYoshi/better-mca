package com.maitreyoshi.mcavie.life.client;

import com.maitreyoshi.mcavie.life.TombstoneDetails;

public final class TombstoneModels {
    private static final TextArea CROSS = new TextArea(0.68f, -0.077f, 0.52f * 96, 0.54f * 96, Look.WOOD);
    private static final TextArea STELE = new TextArea(0.9f, -0.062f, 0.62f * 96, 0.72f * 96, Look.ENGRAVED);
    private static final TextArea MONUMENT = new TextArea(0.98f, 0.298f, 0.7f * 96, 0.78f * 96, Look.GOLD);

    public enum Look {
        MCA, WOOD, ENGRAVED, GOLD
    }

    public record TextArea(float top, float z, float width, float height, Look look) {
    }

    private TombstoneModels() {
    }

    public static TextArea area(int tier) {
        return tier == TombstoneDetails.POOR ? CROSS : tier == TombstoneDetails.RICH ? MONUMENT : STELE;
    }
}
