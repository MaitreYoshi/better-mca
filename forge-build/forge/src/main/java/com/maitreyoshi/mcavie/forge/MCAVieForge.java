package com.maitreyoshi.mcavie.forge;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(MCAVie.MOD_ID)
public final class MCAVieForge {
    @SuppressWarnings("removal")
    public MCAVieForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        EventBuses.registerModEventBus(MCAVie.MOD_ID, modBus);
        WorldCalendar.TIME_SPEED = new ForgeTimeSpeedRule();
        MCAVie.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MCAVieForgeClient.register(modBus);
        }
    }
}
