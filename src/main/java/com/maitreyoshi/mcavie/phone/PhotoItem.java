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
import net.minecraft.util.math.Vec3d;
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

    public interface Placer {
        void open(BlockPos clicked, Direction side, Hand hand, UUID photo, Direction top);
    }

    @Nullable
    public static Placer placer;

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        Direction side = context.getSide();
        ItemStack stack = context.getStack();
        UUID photo = photoOf(stack);
        PlayerEntity player = context.getPlayer();
        if (photo == null || player == null) {
            return ActionResult.PASS;
        }
        if (!player.canPlaceOn(context.getBlockPos().offset(side), side, stack)) {
            return ActionResult.FAIL;
        }
        if (context.getWorld().isClient) {
            if (placer != null) {
                placer.open(context.getBlockPos(), side, context.getHand(), photo, PhotoFrameEntity.topFor(side, player.getHorizontalFacing()));
            }
            return ActionResult.SUCCESS;
        }
        return ActionResult.CONSUME;
    }

    public static void place(ServerPlayerEntity player, BlockPos clicked, Direction side, Hand hand, int width, int height) {
        ItemStack stack = player.getStackInHand(hand);
        UUID photo = photoOf(stack);
        BlockPos pos = clicked.offset(side);
        World world = player.getWorld();
        if (photo == null || player.squaredDistanceTo(Vec3d.ofCenter(pos)) > 64 || !player.canPlaceOn(pos, side, stack)
                || !world.canPlayerModifyAt(player, clicked)) {
            return;
        }
        NbtCompound nbt = stack.getSubNbt(PHOTO_KEY);
        Direction top = PhotoFrameEntity.topFor(side, player.getHorizontalFacing());
        PhotoFrameEntity frame = new PhotoFrameEntity(world, pos, side, top, width, height, photo, nbt.getString("author"), nbt.getLong("date"));
        if (!frame.canStayAttached()) {
            player.sendMessage(Text.translatable("msg.mca_vie.photo.no_support"), true);
            return;
        }
        frame.onPlace();
        world.spawnEntity(frame);
        if (!player.getAbilities().creativeMode) {
            stack.decrement(1);
        }
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
