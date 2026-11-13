package com.maitreyoshi.mcavie.mixin;

import com.maitreyoshi.mcavie.life.LifeCycle;
import com.maitreyoshi.mcavie.life.Wellbeing;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.BreedableRelationship;
import net.conczin.mca.entity.ai.Relationship;
import net.conczin.mca.entity.ai.Memories;
import net.conczin.mca.entity.interaction.gifts.Response;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
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
    private void mcavie$cureWithGift(ServerPlayer player, Memories memory, CallbackInfo ci) {
        ItemStack stack = player.getMainHandItem();
        if (!mcavie$isRemedy(stack)) {
            return;
        }
        VillagerEntityMCA villager = entity;
        if (Wellbeing.cure(villager)) {
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.displayClientMessage(Component.translatable("gui.mca_vie.cured", villager.getName()), true);
            ci.cancel();
        }
    }

    @Inject(method = "startProcreating", at = @At("HEAD"), cancellable = true)
    private void mcavie$menopause(long time, CallbackInfo ci) {
        if (LifeCycle.isMenopausal(entity)) {
            entity.sendChatToAllAround(Component.translatable("msg.mca_vie.menopause"));
            ci.cancel();
        }
    }

    @Unique
    private static boolean mcavie$isRemedy(ItemStack stack) {
        if (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
            return true;
        }
        var potion = PotionUtils.getPotion(stack);
        return potion == Potions.HEALING || potion == Potions.STRONG_HEALING
                || potion == Potions.REGENERATION || potion == Potions.STRONG_REGENERATION || potion == Potions.LONG_REGENERATION;
    }

    @ModifyVariable(method = "acceptGift", at = @At("STORE"), ordinal = 0)
    private Response mcavie$happyWithGift(Response response) {
        if (response != Response.FAIL && entity.level() instanceof ServerLevel world) {
            Wellbeing.addHappiness(world, entity.getUUID(), Wellbeing.LIKED_GIFT);
        }
        return response;
    }
}
