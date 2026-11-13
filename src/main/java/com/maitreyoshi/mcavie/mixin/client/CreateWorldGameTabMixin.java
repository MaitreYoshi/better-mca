package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.calendar.TimeSpeed;
import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;

@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$GameTab")
public abstract class CreateWorldGameTabMixin extends GridLayoutTab {
    private CreateWorldGameTabMixin(Component title) {
        super(title);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mcavie$addTimeSpeed(CreateWorldScreen screen, CallbackInfo ci) {
        WorldCreationUiState creator = screen.getUiState();
        CycleButton<TimeSpeed> button = CycleButton.<TimeSpeed>builder(speed -> Component.translatable("gamerule.mcaVieTimeSpeed." + speed.name().toLowerCase(Locale.ROOT)),
                        WorldCalendar.TIME_SPEED.get(creator.getGameRules()))
                .withValues(TimeSpeed.values())
                .withTooltip(speed -> Tooltip.create(Component.translatable("gamerule.mcaVieTimeSpeed.description")))
                .create(0, 0, 210, 20, Component.translatable("gamerule.mcaVieTimeSpeed"),
                        (widget, speed) -> WorldCalendar.TIME_SPEED.set(creator.getGameRules(), speed, null));
        layout.addChild(button, 5, 0, layout.newCellSettings().alignHorizontallyCenter());
        creator.addListener(c -> button.setValue(WorldCalendar.TIME_SPEED.get(c.getGameRules())));
    }
}
