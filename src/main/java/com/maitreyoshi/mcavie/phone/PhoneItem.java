package com.maitreyoshi.mcavie.phone;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PhoneItem extends Item {
    public static final String NUMBER_KEY = "mca_vie_number";

    public PhoneItem(Settings settings) {
        super(settings);
    }

    public static int numberOf(ItemStack stack) {
        if (!(stack.getItem() instanceof PhoneItem)) {
            return -1;
        }
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(NUMBER_KEY, NbtElement.INT_TYPE)) {
            return -1;
        }
        int number = nbt.getInt(NUMBER_KEY);
        return PhoneNetwork.validNumber(number) ? number : -1;
    }

    public static void setNumber(ItemStack stack, int number) {
        stack.getOrCreateNbt().putInt(NUMBER_KEY, number);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user instanceof ServerPlayerEntity player) {
            PhoneServer.open(player, stack);
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        int number = numberOf(stack);
        if (number >= 0) {
            tooltip.add(Text.translatable("item.mca_vie.phone.number", PhoneRegistry.format(number)).formatted(Formatting.GRAY));
        }
    }
}
