package com.maitreyoshi.mcavie.mixin.village;

import com.maitreyoshi.mcavie.village.VillageServer;
import com.maitreyoshi.mcavie.village.VillageState;
import net.mca.server.world.data.Village;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Village.class, remap = false)
public abstract class VillageMixin implements VillageState.Holder {
    @Unique
    private final VillageState mcavie$state = new VillageState();

    @Override
    public VillageState mcavie$getState() {
        return mcavie$state;
    }

    @Inject(method = "<init>(Lnet/minecraft/nbt/NbtCompound;Lnet/minecraft/server/world/ServerWorld;)V", at = @At("TAIL"), remap = true)
    private void mcavie$load(NbtCompound nbt, ServerWorld world, CallbackInfo ci) {
        if (nbt.contains(VillageState.NBT_KEY)) {
            mcavie$state.readNbt(nbt.getCompound(VillageState.NBT_KEY));
        }
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void mcavie$save(CallbackInfoReturnable<NbtCompound> cir) {
        cir.getReturnValue().put(VillageState.NBT_KEY, mcavie$state.toNbt());
    }

    @Inject(method = "merge", at = @At("HEAD"))
    private void mcavie$merge(Village other, CallbackInfo ci) {
        mcavie$state.merge(VillageState.of(other));
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void mcavie$tick(ServerWorld world, long time, CallbackInfo ci) {
        VillageServer.tick((Village) (Object) this, world, time);
    }
}
