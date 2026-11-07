package com.maitreyoshi.mcavie.mixin;

import com.maitreyoshi.mcavie.life.LifeCycle;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Pregnancy;
import net.mca.entity.ai.relationship.RelationshipState;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Pregnancy.class, remap = false)
public abstract class PregnancyMixin {
    @Shadow
    @Final
    private VillagerEntityMCA mother;

    @Inject(method = "tryStartGestation", at = @At("HEAD"), cancellable = true)
    private void mcavie$menopause(CallbackInfoReturnable<Boolean> cir) {
        if (LifeCycle.isMenopausal(mother) || mcavie$sameSexPartner()) {
            cir.setReturnValue(false);
        }
    }

    @Unique
    private boolean mcavie$sameSexPartner() {
        if (!(mother.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        FamilyTreeNode node = mother.getRelationships().getFamilyEntry();
        return node.getRelationshipState() == RelationshipState.MARRIED_TO_VILLAGER
                && FamilyTree.get(world).getOrEmpty(node.partner()).map(p -> p.gender() == node.gender()).orElse(false);
    }
}
