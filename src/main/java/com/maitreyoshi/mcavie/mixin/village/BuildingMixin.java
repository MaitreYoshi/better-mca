package com.maitreyoshi.mcavie.mixin.village;

import com.maitreyoshi.mcavie.village.BuildingQuality;
import net.mca.server.world.data.Building;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
    @Unique
    private final int[] mcavie$bounds = new int[6];

    @Shadow
    private int pos0X;
    @Shadow
    private int pos0Y;
    @Shadow
    private int pos0Z;
    @Shadow
    private int pos1X;
    @Shadow
    private int pos1Y;
    @Shadow
    private int pos1Z;

    @Override
    public BuildingQuality.Measure mcavie$getMeasure() {
        return mcavie$measure;
    }

    @Override
    public void mcavie$setMeasure(BuildingQuality.Measure measure) {
        mcavie$measure = measure;
    }

    @Inject(method = "<init>(Lnet/minecraft/nbt/NbtCompound;)V", at = @At("TAIL"), remap = true)
    private void mcavie$load(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains(MCAVIE$KEY)) {
            mcavie$measure = BuildingQuality.Measure.fromNbt(nbt.getCompound(MCAVIE$KEY));
        }
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void mcavie$save(CallbackInfoReturnable<NbtCompound> cir) {
        if (mcavie$measure != null) {
            cir.getReturnValue().put(MCAVIE$KEY, mcavie$measure.toNbt());
        }
    }

    @Inject(method = "validateBuilding", at = @At("HEAD"))
    private void mcavie$keepBounds(World world, Set<?> blocked, CallbackInfoReturnable<Building.validationResult> cir) {
        mcavie$bounds[0] = pos0X;
        mcavie$bounds[1] = pos0Y;
        mcavie$bounds[2] = pos0Z;
        mcavie$bounds[3] = pos1X;
        mcavie$bounds[4] = pos1Y;
        mcavie$bounds[5] = pos1Z;
    }

    @Redirect(method = "validateBuilding", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;isAir()Z", remap = true))
    private boolean mcavie$passable(BlockState state) {
        return BuildingQuality.isPassable(state);
    }

    @Inject(method = "validateBuilding", at = @At("RETURN"))
    private void mcavie$measure(World world, Set<?> blocked, CallbackInfoReturnable<Building.validationResult> cir) {
        Building self = (Building) (Object) this;
        if (cir.getReturnValue() != Building.validationResult.SUCCESS || world == null || world.isClient) {
            return;
        }
        if (self.getBuildingType().grouped()) {
            mcavie$measure = null;
            return;
        }
        BuildingQuality.Measure previous = mcavie$measure;
        BuildingQuality.Measure measure = BuildingQuality.measure(world, self.getSourceBlock(), self.isStrictScan());
        if (measure.openings() > 0 && previous != null) {
            pos0X = mcavie$bounds[0];
            pos0Y = mcavie$bounds[1];
            pos0Z = mcavie$bounds[2];
            pos1X = mcavie$bounds[3];
            pos1Y = mcavie$bounds[4];
            pos1Z = mcavie$bounds[5];
            measure = new BuildingQuality.Measure(measure.lights(), measure.windows(), measure.decor(), measure.openings(), previous.floor(), previous.storeys());
        }
        mcavie$measure = measure;
    }
}
