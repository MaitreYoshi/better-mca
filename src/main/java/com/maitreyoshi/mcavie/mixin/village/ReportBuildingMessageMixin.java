package com.maitreyoshi.mcavie.mixin.village;

import com.maitreyoshi.mcavie.village.Prestige;
import com.maitreyoshi.mcavie.village.VillageRights;
import net.conczin.mca.network.c2s.ReportBuildingMessage;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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

    @Inject(method = "handleServer", at = @At("HEAD"), cancellable = true)
    private void mcavie$checkForceType(ServerPlayer player, CallbackInfo ci) {
        VillageManager villages = VillageManager.get(player.level());
        Optional<Village> village = villages.findNearestVillage(player);

        if (action != ReportBuildingMessage.Action.FORCE_TYPE || data == null || data.equals("blocked")) {
            return;
        }
        BlockPos pos = player.blockPosition();
        Optional<Building> building = village.flatMap(v -> v.getBuildings().values().stream()
                .filter(b -> b.containsPos(pos))
                .filter(b -> !b.getBuildingType().grouped())
                .findAny());
        if (village.isEmpty() || building.isEmpty() || building.get().getType().equals(data)) {
            return;
        }

        if (!VillageRights.personalRank(village.get(), player).isAtLeast(VillageRights.FORCE_TYPE)) {
            player.sendOverlayMessage(Component.translatable("msg.mca_vie.rights.denied", Component.translatable(VillageRights.FORCE_TYPE.getTranslationKey())));
            ci.cancel();
            return;
        }

        BuildingType type = BuildingTypes.getInstance().getServerBuildingTypes().get(data);
        if (type == null || !Prestige.hasRequiredBlocks(type, building.get())) {
            player.sendOverlayMessage(Component.translatable("msg.mca_vie.force.missing"));
            ci.cancel();
        }
    }
}
