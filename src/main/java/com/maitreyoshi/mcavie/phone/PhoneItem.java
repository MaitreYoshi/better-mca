package com.maitreyoshi.mcavie.phone;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class PhoneItem extends Item {
    public static final String NUMBER_KEY = "mca_vie_number";

    public PhoneItem(Properties settings) {
        super(settings);
    }

    public static int numberOf(ItemStack stack) {
        if (!(stack.getItem() instanceof PhoneItem)) {
            return -1;
        }
        CompoundTag nbt = stack.getTag();
        if (nbt == null || !nbt.contains(NUMBER_KEY, Tag.TAG_INT)) {
            return -1;
        }
        int number = nbt.getInt(NUMBER_KEY);
        return PhoneNetwork.validNumber(number) ? number : -1;
    }

    public static void setNumber(ItemStack stack, int number) {
        stack.getOrCreateTag().putInt(NUMBER_KEY, number);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (user instanceof ServerPlayer player) {
            PhoneServer.open(player, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, world.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> tooltip, TooltipFlag context) {
        int number = numberOf(stack);
        if (number >= 0) {
            tooltip.add(Component.translatable("item.mca_vie.phone.number", PhoneRegistry.format(number)).withStyle(ChatFormatting.GRAY));
        }
    }
}
