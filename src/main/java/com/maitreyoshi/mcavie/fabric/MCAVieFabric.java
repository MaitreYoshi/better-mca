package com.maitreyoshi.mcavie.fabric;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import net.fabricmc.api.ModInitializer;

public final class MCAVieFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        WorldCalendar.TIME_SPEED = new FabricTimeSpeedRule();
        MCAVie.init();
    }
}
