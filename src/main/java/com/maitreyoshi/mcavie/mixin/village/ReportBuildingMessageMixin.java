package com.maitreyoshi.mcavie.mixin.village;

import com.maitreyoshi.mcavie.village.Prestige;
import com.maitreyoshi.mcavie.village.VillageRights;
import net.mca.network.c2s.ReportBuildingMessage;
import net.mca.resources.BuildingTypes;
import net.mca.resources.data.BuildingType;
import net.mca.server.world.data.Building;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(value = ReportBuildingMessage.class, remap = false)
public abstract class ReportBuildingMessageMixin {
    @Shadow
    @Final
    private ReportBuildingMessage.Action action;
    @Shadow
    @Final
    private String data;

    @Inject(method = "receive", at = @At("HEAD"), cancellable = true)
    private void mcavie$checkForceType(ServerPlayerEntity player, CallbackInfo ci) {
        VillageManager villages = VillageManager.get(player.getServerWorld());
        Optional<Village> village = villages.findNearestVillage(player);

        if (village.isPresent() && !VillageRights.hasRights(village.get(), player)) {
            player.sendMessage(Text.translatable("msg.mca_vie.rights.denied", Text.translatable(VillageRights.RIGHTS.getTranslationKey())), true);
            ci.cancel();
            return;
        }
        if (action != ReportBuildingMessage.Action.FORCE_TYPE || data == null || data.equals("blocked")) {
            return;
        }
        BlockPos pos = player.getBlockPos();
        Optional<Building> building = village.flatMap(v -> v.getBuildings().values().stream()
                .filter(b -> b.containsPos(pos))
                .filter(b -> !b.getBuildingType().grouped())
                .findAny());
        if (village.isEmpty() || building.isEmpty() || building.get().getType().equals(data)) {
            return;
        }

        if (!VillageRights.personalRank(village.get(), player).isAtLeast(VillageRights.FORCE_TYPE)) {
            player.sendMessage(Text.translatable("msg.mca_vie.rights.denied", Text.translatable(VillageRights.FORCE_TYPE.getTranslationKey())), true);
            ci.cancel();
            return;
        }

        BuildingType type = BuildingTypes.getInstance().getServerBuildingTypes().get(data);
        if (type == null || !Prestige.hasRequiredBlocks(type, building.get())) {
            player.sendMessage(Text.translatable("msg.mca_vie.force.missing"), true);
            ci.cancel();
        }
    }
}
