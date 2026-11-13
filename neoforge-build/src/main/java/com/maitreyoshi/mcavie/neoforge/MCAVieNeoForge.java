package com.maitreyoshi.mcavie.neoforge;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(MCAVie.MOD_ID)
public final class MCAVieNeoForge {
    public MCAVieNeoForge(IEventBus modBus) {
        WorldCalendar.TIME_SPEED = new NeoForgeTimeSpeedRule(modBus);
        MCAVie.init();
        if (FMLEnvironment.getDist().isClient()) {
            MCAVieNeoForgeClient.init(modBus);
        }
    }
}