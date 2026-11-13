package com.maitreyoshi.mcavie.mixin.village;

import com.maitreyoshi.mcavie.village.VillageRights;
import net.conczin.mca.network.c2s.RenameVillageMessage;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenameVillageMessage.class, remap = false)
public abstract class RenameVillageMessageMixin {
    @Shadow
    @Final
    private int id;
    @Shadow
    @Final
    private String name;

    @Inject(method = "receive", at = @At("HEAD"), cancellable = true)
    private void mcavie$checkRights(ServerPlayer player, CallbackInfo ci) {
        ci.cancel();
        VillageManager.get(player.level()).getOrEmpty(id).ifPresent(village -> {
            if (!VillageRights.personalRank(village, player).isAtLeast(VillageRights.RENAME)) {
                player.sendOverlayMessage(Component.translatable("msg.mca_vie.rights.denied", Component.translatable(VillageRights.RENAME.getTranslationKey())));
                return;
            }
            String clean = name == null ? "" : name.strip();
            if (!clean.isEmpty()) {
                village.setName(clean.length() > 32 ? clean.substring(0, 32) : clean);
                village.markDirty();
            }
        });
    }
}
