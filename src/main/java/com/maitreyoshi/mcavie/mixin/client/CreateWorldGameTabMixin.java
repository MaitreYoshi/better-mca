package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.calendar.TimeSpeed;
import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.client.gui.tab.GridScreenTab;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

@Mixin(targets = "net.minecraft.client.gui.screen.world.CreateWorldScreen$GameTab")
public abstract class CreateWorldGameTabMixin extends GridScreenTab {
    private CreateWorldGameTabMixin(Text title) {
        super(title);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mcavie$addTimeSpeed(CreateWorldScreen screen, CallbackInfo ci) {
        WorldCreator creator = screen.getWorldCreator();
        CyclingButtonWidget<TimeSpeed> button = CyclingButtonWidget.<TimeSpeed>builder(speed -> Text.translatable("gamerule.mcaVieTimeSpeed." + speed.name().toLowerCase(Locale.ROOT)))
                .values(TimeSpeed.values())
                .initially(WorldCalendar.TIME_SPEED.get(creator.getGameRules()))
                .tooltip(speed -> Tooltip.of(Text.translatable("gamerule.mcaVieTimeSpeed.description")))
                .build(0, 0, 210, 20, Text.translatable("gamerule.mcaVieTimeSpeed"),
                        (widget, speed) -> WorldCalendar.TIME_SPEED.set(creator.getGameRules(), speed, null));
        grid.add(button, 5, 0, grid.copyPositioner().alignHorizontalCenter());
        creator.addListener(c -> button.setValue(WorldCalendar.TIME_SPEED.get(c.getGameRules())));
    }
}
