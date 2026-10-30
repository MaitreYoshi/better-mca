package com.maitreyoshi.mcavie.mixin;

import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.life.HistoryRegistry;
import com.maitreyoshi.mcavie.life.LifeCycle;
import com.maitreyoshi.mcavie.life.LifeRegistry;
import com.maitreyoshi.mcavie.life.TombstoneBlocks;
import com.maitreyoshi.mcavie.life.TombstoneDetails;
import com.maitreyoshi.mcavie.life.TombstonePlacement;
import com.maitreyoshi.mcavie.life.Wellbeing;
import com.maitreyoshi.mcavie.social.Social;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Relationship;
import net.mca.server.world.data.FamilyTreeNode;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
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
    protected MobEntity entity;

    @Shadow
    public abstract FamilyTreeNode getFamilyEntry();

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void mcavie$mourning(DamageSource cause, CallbackInfo ci) {
        if (!(entity.getWorld() instanceof ServerWorld world)) {
            return;
        }
        LifeRegistry life = LifeRegistry.get(world.getServer());
        HistoryRegistry history = HistoryRegistry.get(world.getServer());
        Long simulated = history.pendingDeath(entity.getUuid());
        if (simulated != null) {
            history.clearPendingDeath(entity.getUuid());
            life.setDeathDay(entity.getUuid(), simulated);
            Social.died(world, entity.getUuid(), true);
            return;
        }
        if (life.getBirthDay(entity.getUuid()) != null) {
            life.setDeathDay(entity.getUuid(), WorldCalendar.now(world).dayIndex());
        }
        FamilyTreeNode node = getFamilyEntry();
        if (FamilyTreeNode.isValid(node.partner())) {
            Wellbeing.addHappiness(world, node.partner(), Wellbeing.LOST_SPOUSE);
        }
        node.getParents().forEach(parent -> Wellbeing.addHappiness(world, parent.id(), Wellbeing.LOST_CHILD));
        node.children().forEach(child -> Wellbeing.addHappiness(world, child, Wellbeing.LOST_PARENT));
        if (entity instanceof VillagerEntityMCA villager && !villager.isHostile()) {
            com.maitreyoshi.mcavie.village.CivilRegistry.death(villager, world);
        }
        if (entity instanceof VillagerEntityMCA || life.isZombie(entity.getUuid())) {
            Social.died(world, entity.getUuid(), true);
        }
    }

    @Inject(method = "onDeath", at = @At("RETURN"))
    private void mcavie$forgetDead(DamageSource cause, CallbackInfo ci) {
        if (entity.getWorld() instanceof ServerWorld world) {
            LifeCycle.forgetDead(world, entity.getUuid());
        }
    }

    @Redirect(method = "onDeath", at = @At(value = "INVOKE", target = "Lnet/mca/server/world/data/FamilyTreeNode;willBeRemembered()Z"))
    private boolean mcavie$alwaysRemembered(FamilyTreeNode node) {
        if (entity instanceof VillagerEntityMCA villager && !villager.isHostile()) {
            return true;
        }
        return node.willBeRemembered();
    }

    @Inject(method = "placeTombstone", at = @At("HEAD"), cancellable = true)
    private void mcavie$placeOnNearestFreeBlock(ServerWorld world, BlockPos entityPos, CallbackInfoReturnable<Optional<BlockPos>> cir) {
        TombstonePlacement.findSpot(world, entityPos).ifPresent(pos -> {
            world.setBlockState(pos, TombstoneBlocks.forTier(TombstoneDetails.tierOf(entity, world)).getDefaultState());
            cir.setReturnValue(Optional.of(pos));
        });
    }
}
