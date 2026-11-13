package com.maitreyoshi.mcavie.mixin;

import com.maitreyoshi.mcavie.work.JobProfessions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Échanges de nos métiers : ajoutés en code à la place des échanges en données de Minecraft 26.x. */
@Mixin(Villager.class)
public abstract class VillagerTradesMixin {
    @Inject(method = "updateTrades", at = @At("HEAD"), cancellable = true)
    private void mcavie$jobTrades(ServerLevel level, CallbackInfo ci) {
        Villager self = (Villager) (Object) this;
        if (JobProfessions.addTrades(self, self.getRandom())) {
            ci.cancel();
        }
    }

    @Inject(method = "updateTrades", at = @At("TAIL"))
    private void mcavie$bookTrades(ServerLevel level, CallbackInfo ci) {
        Villager self = (Villager) (Object) this;
        com.maitreyoshi.mcavie.book.GuideBooks.addTrades(self, self.getRandom());
    }
}
