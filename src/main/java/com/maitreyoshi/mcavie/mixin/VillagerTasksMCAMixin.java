package com.maitreyoshi.mcavie.mixin;

import com.maitreyoshi.mcavie.work.tasks.JobTasks;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.ActivityMCA;
import net.mca.entity.ai.brain.VillagerTasksMCA;
import net.minecraft.entity.ai.brain.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = VillagerTasksMCA.class, remap = false)
public abstract class VillagerTasksMCAMixin {
    @Inject(method = "initializeTasks", at = @At("RETURN"))
    private static void mcavie$addJobTasks(VillagerEntityMCA villager, Brain<VillagerEntityMCA> brain, CallbackInfoReturnable<Brain<VillagerEntityMCA>> cir) {
        brain.setTaskList(ActivityMCA.CHORE.get(), com.google.common.collect.ImmutableList.<com.mojang.datafixers.util.Pair<Integer, ? extends net.minecraft.entity.ai.brain.task.Task<? super VillagerEntityMCA>>>builder()
                .addAll(VillagerTasksMCA.getChorePackage())
                .addAll(JobTasks.createPackage())
                .build());
    }
}
