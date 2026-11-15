package com.maitreyoshi.mcavie.work.tasks;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.entity.ai.brain.task.Task;

public final class JobTasks {
    private JobTasks() {
    }

    public static ImmutableList<Pair<Integer, ? extends Task<? super VillagerEntityMCA>>> createPackage() {
        return ImmutableList.of(
                Pair.of(0, new DepositTask()),
                Pair.of(1, new FarmerTask()),
                Pair.of(1, new LumberjackTask()),
                Pair.of(1, new FisherTask()),
                Pair.of(1, new BreederTask()),
                Pair.of(1, new DoctorTask()),
                Pair.of(1, new BlacksmithTask()),
                Pair.of(1, new BakerTask()),
                Pair.of(1, new TeacherTask()),
                Pair.of(1, new MinerTask()),
                Pair.of(1, new CookTask())
        );
    }
}
