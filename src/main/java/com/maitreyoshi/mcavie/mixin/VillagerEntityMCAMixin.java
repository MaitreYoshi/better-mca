package com.maitreyoshi.mcavie.mixin;

import net.minecraft.world.entity.EntityTypes;
import com.maitreyoshi.mcavie.Surnames;
import com.maitreyoshi.mcavie.aging.AgeHolder;
import com.maitreyoshi.mcavie.aging.Aging;
import com.maitreyoshi.mcavie.life.LifeCycle;
import com.maitreyoshi.mcavie.life.Orientation;
import com.maitreyoshi.mcavie.life.Wellbeing;
import com.maitreyoshi.mcavie.social.Social;
import com.maitreyoshi.mcavie.social.SocialChat;
import com.maitreyoshi.mcavie.work.WorkManager;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VillagerEntityMCA.class)
public abstract class VillagerEntityMCAMixin implements AgeHolder {
    @Unique
    private int mcavie$ageYears = -1;
    @Unique
    private int mcavie$cooldown;
    @Unique
    private int mcavie$checks;
    @Unique
    private boolean mcavie$started;

    @Inject(method = "tick", at = @At("HEAD"))
    private void mcavie$tickHandSwing(CallbackInfo ci) {
        ((LivingEntityInvoker) this).mcavie$tickHandSwing();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void mcavie$checkSurname(CallbackInfo ci) {
        VillagerEntityMCA self = (VillagerEntityMCA) (Object) this;
        if (self.level().isClientSide() || self.isDeadOrDying()) {
            return;
        }
        WorkManager.keepWorking(self);
        SocialChat.hold(self);
        if (!mcavie$started) {
            mcavie$started = true;
            mcavie$checks = Math.floorMod(self.getId(), 5);
        }
        if (mcavie$cooldown-- > 0) {
            return;
        }
        mcavie$cooldown = 20;
        Orientation.update(self);
        Surnames.update(self);
        LifeCycle.update(self);
        Wellbeing.update(self, mcavie$checks++ % 5 == 0);
        Social.update(self);
        WorkManager.update(self);
        com.maitreyoshi.mcavie.work.RepairErrands.update(self);
    }

    @Inject(method = "convertTo", at = @At("HEAD"))
    private void mcavie$zombified(net.minecraft.world.entity.EntityType<?> type, net.minecraft.world.entity.ConversionParams params, net.minecraft.world.entity.ConversionParams.AfterConversion<?> after, CallbackInfoReturnable<?> cir) {
        VillagerEntityMCA self = (VillagerEntityMCA) (Object) this;
        if (type == net.minecraft.world.entity.EntityTypes.ZOMBIE_VILLAGER && self.level() instanceof ServerLevel world) {
            com.maitreyoshi.mcavie.village.CivilRegistry.zombified(self, world);
            com.maitreyoshi.mcavie.life.LifeRegistry.get(world.getServer()).setZombified(self.getUUID(),
                    com.maitreyoshi.mcavie.calendar.WorldCalendar.now(world).dayIndex());
            WorkManager.forget(self.getUUID());
        }
    }

    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void mcavie$hurtFeelings(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        VillagerEntityMCA self = (VillagerEntityMCA) (Object) this;
        if (cir.getReturnValueZ() && amount > 0 && source.getEntity() != null && self.isAlive()
                && self.level() instanceof ServerLevel world) {
            Wellbeing.addHappiness(world, self.getUUID(), source.getEntity() instanceof Player ? Wellbeing.HIT_BY_PLAYER : Wellbeing.HIT);
            if (source.getEntity() instanceof VillagerEntityMCA attacker) {
                Social.hit(world, self.getUUID(), attacker.getUUID());
            }
        }
    }

    @Inject(method = "getVoicePitch", at = @At("RETURN"), cancellable = true)
    private void mcavie$olderVoice(CallbackInfoReturnable<Float> cir) {
        float drop = Aging.voicePitchDrop((VillagerEntityMCA) (Object) this);
        if (drop > 0) {
            cir.setReturnValue(Math.max(0.5f, cir.getReturnValueF() - drop));
        }
    }

    @Override
    public int mcavie$getAgeYears() {
        return mcavie$ageYears;
    }

    @Override
    public void mcavie$setAgeYears(int years) {
        mcavie$ageYears = years;
    }
}
