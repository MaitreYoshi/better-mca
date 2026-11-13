package com.maitreyoshi.mcavie.mixin;

import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.life.HistoryRegistry;
import com.maitreyoshi.mcavie.life.LifeCycle;
import com.maitreyoshi.mcavie.life.LifeRegistry;
import com.maitreyoshi.mcavie.life.TombstoneBlocks;
import com.maitreyoshi.mcavie.life.TombstoneDetails;
import com.maitreyoshi.mcavie.life.TombReservations;
import com.maitreyoshi.mcavie.life.TombstonePlacement;
import net.conczin.mca.server.world.data.GraveyardManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import com.maitreyoshi.mcavie.life.Wellbeing;
import com.maitreyoshi.mcavie.social.Social;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Relationship;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(value = Relationship.class, remap = false)
public abstract class RelationshipMixin {
    @Shadow
    @Final
    protected Mob entity;

    @Shadow
    public abstract FamilyTreeNode getFamilyEntry();

    @org.spongepowered.asm.mixin.Unique
    private boolean mcavie$memorial;

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void mcavie$mourning(DamageSource cause, CallbackInfo ci) {
        mcavie$memorial = false;
        if (!(entity.level() instanceof ServerLevel world)) {
            return;
        }
        LifeRegistry life = LifeRegistry.get(world.getServer());
        HistoryRegistry history = HistoryRegistry.get(world.getServer());
        Long simulated = history.pendingDeath(entity.getUUID());
        if (simulated != null) {
            history.clearPendingDeath(entity.getUUID());
            life.setDeathDay(entity.getUUID(), simulated);
            if (entity instanceof VillagerEntityMCA villager) {
                com.maitreyoshi.mcavie.life.Ghosts.died(villager, world);
            }
            Social.died(world, entity.getUUID(), true);
            return;
        }
        if (life.getBirthDay(entity.getUUID()) != null) {
            life.recordDeath(entity.getUUID(), WorldCalendar.now(world).dayIndex(), WorldCalendar.exactCalendarDay(world));
        }
        FamilyTreeNode node = getFamilyEntry();
        if (FamilyTreeNode.isValid(node.partner())) {
            Wellbeing.addHappiness(world, node.partner(), Wellbeing.LOST_SPOUSE);
        }
        node.getParents().forEach(parent -> Wellbeing.addHappiness(world, parent.id(), Wellbeing.LOST_CHILD));
        node.children().forEach(child -> Wellbeing.addHappiness(world, child, Wellbeing.LOST_PARENT));
        if (entity instanceof VillagerEntityMCA villager && !villager.isHostile()) {
            com.maitreyoshi.mcavie.village.CivilRegistry.death(villager, world);
            com.maitreyoshi.mcavie.life.Ghosts.died(villager, world);
            mcavie$memorial = com.maitreyoshi.mcavie.life.MemorialRegistry.died(villager, world, cause);
        }
        if (entity instanceof VillagerEntityMCA || life.isZombie(entity.getUUID())) {
            Social.died(world, entity.getUUID(), true);
        }
    }

    @Inject(method = "onDeath", at = @At("RETURN"))
    private void mcavie$forgetDead(DamageSource cause, CallbackInfo ci) {
        if (entity.level() instanceof ServerLevel world) {
            LifeCycle.forgetDead(world, entity.getUUID());
        }
    }

    @Redirect(method = "onDeath", at = @At(value = "INVOKE", target = "Lnet/mca/server/world/data/FamilyTreeNode;willBeRemembered()Z"))
    private boolean mcavie$alwaysRemembered(FamilyTreeNode node) {
        return entity instanceof VillagerEntityMCA || node.willBeRemembered();
    }

    @Redirect(method = "onDeath", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/mca/server/world/data/GraveyardManager;findNearest(Lnet/minecraft/core/BlockPos;Lnet/mca/server/world/data/GraveyardManager$TombstoneState;I)Ljava/util/Optional;"))
    private Optional<BlockPos> mcavie$reservedOrFreeTomb(GraveyardManager graveyard, BlockPos pos, GraveyardManager.TombstoneState state, int range) {
        if (mcavie$memorial) {
            return Optional.empty();
        }
        return entity.level() instanceof ServerLevel world ? TombReservations.findTomb(world, entity, pos, range) : graveyard.findNearest(pos, state, range);
    }

    @Inject(method = "placeTombstone", at = @At("HEAD"), cancellable = true)
    private void mcavie$placeOnNearestFreeBlock(ServerLevel world, BlockPos entityPos, CallbackInfoReturnable<Optional<BlockPos>> cir) {
        if (mcavie$memorial) {
            cir.setReturnValue(Optional.empty());
            return;
        }
        TombstonePlacement.findSpot(world, entityPos).or(() -> TombstonePlacement.surface(world, entityPos)).ifPresent(pos -> {
            world.setBlockAndUpdate(pos, TombstoneBlocks.forTier(TombstoneDetails.tierOf(entity, world)).defaultBlockState());
            cir.setReturnValue(Optional.of(pos));
        });
    }
}
