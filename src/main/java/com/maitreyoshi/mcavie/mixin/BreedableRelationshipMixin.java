package com.maitreyoshi.mcavie.mixin;

import com.maitreyoshi.mcavie.life.LifeCycle;
import com.maitreyoshi.mcavie.life.Wellbeing;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.BreedableRelationship;
import net.mca.entity.ai.Relationship;
import net.mca.entity.ai.Memories;
import net.mca.entity.interaction.gifts.Response;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BreedableRelationship.class, remap = false)
public abstract class BreedableRelationshipMixin extends Relationship<VillagerEntityMCA> {
    private BreedableRelationshipMixin(VillagerEntityMCA entity) {
        super(entity);
    }

    @Inject(method = "giveGift", at = @At("HEAD"), cancellable = true)
    private void mcavie$cureWithGift(ServerPlayerEntity player, Memories memory, CallbackInfo ci) {
        ItemStack stack = player.getMainHandStack();
        if (!mcavie$isRemedy(stack)) {
            return;
        }
        VillagerEntityMCA villager = entity;
        if (Wellbeing.cure(villager)) {
            if (!player.getAbilities().creativeMode) {
                stack.decrement(1);
            }
            player.sendMessage(Text.translatable("gui.mca_vie.cured", villager.getName()), true);
            ci.cancel();
        }
    }

    @Inject(method = "startProcreating", at = @At("HEAD"), cancellable = true)
    private void mcavie$menopause(long time, CallbackInfo ci) {
        if (LifeCycle.isMenopausal(entity)) {
            entity.sendChatToAllAround(Text.translatable("msg.mca_vie.menopause"));
            ci.cancel();
        }
    }

    @Unique
    private static boolean mcavie$isRemedy(ItemStack stack) {
        if (stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)) {
            return true;
        }
        var potion = PotionUtil.getPotion(stack);
        return potion == Potions.HEALING || potion == Potions.STRONG_HEALING
                || potion == Potions.REGENERATION || potion == Potions.STRONG_REGENERATION || potion == Potions.LONG_REGENERATION;
    }

    @ModifyVariable(method = "acceptGift", at = @At("STORE"), ordinal = 0)
    private Response mcavie$happyWithGift(Response response) {
        if (response != Response.FAIL && entity.getWorld() instanceof ServerWorld world) {
            Wellbeing.addHappiness(world, entity.getUuid(), Wellbeing.LIKED_GIFT);
        }
        return response;
    }
}
