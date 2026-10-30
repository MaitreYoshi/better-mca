package com.maitreyoshi.mcavie.tree.server.mixin;

import com.maitreyoshi.mcavie.tree.server.SpouseRegistry;
import net.mca.entity.ai.relationship.RelationshipState;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;
import java.util.UUID;

@Mixin(value = FamilyTreeNode.class, remap = false)
public abstract class FamilyTreeNodeMixin {
    @Shadow
    @Final
    private UUID id;

    @Shadow
    private UUID father;

    @Shadow
    private UUID mother;

    @Shadow
    private UUID partner;

    @Shadow
    private RelationshipState relationshipState;

    @Shadow
    public abstract FamilyTree getRoot();

    @Inject(method = "setFather", at = @At("HEAD"))
    private void mcavie$detachFromOldFather(FamilyTreeNode parent, CallbackInfoReturnable<Boolean> cir) {
        if (!Objects.equals(father, mother)) {
            mcavie$detachFrom(father, parent);
        }
    }

    @Inject(method = "setMother", at = @At("HEAD"))
    private void mcavie$detachFromOldMother(FamilyTreeNode parent, CallbackInfoReturnable<Boolean> cir) {
        if (!Objects.equals(mother, father)) {
            mcavie$detachFrom(mother, parent);
        }
    }

    private void mcavie$detachFrom(UUID oldParent, FamilyTreeNode newParent) {
        FamilyTree root = getRoot();
        if (root != null && FamilyTreeNode.isValid(oldParent) && !oldParent.equals(newParent.id())) {
            root.getOrEmpty(oldParent).ifPresent(old -> old.children().remove(id));
        }
    }

    @Inject(method = "updatePartner*", at = @At("HEAD"))
    private void mcavie$rememberPreviousSpouse(CallbackInfo ci) {
        if (FamilyTreeNode.isValid(partner) && (relationshipState == RelationshipState.WIDOW
                || relationshipState != null && relationshipState.isMarried())) {
            SpouseRegistry.record(id, partner);
        }
    }

    @Inject(method = "updatePartner*", at = @At("TAIL"))
    private void mcavie$rememberNewSpouse(CallbackInfo ci) {
        if (FamilyTreeNode.isValid(partner) && relationshipState != null && relationshipState.isMarried()) {
            SpouseRegistry.record(id, partner);
        }
    }
}
