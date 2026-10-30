package com.maitreyoshi.mcavie.tree.server.mixin;

import com.maitreyoshi.mcavie.tree.server.TreeRepair;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.minecraft.world.PersistentState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;

@Mixin(value = FamilyTree.class, remap = false)
public abstract class FamilyTreeMixin {
    @Shadow
    @Final
    private Map<UUID, FamilyTreeNode> entries;

    @Inject(method = "<init>*", at = @At("TAIL"))
    private void mcavie$repairLinks(CallbackInfo ci) {
        if (TreeRepair.repair(entries)) {
            ((PersistentState) (Object) this).markDirty();
        }
    }
}
