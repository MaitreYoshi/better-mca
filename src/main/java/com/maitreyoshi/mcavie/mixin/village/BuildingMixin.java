package com.maitreyoshi.mcavie.mixin.village;

import com.maitreyoshi.mcavie.village.BuildingQuality;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(value = Building.class, remap = false)
public abstract class BuildingMixin implements BuildingQuality.Holder {
    @Unique
    private static final String MCAVIE$KEY = "mca_vie_quality";

    @Unique
    private BuildingQuality.Measure mcavie$measure;

    @Override
    public BuildingQuality.Measure mcavie$getMeasure() {
        return mcavie$measure;
    }

    @Override
    public void mcavie$setMeasure(BuildingQuality.Measure measure) {
        mcavie$measure = measure;
    }

    @Inject(method = "<init>(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"), remap = true)
    private void mcavie$load(CompoundTag nbt, CallbackInfo ci) {
        if (nbt.contains(MCAVIE$KEY)) {
            mcavie$measure = BuildingQuality.Measure.fromNbt(nbt.getCompound(MCAVIE$KEY));
        }
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void mcavie$save(CallbackInfoReturnable<CompoundTag> cir) {
        if (mcavie$measure != null) {
            cir.getReturnValue().put(MCAVIE$KEY, mcavie$measure.toNbt());
        }
    }

    @Redirect(method = "validateBuilding", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;isAir()Z", remap = true))
    private boolean mcavie$passable(BlockState state) {
        return BuildingQuality.isPassable(state);
    }

    @Inject(method = "validateBuilding", at = @At("RETURN"))
    private void mcavie$measure(Level world, Set<?> blocked, CallbackInfoReturnable<Building.validationResult> cir) {
        Building self = (Building) (Object) this;
        if (cir.getReturnValue() != Building.validationResult.SUCCESS || world == null || world.isClientSide) {
            return;
        }
        if (self.getBuildingType().grouped()) {
            mcavie$measure = null;
            return;
        }
        mcavie$measure = BuildingQuality.measure(world, self.getSourceBlock(), self.isStrictScan());
    }
}
