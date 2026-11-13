package com.maitreyoshi.mcavie.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.life.History;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.TimeCommand;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TimeCommand.class)
public abstract class TimeCommandMixin {
    @Unique
    private static long mcavie$dayBefore;

    @ModifyVariable(method = "addTime", at = @At("HEAD"), argsOnly = true)
    private static int mcavie$limitJump(int time, CommandSourceStack source) {
        if (time <= 0) {
            return time;
        }
        // La durée demandée est du temps calendaire : /time add 100d avance de 100 jours du calendrier, quelle que soit l'échelle de temps.
        long calendarTicks = Math.min(time, History.MAX_DAYS * WorldCalendar.TICKS_PER_DAY);
        if (time > calendarTicks) {
            source.sendSuccess(() -> Component.translatable("msg.mca_vie.history.limited", History.MAX_DAYS / 365), false);
        }
        double speed = WorldCalendar.calendarDaysPerGameDay(WorldCalendar.now(source.getServer().overworld()).dayIndex());
        return (int) Math.max(1, Math.round(calendarTicks / speed));
    }

    @Inject(method = "addTime", at = @At("HEAD"))
    private static void mcavie$rememberDay(CommandSourceStack source, int time, CallbackInfoReturnable<Integer> cir) {
        mcavie$dayBefore = WorldCalendar.now(source.getServer().overworld()).dayIndex();
    }

    @Inject(method = "addTime", at = @At("RETURN"))
    private static void mcavie$simulate(CommandSourceStack source, int time, CallbackInfoReturnable<Integer> cir) {
        ServerLevel world = source.getServer().overworld();
        History.simulate(world, mcavie$dayBefore, WorldCalendar.now(world).dayIndex());
    }

    @WrapOperation(method = "setTime", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setDayTime(J)V"))
    private static void mcavie$keepDay(ServerLevel world, long time, Operation<Void> original) {
        long current = world.getOverworldClockTime();
        long target = Math.floorDiv(current, WorldCalendar.TICKS_PER_DAY) * WorldCalendar.TICKS_PER_DAY + Math.floorMod(time, WorldCalendar.TICKS_PER_DAY);
        if (target < current) {
            target += WorldCalendar.TICKS_PER_DAY;
        }
        original.call(world, target);
    }
}
