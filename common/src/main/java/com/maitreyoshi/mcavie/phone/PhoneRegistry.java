package com.maitreyoshi.mcavie.phone;

import com.maitreyoshi.mcavie.MCAVie;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PhoneRegistry extends PersistentState {
    private static final String DATA_ID = "mca_vie_phones";
    public static final int NUMBERS = 1000;

    private final Map<Integer, Phone> phones = new HashMap<>();

    public static PhoneRegistry get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(PhoneRegistry::fromNbt, PhoneRegistry::new, DATA_ID);
    }

    private static PhoneRegistry fromNbt(NbtCompound nbt) {
        PhoneRegistry registry = new PhoneRegistry();
        NbtCompound all = nbt.getCompound("phones");
        for (String key : all.getKeys()) {
            try {
                int number = Integer.parseInt(key);
                if (number >= 0 && number < NUMBERS) {
                    registry.phones.put(number, Phone.fromNbt(all.getCompound(key)));
                }
            } catch (IllegalArgumentException e) {
                MCAVie.LOGGER.warn("Téléphone invalide dans le registre : {}", key);
            }
        }
        return registry;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound all = new NbtCompound();
        phones.forEach((number, phone) -> all.put(String.valueOf(number), phone.toNbt()));
        nbt.put("phones", all);
        return nbt;
    }

    @Nullable
    public Phone get(int number) {
        return phones.get(number);
    }

    public Map<Integer, Phone> all() {
        return phones;
    }

    public int allocate(Random random) {
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < NUMBERS; i++) {
            if (!phones.containsKey(i)) {
                free.add(i);
            }
        }
        if (free.isEmpty()) {
            return -1;
        }
        int number = free.get(random.nextInt(free.size()));
        phones.put(number, new Phone());
        markDirty();
        return number;
    }

    public static String format(int number) {
        return String.format("%03d", number);
    }

    public static final class Phone {
        @Nullable
        public UUID owner;
        public String ownerName = "";
        public String ownerSkin = "";
        @Nullable
        public String code;
        public final Set<Integer> contacts = new LinkedHashSet<>();
        public final Map<Integer, Conversation> conversations = new LinkedHashMap<>();
        public final List<Photo> gallery = new ArrayList<>();

        public boolean references(UUID photo) {
            for (Photo entry : gallery) {
                if (entry.id().equals(photo)) {
                    return true;
                }
            }
            for (Conversation conversation : conversations.values()) {
                for (Message message : conversation.messages) {
                    if (photo.equals(message.photo())) {
                        return true;
                    }
                }
            }
            return false;
        }

        public int unread() {
            int total = 0;
            for (Conversation conversation : conversations.values()) {
                total += conversation.unread;
            }
            return total;
        }

        NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            if (owner != null) {
                nbt.putUuid("owner", owner);
            }
            nbt.putString("ownerName", ownerName);
            nbt.putString("ownerSkin", ownerSkin);
            if (code != null) {
                nbt.putString("code", code);
            }
            NbtList contactList = new NbtList();
            for (int number : contacts) {
                NbtCompound contact = new NbtCompound();
                contact.putInt("number", number);
                contactList.add(contact);
            }
            nbt.put("contacts", contactList);
            NbtList conversationList = new NbtList();
            conversations.forEach((number, conversation) -> {
                NbtCompound entry = conversation.toNbt();
                entry.putInt("number", number);
                conversationList.add(entry);
            });
            nbt.put("conversations", conversationList);
            NbtList galleryList = new NbtList();
            for (Photo photo : gallery) {
                NbtCompound entry = new NbtCompound();
                entry.putUuid("id", photo.id());
                entry.putLong("time", photo.time());
                galleryList.add(entry);
            }
            nbt.put("gallery", galleryList);
            return nbt;
        }

        static Phone fromNbt(NbtCompound nbt) {
            Phone phone = new Phone();
            if (nbt.containsUuid("owner")) {
                phone.owner = nbt.getUuid("owner");
            }
            phone.ownerName = nbt.getString("ownerName");
            phone.ownerSkin = nbt.getString("ownerSkin");
            if (nbt.contains("code")) {
                phone.code = nbt.getString("code");
            }
            for (NbtElement element : nbt.getList("contacts", NbtElement.COMPOUND_TYPE)) {
                NbtCompound contact = (NbtCompound) element;
                phone.contacts.add(contact.getInt("number"));
            }
            for (NbtElement element : nbt.getList("conversations", NbtElement.COMPOUND_TYPE)) {
                NbtCompound entry = (NbtCompound) element;
                phone.conversations.put(entry.getInt("number"), Conversation.fromNbt(entry));
            }
            for (NbtElement element : nbt.getList("gallery", NbtElement.COMPOUND_TYPE)) {
                NbtCompound entry = (NbtCompound) element;
                if (entry.containsUuid("id")) {
                    phone.gallery.add(new Photo(entry.getUuid("id"), entry.getLong("time")));
                }
            }
            return phone;
        }
    }

    public static final class Conversation {
        public final List<Message> messages = new ArrayList<>();
        public int unread;
        public long lastTime;

        NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putInt("unread", unread);
            nbt.putLong("lastTime", lastTime);
            NbtList list = new NbtList();
            for (Message message : messages) {
                NbtCompound entry = new NbtCompound();
                entry.putBoolean("out", message.outgoing());
                entry.putString("text", message.text());
                entry.putLong("time", message.time());
                if (message.photo() != null) {
                    entry.putUuid("photo", message.photo());
                }
                list.add(entry);
            }
            nbt.put("messages", list);
            return nbt;
        }

        static Conversation fromNbt(NbtCompound nbt) {
            Conversation conversation = new Conversation();
            conversation.unread = nbt.getInt("unread");
            conversation.lastTime = nbt.getLong("lastTime");
            for (NbtElement element : nbt.getList("messages", NbtElement.COMPOUND_TYPE)) {
                NbtCompound entry = (NbtCompound) element;
                conversation.messages.add(new Message(entry.getBoolean("out"), entry.getString("text"), entry.getLong("time"), entry.containsUuid("photo") ? entry.getUuid("photo") : null));
            }
            return conversation;
        }
    }

    public boolean referenced(UUID photo) {
        for (Phone phone : phones.values()) {
            if (phone.references(photo)) {
                return true;
            }
        }
        return false;
    }

    public record Message(boolean outgoing, String text, long time, @Nullable UUID photo) {
    }

    public record Photo(UUID id, long time) {
    }
}
