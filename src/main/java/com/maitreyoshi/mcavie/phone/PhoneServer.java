package com.maitreyoshi.mcavie.phone;

import com.mojang.authlib.properties.Property;
import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.phone.PhoneRegistry.Conversation;
import com.maitreyoshi.mcavie.phone.PhoneRegistry.Message;
import com.maitreyoshi.mcavie.phone.PhoneRegistry.Phone;
import com.maitreyoshi.mcavie.phone.PhoneRegistry.Photo;
import com.maitreyoshi.mcavie.compat.ServerLifecycleEvents;
import com.maitreyoshi.mcavie.compat.Registration;
import dev.architectury.registry.CreativeTabRegistry;
import net.mca.server.world.data.FamilyTree;
import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import com.maitreyoshi.mcavie.compat.ServerPlayConnectionEvents;
import com.maitreyoshi.mcavie.compat.ServerPlayNetworking;
import net.minecraft.SharedConstants;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PhoneServer {
    public static Item PHONE;
    public static Item PHOTO;
    public static EntityType<PhotoFrameEntity> PHOTO_FRAME;
    private static final long PRINT_COOLDOWN = 20;
    private static final double PRINT_VIEW_DISTANCE = 128;
    private static final Map<UUID, Long> LAST_PRINT = new HashMap<>();

    private static final long UNLOCK_COOLDOWN = 20;
    private static final long SEND_COOLDOWN = 10;
    private static final Map<UUID, Integer> UNLOCKED = new HashMap<>();
    private static final Map<UUID, Long> LAST_UNLOCK = new HashMap<>();
    private static final Map<UUID, Long> LAST_SEND = new HashMap<>();
    private static final Map<UUID, Long> LAST_PHOTO = new HashMap<>();
    private static final Map<UUID, Upload> UPLOADS = new HashMap<>();
    private static final long PHOTO_COOLDOWN = 40;
    private static final int MAX_UPLOAD = PhoneNetwork.MAX_FULL_BYTES + PhoneNetwork.MAX_THUMB_BYTES;
    private static final int MAX_CHUNKS = (MAX_UPLOAD + PhoneNetwork.PHOTO_CHUNK - 1) / PhoneNetwork.PHOTO_CHUNK;

    private static final class Upload {
        final int number;
        final int id;
        final int total;
        final int thumbnail;
        int received;
        final ByteArrayOutputStream data = new ByteArrayOutputStream();

        Upload(int number, int id, int total, int thumbnail) {
            this.number = number;
            this.id = id;
            this.total = total;
            this.thumbnail = thumbnail;
        }
    }

    private PhoneServer() {
    }

    public static void register() {
        Registration.register(RegistryKeys.ITEM, "phone", () -> new PhoneItem(new Item.Settings().maxCount(1)), item -> PHONE = item);
        Registration.register(RegistryKeys.ITEM, "photo", () -> new PhotoItem(new Item.Settings().maxCount(1)), item -> PHOTO = item);
        Registration.register(RegistryKeys.ENTITY_TYPE, "photo_frame",
                () -> EntityType.Builder.<PhotoFrameEntity>create(PhotoFrameEntity::new, SpawnGroup.MISC)
                        .setDimensions(0.5f, 0.5f).maxTrackingRange(10).trackingTickInterval(Integer.MAX_VALUE).build(MCAVie.id("photo_frame").toString()),
                type -> PHOTO_FRAME = type);
        CreativeTabRegistry.modify(CreativeTabRegistry.defer(ItemGroups.TOOLS), (flags, entries, operator) -> entries.add(PHONE));
        ServerPlayNetworking.registerGlobalReceiver(PhoneNetwork.ACTION, (server, player, handler, buf, responseSender) -> {
            int number = buf.readVarInt();
            int op = buf.readVarInt();
            int other = -1;
            String text = "";
            UUID photo = null;
            UUID person = null;
            switch (op) {
                case PhoneNetwork.OP_DELETE_PHOTO, PhoneNetwork.OP_SAVE_PHOTO, PhoneNetwork.OP_PRINT_PHOTO -> photo = buf.readUuid();
                case PhoneNetwork.OP_PHOTO -> {
                    photo = buf.readUuid();
                    other = buf.readBoolean() ? 1 : 0;
                }
                case PhoneNetwork.OP_SEND_PHOTO -> {
                    other = buf.readVarInt();
                    photo = buf.readUuid();
                }
                case PhoneNetwork.OP_SET_PORTRAIT -> {
                    person = buf.readUuid();
                    photo = buf.readUuid();
                }
                case PhoneNetwork.OP_UNLOCK, PhoneNetwork.OP_SET_CODE -> text = buf.readString(PhoneNetwork.CODE_LENGTH);
                case PhoneNetwork.OP_CONVERSATION, PhoneNetwork.OP_ADD_CONTACT, PhoneNetwork.OP_REMOVE_CONTACT -> other = buf.readVarInt();
                case PhoneNetwork.OP_SEND -> {
                    other = buf.readVarInt();
                    text = buf.readString(PhoneNetwork.MAX_TEXT);
                }
                case PhoneNetwork.OP_FOLDER_CREATE -> text = buf.readString(PhoneNetwork.MAX_FOLDER_NAME);
                case PhoneNetwork.OP_FOLDER_RENAME -> {
                    other = buf.readVarInt();
                    text = buf.readString(PhoneNetwork.MAX_FOLDER_NAME);
                }
                case PhoneNetwork.OP_FOLDER_DELETE -> other = buf.readVarInt();
                case PhoneNetwork.OP_MOVE_PHOTO -> {
                    photo = buf.readUuid();
                    other = buf.readVarInt();
                }
                default -> {
                }
            }
            int target = other;
            String value = text;
            UUID picture = photo;
            UUID portrait = person;
            server.execute(() -> handle(player, number, op, target, value, picture, portrait));
        });
        ServerPlayNetworking.registerGlobalReceiver(PhoneNetwork.PHOTO_UPLOAD, (server, player, handler, buf, responseSender) -> {
            int number = buf.readVarInt();
            int id = buf.readVarInt();
            int index = buf.readVarInt();
            int total = buf.readVarInt();
            int thumbnail = buf.readVarInt();
            byte[] chunk = buf.readByteArray(PhoneNetwork.PHOTO_CHUNK);
            server.execute(() -> receiveChunk(player, number, id, index, total, thumbnail, chunk));
        });
        ServerPlayNetworking.registerGlobalReceiver(PhoneNetwork.PRINT_REQUEST, (server, player, handler, buf, responseSender) -> {
            UUID photo = buf.readUuid();
            boolean full = buf.readBoolean();
            server.execute(() -> sendPrint(player, photo, full));
        });
        ServerPlayNetworking.registerGlobalReceiver(PhoneNetwork.PHOTO_PLACE, (server, player, handler, buf, responseSender) -> {
            BlockPos clicked = buf.readBlockPos();
            Direction side = buf.readEnumConstant(Direction.class);
            Hand hand = buf.readEnumConstant(Hand.class);
            int width = buf.readVarInt();
            int height = buf.readVarInt();
            server.execute(() -> PhotoItem.place(player, clicked, side, hand, width, height));
        });
        ServerPlayNetworking.registerGlobalReceiver(PhoneNetwork.PHOTO_RESIZE, (server, player, handler, buf, responseSender) -> {
            int id = buf.readVarInt();
            int width = buf.readVarInt();
            int height = buf.readVarInt();
            server.execute(() -> {
                if (player.getServerWorld().getEntityById(id) instanceof PhotoFrameEntity frame && frame.getBoundingBox().expand(8).contains(player.getPos())) {
                    frame.resize(width, height);
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(PhoneNetwork.PORTRAIT_REQUEST, (server, player, handler, buf, responseSender) -> {
            UUID image = buf.readUuid();
            server.execute(() -> {
                if (PortraitRegistry.get(server).isPortrait(image)) {
                    sendImage(player, image, false);
                }
            });
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            PhoneRegistry registry = PhoneRegistry.get(server);
            for (Phone phone : registry.all().values()) {
                if (handler.player.getUuid().equals(phone.owner) && updateOwner(phone, handler.player)) {
                    registry.markDirty();
                }
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> {
            UUID uuid = handler.player.getUuid();
            UNLOCKED.remove(uuid);
            LAST_UNLOCK.remove(uuid);
            LAST_SEND.remove(uuid);
            LAST_PHOTO.remove(uuid);
            LAST_PRINT.remove(uuid);
            UPLOADS.remove(uuid);
        }));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            UNLOCKED.clear();
            LAST_UNLOCK.clear();
            LAST_SEND.clear();
            LAST_PHOTO.clear();
            LAST_PRINT.clear();
            UPLOADS.clear();
        });
    }

    public static void open(ServerPlayerEntity player, ItemStack stack) {
        PhoneRegistry registry = PhoneRegistry.get(player.getServer());
        int number = PhoneItem.numberOf(stack);
        if (number < 0 || registry.get(number) == null) {
            number = registry.allocate(player.getRandom());
            if (number < 0) {
                player.sendMessage(Text.translatable("msg.mca_vie.phone.no_number"), true);
                return;
            }
            PhoneItem.setNumber(stack, number);
        }
        Phone phone = registry.get(number);
        if (phone.owner == null) {
            phone.owner = player.getUuid();
            registry.markDirty();
        }
        if (player.getUuid().equals(phone.owner) && updateOwner(phone, player)) {
            registry.markDirty();
        }
        UNLOCKED.remove(player.getUuid());
        sendState(player, number, phone, true);
    }

    private static void handle(ServerPlayerEntity player, int number, int op, int other, String text, @Nullable UUID photo, @Nullable UUID person) {
        if (!PhoneNetwork.validNumber(number) || !holds(player, number)) {
            return;
        }
        PhoneRegistry registry = PhoneRegistry.get(player.getServer());
        Phone phone = registry.get(number);
        if (phone == null) {
            return;
        }
        long time = player.getServerWorld().getTime();
        if (op == PhoneNetwork.OP_UNLOCK) {
            if (!cooldown(LAST_UNLOCK, player, time, UNLOCK_COOLDOWN)) {
                return;
            }
            if (phone.code == null || phone.code.equals(text)) {
                UNLOCKED.put(player.getUuid(), number);
            } else {
                error(player, "msg.mca_vie.phone.wrong_code");
            }
            sendState(player, number, phone, false);
            return;
        }
        if (!canAccess(player, number, phone)) {
            sendState(player, number, phone, false);
            return;
        }
        switch (op) {
            case PhoneNetwork.OP_REFRESH -> sendState(player, number, phone, false);
            case PhoneNetwork.OP_CONVERSATION -> {
                if (!PhoneNetwork.validNumber(other)) {
                    return;
                }
                Conversation conversation = phone.conversations.get(other);
                if (conversation != null && conversation.unread > 0) {
                    conversation.unread = 0;
                    registry.markDirty();
                    sendState(player, number, phone, false);
                }
                sendConversation(player, number, phone, other);
            }
            case PhoneNetwork.OP_SEND -> send(player, registry, number, phone, other, clean(text), null, time);
            case PhoneNetwork.OP_SEND_PHOTO -> {
                if (photo != null && phone.gallery.stream().anyMatch(p -> p.id().equals(photo))) {
                    send(player, registry, number, phone, other, "", photo, time);
                }
            }
            case PhoneNetwork.OP_PHOTO -> {
                if (photo == null || !phone.references(photo)) {
                    return;
                }
                sendImage(player, photo, other == 1);
            }
            case PhoneNetwork.OP_SET_PORTRAIT -> setPortrait(player, phone, person, photo, time);
            case PhoneNetwork.OP_SAVE_PHOTO -> {
                if (photo == null || !phone.references(photo) || phone.gallery.stream().anyMatch(p -> p.id().equals(photo))) {
                    return;
                }
                if (phone.gallery.size() >= PhoneNetwork.MAX_PHOTOS) {
                    error(player, "msg.mca_vie.phone.gallery_full");
                    return;
                }
                phone.gallery.add(0, new Photo(photo, player.getServer().getOverworld().getTimeOfDay()));
                registry.markDirty();
                player.sendMessage(Text.translatable("msg.mca_vie.phone.photo_saved"), true);
                sendState(player, number, phone, false);
            }
            case PhoneNetwork.OP_PRINT_PHOTO -> {
                if (photo == null || !phone.references(photo) || !cooldown(LAST_PRINT, player, time, PRINT_COOLDOWN)) {
                    return;
                }
                UUID print = UUID.randomUUID();
                if (!PhotoStore.copy(player.getServer(), photo, print)) {
                    error(player, "msg.mca_vie.phone.print_failed");
                    return;
                }
                long date = phone.gallery.stream().filter(p -> p.id().equals(photo)).map(Photo::time).findFirst()
                        .orElse(player.getServer().getOverworld().getTimeOfDay());
                ItemStack stack = PhotoItem.create(print, player.getGameProfile().getName(), date);
                player.sendMessage(Text.translatable(giveToInventory(player, stack)
                        ? "msg.mca_vie.phone.printed" : "msg.mca_vie.phone.printed_ground"), true);
                ServerPlayNetworking.send(player, PhoneNetwork.PRINTED, PacketByteBufs.empty());
            }
            case PhoneNetwork.OP_FOLDER_CREATE -> {
                String name = folderName(text);
                if (name.isEmpty()) {
                    return;
                }
                if (phone.folders.size() >= PhoneNetwork.MAX_FOLDERS) {
                    error(player, "msg.mca_vie.phone.folders_full");
                    return;
                }
                phone.folders.put(phone.nextFolder++, name);
                registry.markDirty();
                sendState(player, number, phone, false);
            }
            case PhoneNetwork.OP_FOLDER_RENAME -> {
                String name = folderName(text);
                if (!name.isEmpty() && phone.folders.containsKey(other)) {
                    phone.folders.put(other, name);
                    registry.markDirty();
                }
                sendState(player, number, phone, false);
            }
            case PhoneNetwork.OP_FOLDER_DELETE -> {
                if (phone.folders.remove(other) != null) {
                    phone.gallery.replaceAll(p -> p.folder() == other ? new Photo(p.id(), p.time()) : p);
                    registry.markDirty();
                }
                sendState(player, number, phone, false);
            }
            case PhoneNetwork.OP_MOVE_PHOTO -> {
                if (photo == null || (other != 0 && !phone.folders.containsKey(other))) {
                    return;
                }
                for (int i = 0; i < phone.gallery.size(); i++) {
                    Photo entry = phone.gallery.get(i);
                    if (entry.id().equals(photo) && entry.folder() != other) {
                        phone.gallery.set(i, new Photo(entry.id(), entry.time(), other));
                        registry.markDirty();
                    }
                }
                sendState(player, number, phone, false);
            }
            case PhoneNetwork.OP_DELETE_PHOTO -> {
                if (photo != null && phone.gallery.removeIf(p -> p.id().equals(photo))) {
                    registry.markDirty();
                    release(player.getServer(), registry, photo);
                }
                sendState(player, number, phone, false);
            }
            case PhoneNetwork.OP_ADD_CONTACT -> {
                if (!PhoneNetwork.validNumber(other)) {
                    return;
                }
                Phone target = registry.get(other);
                if (target == null || target.owner == null || other == number) {
                    error(player, "msg.mca_vie.phone.unknown_number");
                    return;
                }
                if (!phone.contacts.contains(other) && phone.contacts.size() >= PhoneNetwork.MAX_CONTACTS) {
                    error(player, "msg.mca_vie.phone.contacts_full");
                    return;
                }
                phone.contacts.add(other);
                registry.markDirty();
                sendState(player, number, phone, false);
            }
            case PhoneNetwork.OP_REMOVE_CONTACT -> {
                if (phone.contacts.remove(Integer.valueOf(other))) {
                    registry.markDirty();
                }
                sendState(player, number, phone, false);
            }
            case PhoneNetwork.OP_SET_CODE -> {
                if (!player.getUuid().equals(phone.owner) || !(text.isEmpty() || PhoneNetwork.validCode(text))) {
                    return;
                }
                phone.code = text.isEmpty() ? null : text;
                registry.markDirty();
                UNLOCKED.values().removeIf(n -> n == number);
                player.sendMessage(Text.translatable(text.isEmpty() ? "msg.mca_vie.phone.code_removed" : "msg.mca_vie.phone.code_set"), true);
                sendState(player, number, phone, false);
            }
            default -> {
            }
        }
    }

    private static void send(ServerPlayerEntity player, PhoneRegistry registry, int number, Phone phone, int other, String message, @Nullable UUID photo, long time) {
        if (!PhoneNetwork.validNumber(other) || (message.isEmpty() && photo == null) || !cooldown(LAST_SEND, player, time, SEND_COOLDOWN)) {
            return;
        }
        Phone recipient = registry.get(other);
        if (recipient == null || other == number) {
            error(player, "msg.mca_vie.phone.unknown_number");
            return;
        }
        long date = player.getServer().getOverworld().getTimeOfDay();
        List<UUID> dropped = new ArrayList<>();
        append(phone, other, new Message(true, message, date, photo), false, dropped);
        append(recipient, number, new Message(false, message, date, photo), true, dropped);
        registry.markDirty();
        dropped.forEach(id -> release(player.getServer(), registry, id));
        sendState(player, number, phone, false);
        sendConversation(player, number, phone, other);
        notify(player.getServer(), registry, other, recipient, number, photo != null);
    }

    private static void append(Phone phone, int other, Message message, boolean incoming, List<UUID> dropped) {
        Conversation conversation = phone.conversations.computeIfAbsent(other, k -> new Conversation());
        conversation.messages.add(message);
        while (conversation.messages.size() > PhoneNetwork.MAX_MESSAGES) {
            Message removed = conversation.messages.remove(0);
            if (removed.photo() != null) {
                dropped.add(removed.photo());
            }
        }
        conversation.lastTime = message.time();
        if (incoming) {
            conversation.unread = Math.min(conversation.unread + 1, PhoneNetwork.MAX_MESSAGES);
        }
    }

    private static void notify(MinecraftServer server, PhoneRegistry registry, int number, Phone phone, int from, boolean photo) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!carries(player, number)) {
                continue;
            }
            player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 0.8f, 1.6f);
            if (canAccess(player, number, phone)) {
                player.sendMessage(Text.translatable(photo ? "msg.mca_vie.phone.new_photo_from" : "msg.mca_vie.phone.new_from", displayName(registry, from)), true);
                sendState(player, number, phone, false);
            } else {
                player.sendMessage(Text.translatable(photo ? "msg.mca_vie.phone.new_photo" : "msg.mca_vie.phone.new"), true);
            }
        }
    }

    private static void receiveChunk(ServerPlayerEntity player, int number, int id, int index, int total, int thumbnail, byte[] chunk) {
        UUID uuid = player.getUuid();
        PhoneRegistry registry = PhoneRegistry.get(player.getServer());
        Phone phone = PhoneNetwork.validNumber(number) ? registry.get(number) : null;
        if (phone == null || !holds(player, number) || !canAccess(player, number, phone) || total < 1 || total > MAX_CHUNKS) {
            UPLOADS.remove(uuid);
            return;
        }
        Upload upload = UPLOADS.get(uuid);
        if (index == 0) {
            upload = new Upload(number, id, total, thumbnail);
            UPLOADS.put(uuid, upload);
        }
        if (upload == null || upload.number != number || upload.id != id || upload.total != total || upload.thumbnail != thumbnail
                || upload.received != index || upload.data.size() + chunk.length > MAX_UPLOAD) {
            UPLOADS.remove(uuid);
            return;
        }
        upload.data.write(chunk, 0, chunk.length);
        upload.received++;
        if (upload.received < upload.total) {
            return;
        }
        UPLOADS.remove(uuid);
        byte[] data = upload.data.toByteArray();
        if (thumbnail <= 0 || thumbnail >= data.length) {
            return;
        }
        byte[] thumb = Arrays.copyOf(data, thumbnail);
        byte[] full = Arrays.copyOfRange(data, thumbnail, data.length);
        if (!PhotoStore.validThumbnail(thumb) || !PhotoStore.validFull(full) || !cooldown(LAST_PHOTO, player, player.getServerWorld().getTime(), PHOTO_COOLDOWN)) {
            return;
        }
        if (phone.gallery.size() >= PhoneNetwork.MAX_PHOTOS) {
            error(player, "msg.mca_vie.phone.gallery_full");
            return;
        }
        UUID photo = UUID.randomUUID();
        if (!PhotoStore.write(player.getServer(), photo, thumb, full)) {
            return;
        }
        phone.gallery.add(0, new Photo(photo, player.getServer().getOverworld().getTimeOfDay()));
        registry.markDirty();
        player.sendMessage(Text.translatable("msg.mca_vie.phone.photo_saved"), true);
        sendState(player, number, phone, false);
    }

    private static boolean giveToInventory(ServerPlayerEntity player, ItemStack stack) {
        PlayerInventory inventory = player.getInventory();
        int slot = -1;
        for (int i = 0; i < inventory.main.size() && slot < 0; i++) {
            if (inventory.main.get(i).isEmpty()) {
                slot = i;
            }
        }
        if (slot < 0) {
            player.dropItem(stack, false);
            return false;
        }
        inventory.setStack(slot, stack);
        return true;
    }

    public static void openResize(ServerPlayerEntity player, PhotoFrameEntity frame) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(frame.getId());
        ServerPlayNetworking.send(player, PhoneNetwork.PHOTO_RESIZE_OPEN, buf);
    }

    public static void viewPrint(ServerPlayerEntity player, UUID photo) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(photo);
        ServerPlayNetworking.send(player, PhoneNetwork.PRINT_VIEW, buf);
    }

    private static void sendPrint(ServerPlayerEntity player, UUID photo, boolean full) {
        boolean allowed = carriesPrint(player, photo);
        if (!allowed) {
            Box area = player.getBoundingBox().expand(PRINT_VIEW_DISTANCE);
            ServerWorld world = player.getServerWorld();
            allowed = !world.getEntitiesByClass(PhotoFrameEntity.class, area, frame -> photo.equals(frame.photo())).isEmpty()
                    || !world.getEntitiesByClass(ItemEntity.class, area, item -> photo.equals(PhotoItem.photoOf(item.getStack()))).isEmpty()
                    || world.getPlayers().stream().anyMatch(other -> other.getBoundingBox().intersects(area) && carriesPrint(other, photo));
        }
        if (allowed) {
            sendImage(player, photo, full);
        }
    }

    private static boolean carriesPrint(ServerPlayerEntity player, UUID photo) {
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            if (photo.equals(PhotoItem.photoOf(inventory.getStack(i)))) {
                return true;
            }
        }
        return false;
    }

    private static void sendImage(ServerPlayerEntity player, UUID photo, boolean full) {
        PhotoStore.Image image = PhotoStore.read(player.getServer(), photo, full);
        if (image != null) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeUuid(photo);
            buf.writeBoolean(full);
            buf.writeBoolean(image.jpeg());
            buf.writeByteArray(image.data());
            ServerPlayNetworking.send(player, PhoneNetwork.PHOTO, buf);
        }
    }

    private static void setPortrait(ServerPlayerEntity player, Phone phone, @Nullable UUID person, @Nullable UUID photo, long time) {
        if (person == null || photo == null || !cooldown(LAST_PRINT, player, time, PRINT_COOLDOWN)
                || FamilyTree.get(player.getServerWorld()).getOrEmpty(person).isEmpty()) {
            return;
        }
        MinecraftServer server = player.getServer();
        PortraitRegistry portraits = PortraitRegistry.get(server);
        UUID image = null;
        if (!Util.NIL_UUID.equals(photo)) {
            if (!phone.references(photo)) {
                return;
            }
            image = UUID.randomUUID();
            if (!PhotoStore.writePortrait(server, photo, image)) {
                error(player, "msg.mca_vie.phone.portrait_failed");
                return;
            }
        } else if (portraits.get(person) == null) {
            return;
        }
        UUID old = portraits.set(person, image);
        if (old != null) {
            PhotoStore.delete(server, old);
        }
        player.sendMessage(Text.translatable(image == null ? "msg.mca_vie.phone.portrait_removed" : "msg.mca_vie.phone.portrait_set"), true);
    }

    private static void release(MinecraftServer server, PhoneRegistry registry, UUID photo) {
        if (!registry.referenced(photo)) {
            PhotoStore.delete(server, photo);
        }
    }

    private static boolean updateOwner(Phone phone, ServerPlayerEntity player) {
        String name = player.getGameProfile().getName();
        String skin = player.getGameProfile().getProperties().get("textures").stream().findFirst().map(Property::getValue).orElse("");
        if (name.equals(phone.ownerName) && skin.equals(phone.ownerSkin)) {
            return false;
        }
        phone.ownerName = name;
        phone.ownerSkin = skin;
        return true;
    }

    private static String displayName(PhoneRegistry registry, int number) {
        Phone phone = registry.get(number);
        return phone == null || phone.ownerName.isEmpty() ? PhoneRegistry.format(number) : phone.ownerName;
    }

    private static boolean canAccess(ServerPlayerEntity player, int number, Phone phone) {
        return phone.code == null || player.getUuid().equals(phone.owner) || Integer.valueOf(number).equals(UNLOCKED.get(player.getUuid()));
    }

    private static boolean holds(ServerPlayerEntity player, int number) {
        for (Hand hand : Hand.values()) {
            if (PhoneItem.numberOf(player.getStackInHand(hand)) == number) {
                return true;
            }
        }
        return false;
    }

    private static boolean carries(ServerPlayerEntity player, int number) {
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            if (PhoneItem.numberOf(inventory.getStack(i)) == number) {
                return true;
            }
        }
        return false;
    }

    private static boolean cooldown(Map<UUID, Long> last, ServerPlayerEntity player, long time, long delay) {
        Long previous = last.get(player.getUuid());
        if (previous != null && time - previous >= 0 && time - previous < delay) {
            return false;
        }
        last.put(player.getUuid(), time);
        return true;
    }

    private static String folderName(String text) {
        String name = clean(text);
        return name.length() > PhoneNetwork.MAX_FOLDER_NAME ? name.substring(0, PhoneNetwork.MAX_FOLDER_NAME).trim() : name;
    }

    private static String clean(String text) {
        return SharedConstants.stripInvalidChars(text).trim();
    }

    private static void error(ServerPlayerEntity player, String key) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeText(Text.translatable(key));
        ServerPlayNetworking.send(player, PhoneNetwork.ERROR, buf);
    }

    private static void sendState(ServerPlayerEntity player, int number, Phone phone, boolean open) {
        boolean access = canAccess(player, number, phone);
        PhoneRegistry registry = PhoneRegistry.get(player.getServer());
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(open);
        buf.writeVarInt(number);
        buf.writeBoolean(access);
        if (access) {
            buf.writeBoolean(player.getUuid().equals(phone.owner));
            buf.writeBoolean(phone.code != null);
            buf.writeVarInt(phone.contacts.size());
            phone.contacts.forEach(buf::writeVarInt);
            Set<Integer> known = new LinkedHashSet<>(phone.contacts);
            known.addAll(phone.conversations.keySet());
            buf.writeVarInt(known.size());
            for (int other : known) {
                Phone target = registry.get(other);
                buf.writeVarInt(other);
                buf.writeString(target == null ? "" : target.ownerName);
                buf.writeBoolean(target != null && target.owner != null);
                if (target != null && target.owner != null) {
                    buf.writeUuid(target.owner);
                }
                buf.writeString(target == null ? "" : target.ownerSkin);
            }
            List<Map.Entry<Integer, Conversation>> conversations = new ArrayList<>(phone.conversations.entrySet());
            conversations.removeIf(e -> e.getValue().messages.isEmpty());
            conversations.sort(Comparator.comparingLong((Map.Entry<Integer, Conversation> e) -> e.getValue().lastTime).reversed());
            buf.writeVarInt(conversations.size());
            for (Map.Entry<Integer, Conversation> entry : conversations) {
                Conversation conversation = entry.getValue();
                Message last = conversation.messages.get(conversation.messages.size() - 1);
                String preview = last.text().length() > PhoneNetwork.PREVIEW ? last.text().substring(0, PhoneNetwork.PREVIEW) + "…" : last.text();
                buf.writeVarInt(entry.getKey());
                buf.writeVarInt(conversation.unread);
                buf.writeBoolean(last.outgoing());
                buf.writeBoolean(last.photo() != null);
                buf.writeString(preview);
                buf.writeVarLong(conversation.lastTime);
            }
            buf.writeVarInt(phone.folders.size());
            phone.folders.forEach((id, name) -> {
                buf.writeVarInt(id);
                buf.writeString(name);
            });
            buf.writeVarInt(phone.gallery.size());
            for (Photo photo : phone.gallery) {
                buf.writeUuid(photo.id());
                buf.writeVarLong(photo.time());
                buf.writeVarInt(photo.folder());
            }
        }
        ServerPlayNetworking.send(player, PhoneNetwork.STATE, buf);
    }

    private static void sendConversation(ServerPlayerEntity player, int number, Phone phone, int other) {
        Conversation conversation = phone.conversations.get(other);
        List<Message> messages = conversation == null ? List.of() : conversation.messages;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(number);
        buf.writeVarInt(other);
        buf.writeVarInt(messages.size());
        for (Message message : messages) {
            buf.writeBoolean(message.outgoing());
            buf.writeString(message.text());
            buf.writeVarLong(message.time());
            buf.writeBoolean(message.photo() != null);
            if (message.photo() != null) {
                buf.writeUuid(message.photo());
            }
        }
        ServerPlayNetworking.send(player, PhoneNetwork.CONVERSATION, buf);
    }
}
