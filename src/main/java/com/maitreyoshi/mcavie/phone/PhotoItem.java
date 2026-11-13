package com.maitreyoshi.mcavie.phone;

import com.maitreyoshi.mcavie.compat.Nbt;
import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.calendar.WorldCalendar.CalendarDate;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class PhotoItem extends Item {
    private static final String PHOTO_KEY = "mca_vie_photo";

    public PhotoItem(Properties settings) {
        super(settings);
    }

    public static ItemStack create(UUID photo, String author, long date) {
        ItemStack stack = new ItemStack(PhoneServer.PHOTO);
        CompoundTag nbt = stack.getOrCreateTagElement(PHOTO_KEY);
        Nbt.putUUID(nbt, "id", photo);
        nbt.putString("author", author);
        nbt.putLong("date", date);
        return stack;
    }

    @Nullable
    public static UUID photoOf(ItemStack stack) {
        CompoundTag nbt = stack.getItem() instanceof PhotoItem ? stack.getTagElement(PHOTO_KEY) : null;
        return nbt != null && Nbt.hasUUID(nbt, "id") ? Nbt.getUUID(nbt, "id") : null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        UUID photo = photoOf(stack);
        if (photo == null) {
            return InteractionResultHolder.pass(stack);
        }
        if (user instanceof ServerPlayer player) {
            PhoneServer.viewPrint(player, photo);
        }
        return InteractionResultHolder.sidedSuccess(stack, world.isClientSide());
    }

    public interface Placer {
        void open(BlockPos clicked, Direction side, InteractionHand hand, UUID photo, Direction top);
    }

    @Nullable
    public static Placer placer;

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Direction side = context.getClickedFace();
        ItemStack stack = context.getItemInHand();
        UUID photo = photoOf(stack);
        Player player = context.getPlayer();
        if (photo == null || player == null) {
            return InteractionResult.PASS;
        }
        if (!player.mayUseItemAt(context.getClickedPos().relative(side), side, stack)) {
            return InteractionResult.FAIL;
        }
        if (context.getLevel().isClientSide()) {
            if (placer != null) {
                placer.open(context.getClickedPos(), side, context.getHand(), photo, PhotoFrameEntity.topFor(side, player.getDirection()));
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }

    public static void place(ServerPlayer player, BlockPos clicked, Direction side, InteractionHand hand, int width, int height) {
        ItemStack stack = player.getItemInHand(hand);
        UUID photo = photoOf(stack);
        BlockPos pos = clicked.relative(side);
        Level world = player.level();
        if (photo == null || player.distanceToSqr(Vec3.atCenterOf(pos)) > 64 || !player.mayUseItemAt(pos, side, stack)
                || !world.mayInteract(player, clicked)) {
            return;
        }
        CompoundTag nbt = stack.getTagElement(PHOTO_KEY);
        Direction top = PhotoFrameEntity.topFor(side, player.getDirection());
        PhotoFrameEntity frame = new PhotoFrameEntity(world, pos, side, top, width, height, photo, nbt.getStringOr("author", ""), nbt.getLongOr("date", 0L));
        if (!frame.survives()) {
            player.sendOverlayMessage(Component.translatable("msg.mca_vie.photo.no_support"));
            return;
        }
        frame.playPlacementSound();
        world.addFreshEntity(frame);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> tooltip, TooltipFlag context) {
        CompoundTag nbt = stack.getTagElement(PHOTO_KEY);
        if (nbt == null) {
            return;
        }
        if (!nbt.getStringOr("author", "").isEmpty()) {
            tooltip.add(Component.translatable("item.mca_vie.photo.author", nbt.getStringOr("author", "")).withStyle(ChatFormatting.GRAY));
        }
        CalendarDate date = WorldCalendar.flowingFromTicks(nbt.getLongOr("date", 0L));
        tooltip.add(Component.translatable("item.mca_vie.photo.date", date.day() + "/" + date.month() + "/" + date.year()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.mca_vie.photo.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
