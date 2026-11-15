package com.maitreyoshi.mcavie.mixin.village;

import net.mca.resources.Rank;
import net.mca.resources.Tasks;
import net.mca.resources.data.tasks.AdvancementTask;
import net.mca.resources.data.tasks.BuildingTask;
import net.mca.resources.data.tasks.PopulationTask;
import net.mca.server.world.data.Village;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Tasks.class, remap = false)
public abstract class TasksMixin {
    @Unique
    private static Object mcavie$filtered;

    @Inject(method = "getRank", at = @At("HEAD"), cancellable = true)
    private static void mcavie$forcedTitle(Village village, ServerPlayerEntity player, CallbackInfoReturnable<Rank> cir) {
        Rank forced = com.maitreyoshi.mcavie.village.VillageState.of(village).forcedTitles.get(player.getUuid());
        if (forced != null) {
            cir.setReturnValue(forced);
        }
    }

    @Inject(method = "getRank", at = @At("HEAD"))
    private static void mcavie$reputationOnly(Village village, ServerPlayerEntity player, CallbackInfoReturnable<Rank> cir) {
        Tasks tasks = Tasks.getInstance();
        if (tasks == null) {
            return;
        }
        Object marker = tasks.tasks.get(Rank.values()[0]);
        if (marker != null && marker == mcavie$filtered) {
            return;
        }
        tasks.tasks.values().forEach(list -> list.removeIf(t -> t instanceof BuildingTask || t instanceof PopulationTask));
        mcavie$filtered = marker;
    }

    /** MCA ne marque pas les tâches de succès comme requises : un titre (monarque) exige aussi ses succès (Faucheuse tuée). */
    @Inject(method = "getRank", at = @At("RETURN"), cancellable = true)
    private static void mcavie$requireAdvancements(Village village, ServerPlayerEntity player, CallbackInfoReturnable<Rank> cir) {
        Tasks tasks = Tasks.getInstance();
        if (tasks == null) {
            return;
        }
        Rank rank = cir.getReturnValue();
        while (rank.ordinal() > 0 && !mcavie$advancementsDone(tasks, rank, village, player)) {
            rank = Rank.values()[rank.ordinal() - 1];
        }
        if (rank != cir.getReturnValue()) {
            cir.setReturnValue(rank);
        }
    }

    @Unique
    private static boolean mcavie$advancementsDone(Tasks tasks, Rank rank, Village village, ServerPlayerEntity player) {
        for (var task : tasks.tasks.getOrDefault(rank, java.util.List.of())) {
            if (task instanceof AdvancementTask) {
                try {
                    if (!task.isCompleted(village, player)) {
                        return false;
                    }
                } catch (RuntimeException e) {
                    return false;
                }
            }
        }
        return true;
    }
}
