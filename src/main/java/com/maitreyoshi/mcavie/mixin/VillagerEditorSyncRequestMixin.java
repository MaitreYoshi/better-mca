package com.maitreyoshi.mcavie.mixin;

import com.maitreyoshi.mcavie.Surnames;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.network.c2s.VillagerEditorSyncRequest;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(value = VillagerEditorSyncRequest.class, remap = false)
public abstract class VillagerEditorSyncRequestMixin {
    @Shadow
    @Final
    private String command;
    @Shadow
    @Final
    private UUID uuid;

    @Inject(method = "receive", at = @At("TAIL"))
    private void mcavie$storeEditedSurname(ServerPlayer player, CallbackInfo ci) {
        if ("sync".equals(command) && player.serverLevel().getEntity(uuid) instanceof VillagerEntityMCA villager) {
            Surnames.onEditorRename(villager);
        }
    }
}
