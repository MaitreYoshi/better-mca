package com.maitreyoshi.mcavie.mixin;

import com.maitreyoshi.mcavie.Surnames;
import net.mca.entity.VillagerEntityMCA;
import net.mca.network.c2s.VillagerEditorSyncRequest;
import net.minecraft.server.network.ServerPlayerEntity;
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

    // Le nom de famille saisi dans l'éditeur remplace celui du registre, sinon il serait ré-ajouté au tick suivant.
    @Inject(method = "receive", at = @At("TAIL"))
    private void mcavie$storeEditedSurname(ServerPlayerEntity player, CallbackInfo ci) {
        if ("sync".equals(command) && player.getServerWorld().getEntity(uuid) instanceof VillagerEntityMCA villager) {
            Surnames.onEditorRename(villager);
        }
    }
}
