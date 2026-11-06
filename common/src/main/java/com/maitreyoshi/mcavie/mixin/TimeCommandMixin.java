package com.maitreyoshi.mcavie.mixin;

import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.life.History;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.server.command.TimeCommand;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TimeCommand.class)
public abstract class TimeCommandMixin {
    @Unique
    private static long mcavie$dayBefore;

    @ModifyVariable(method = "executeAdd", at = @At("HEAD"), argsOnly = true)
    private static int mcavie$limitJump(int time, ServerCommandSource source) {
        if (time <= 0) {
            return time;
        }
        // La durée demandée est du temps calendaire : /time add 100d avance de 100 jours du calendrier, quelle que soit l'échelle de temps.
        long calendarTicks = Math.min(time, History.MAX_DAYS * WorldCalendar.TICKS_PER_DAY);
        if (time > calendarTicks) {
            source.sendFeedback(() -> Text.translatable("msg.mca_vie.history.limited", History.MAX_DAYS / 365), false);
        }
        double speed = WorldCalendar.calendarDaysPerGameDay(WorldCalendar.now(source.getServer().getOverworld()).dayIndex());
        return (int) Math.max(1, Math.round(calendarTicks / speed));
    }

    @Inject(method = "executeAdd", at = @At("HEAD"))
    private static void mcavie$rememberDay(ServerCommandSource source, int time, CallbackInfoReturnable<Integer> cir) {
        mcavie$dayBefore = WorldCalendar.now(source.getServer().getOverworld()).dayIndex();
    }

    @Inject(method = "executeAdd", at = @At("RETURN"))
    private static void mcavie$simulate(ServerCommandSource source, int time, CallbackInfoReturnable<Integer> cir) {
        ServerWorld world = source.getServer().getOverworld();
        History.simulate(world, mcavie$dayBefore, WorldCalendar.now(world).dayIndex());
    }

    @Redirect(method = "executeSet", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;setTimeOfDay(J)V"))
    private static void mcavie$keepDay(ServerWorld world, long time) {
        long current = world.getTimeOfDay();
        long target = Math.floorDiv(current, WorldCalendar.TICKS_PER_DAY) * WorldCalendar.TICKS_PER_DAY + Math.floorMod(time, WorldCalendar.TICKS_PER_DAY);
        if (target < current) {
            target += WorldCalendar.TICKS_PER_DAY;
        }
        world.setTimeOfDay(target);
    }
}
