package com.maitreyoshi.mcavie.mixin.village;

import com.maitreyoshi.mcavie.village.VillageRights;
import net.mca.network.c2s.RenameVillageMessage;
import net.mca.server.world.data.VillageManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
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
    private void mcavie$checkRights(ServerPlayerEntity player, CallbackInfo ci) {
        ci.cancel();
        VillageManager.get(player.getServerWorld()).getOrEmpty(id).ifPresent(village -> {
            if (!VillageRights.personalRank(village, player).isAtLeast(VillageRights.RENAME)) {
                player.sendMessage(Text.translatable("msg.mca_vie.rights.denied", Text.translatable(VillageRights.RENAME.getTranslationKey())), true);
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
