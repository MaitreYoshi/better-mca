package com.maitreyoshi.mcavie.phone;

import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.calendar.WorldCalendar.CalendarDate;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class PhotoItem extends Item {
    private static final String PHOTO_KEY = "mca_vie_photo";

    public PhotoItem(Settings settings) {
        super(settings);
    }

    public static ItemStack create(UUID photo, String author, long date) {
        ItemStack stack = new ItemStack(PhoneServer.PHOTO);
        NbtCompound nbt = stack.getOrCreateSubNbt(PHOTO_KEY);
        nbt.putUuid("id", photo);
        nbt.putString("author", author);
        nbt.putLong("date", date);
        return stack;
    }

    @Nullable
    public static UUID photoOf(ItemStack stack) {
        NbtCompound nbt = stack.getItem() instanceof PhotoItem ? stack.getSubNbt(PHOTO_KEY) : null;
        return nbt != null && nbt.containsUuid("id") ? nbt.getUuid("id") : null;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        UUID photo = photoOf(stack);
        if (photo == null) {
            return TypedActionResult.pass(stack);
        }
        if (user instanceof ServerPlayerEntity player) {
            PhoneServer.viewPrint(player, photo);
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        Direction side = context.getSide();
        ItemStack stack = context.getStack();
        UUID photo = photoOf(stack);
        PlayerEntity player = context.getPlayer();
        if (side.getAxis().isVertical() || photo == null || player == null) {
            return ActionResult.PASS;
        }
        BlockPos pos = context.getBlockPos().offset(side);
        if (!player.canPlaceOn(pos, side, stack)) {
            return ActionResult.FAIL;
        }
        World world = context.getWorld();
        NbtCompound nbt = stack.getSubNbt(PHOTO_KEY);
        PhotoFrameEntity frame = new PhotoFrameEntity(world, pos, side, photo, nbt.getString("author"), nbt.getLong("date"));
        if (!frame.canStayAttached()) {
            return ActionResult.FAIL;
        }
        if (!world.isClient) {
            frame.onPlace();
            world.spawnEntity(frame);
        }
        if (!player.getAbilities().creativeMode) {
            stack.decrement(1);
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        NbtCompound nbt = stack.getSubNbt(PHOTO_KEY);
        if (nbt == null) {
            return;
        }
        if (!nbt.getString("author").isEmpty()) {
            tooltip.add(Text.translatable("item.mca_vie.photo.author", nbt.getString("author")).formatted(Formatting.GRAY));
        }
        CalendarDate date = WorldCalendar.flowingFromTicks(nbt.getLong("date"));
        tooltip.add(Text.translatable("item.mca_vie.photo.date", date.day() + "/" + date.month() + "/" + date.year()).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.mca_vie.photo.hint").formatted(Formatting.DARK_GRAY));
    }
}
