package com.maitreyoshi.mcavie.phone;

import com.maitreyoshi.mcavie.MCAVie;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PhoneRegistry extends SavedData {
    private static final String DATA_ID = "mca_vie_phones";
    public static final int NUMBERS = 1000;

    private final Map<Integer, Phone> phones = new HashMap<>();

    public static PhoneRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PhoneRegistry::fromNbt, PhoneRegistry::new, DATA_ID);
    }

    private static PhoneRegistry fromNbt(CompoundTag nbt) {
        PhoneRegistry registry = new PhoneRegistry();
        CompoundTag all = nbt.getCompound("phones");
        for (String key : all.getAllKeys()) {
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
    public CompoundTag save(CompoundTag nbt) {
        CompoundTag all = new CompoundTag();
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

    public int allocate(RandomSource random) {
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
        setDirty();
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
        public final Map<Integer, String> folders = new LinkedHashMap<>();
        public int nextFolder = 1;
        public String wallpaper = "";
        public final List<AgendaEvent> agenda = new ArrayList<>();
        public int nextEvent = 1;
        public long agendaDay = -1;

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

        CompoundTag toNbt() {
            CompoundTag nbt = new CompoundTag();
            if (owner != null) {
                nbt.putUUID("owner", owner);
            }
            nbt.putString("ownerName", ownerName);
            nbt.putString("ownerSkin", ownerSkin);
            if (code != null) {
                nbt.putString("code", code);
            }
            ListTag contactList = new ListTag();
            for (int number : contacts) {
                CompoundTag contact = new CompoundTag();
                contact.putInt("number", number);
                contactList.add(contact);
            }
            nbt.put("contacts", contactList);
            ListTag conversationList = new ListTag();
            conversations.forEach((number, conversation) -> {
                CompoundTag entry = conversation.toNbt();
                entry.putInt("number", number);
                conversationList.add(entry);
            });
            nbt.put("conversations", conversationList);
            ListTag galleryList = new ListTag();
            for (Photo photo : gallery) {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("id", photo.id());
                entry.putLong("time", photo.time());
                if (photo.folder() != 0) {
                    entry.putInt("folder", photo.folder());
                }
                galleryList.add(entry);
            }
            nbt.put("gallery", galleryList);
            ListTag folderList = new ListTag();
            folders.forEach((id, name) -> {
                CompoundTag entry = new CompoundTag();
                entry.putInt("id", id);
                entry.putString("name", name);
                folderList.add(entry);
            });
            nbt.put("folders", folderList);
            nbt.putInt("nextFolder", nextFolder);
            if (!wallpaper.isEmpty()) {
                nbt.putString("wallpaper", wallpaper);
            }
            ListTag agendaList = new ListTag();
            for (AgendaEvent event : agenda) {
                CompoundTag entry = new CompoundTag();
                entry.putInt("id", event.id());
                entry.putLong("day", event.day());
                entry.putBoolean("yearly", event.yearly());
                entry.putString("title", event.title());
                agendaList.add(entry);
            }
            nbt.put("agenda", agendaList);
            nbt.putInt("nextEvent", nextEvent);
            nbt.putLong("agendaDay", agendaDay);
            return nbt;
        }

        static Phone fromNbt(CompoundTag nbt) {
            Phone phone = new Phone();
            if (nbt.hasUUID("owner")) {
                phone.owner = nbt.getUUID("owner");
            }
            phone.ownerName = nbt.getString("ownerName");
            phone.ownerSkin = nbt.getString("ownerSkin");
            if (nbt.contains("code")) {
                phone.code = nbt.getString("code");
            }
            for (Tag element : nbt.getList("contacts", Tag.TAG_COMPOUND)) {
                CompoundTag contact = (CompoundTag) element;
                phone.contacts.add(contact.getInt("number"));
            }
            for (Tag element : nbt.getList("conversations", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) element;
                phone.conversations.put(entry.getInt("number"), Conversation.fromNbt(entry));
            }
            for (Tag element : nbt.getList("folders", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) element;
                phone.folders.put(entry.getInt("id"), entry.getString("name"));
            }
            phone.nextFolder = Math.max(1, nbt.getInt("nextFolder"));
            phone.wallpaper = nbt.getString("wallpaper");
            for (Tag element : nbt.getList("agenda", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) element;
                phone.agenda.add(new AgendaEvent(entry.getInt("id"), entry.getLong("day"), entry.getBoolean("yearly"), entry.getString("title")));
            }
            phone.nextEvent = Math.max(1, nbt.getInt("nextEvent"));
            phone.agendaDay = nbt.contains("agendaDay") ? nbt.getLong("agendaDay") : -1;
            for (Tag element : nbt.getList("gallery", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) element;
                if (entry.hasUUID("id")) {
                    int folder = entry.getInt("folder");
                    phone.gallery.add(new Photo(entry.getUUID("id"), entry.getLong("time"), phone.folders.containsKey(folder) ? folder : 0));
                }
            }
            return phone;
        }
    }

    public static final class Conversation {
        public final List<Message> messages = new ArrayList<>();
        public int unread;
        public long lastTime;

        CompoundTag toNbt() {
            CompoundTag nbt = new CompoundTag();
            nbt.putInt("unread", unread);
            nbt.putLong("lastTime", lastTime);
            ListTag list = new ListTag();
            for (Message message : messages) {
                CompoundTag entry = new CompoundTag();
                entry.putBoolean("out", message.outgoing());
                entry.putString("text", message.text());
                entry.putLong("time", message.time());
                if (message.photo() != null) {
                    entry.putUUID("photo", message.photo());
                }
                list.add(entry);
            }
            nbt.put("messages", list);
            return nbt;
        }

        static Conversation fromNbt(CompoundTag nbt) {
            Conversation conversation = new Conversation();
            conversation.unread = nbt.getInt("unread");
            conversation.lastTime = nbt.getLong("lastTime");
            for (Tag element : nbt.getList("messages", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) element;
                conversation.messages.add(new Message(entry.getBoolean("out"), entry.getString("text"), entry.getLong("time"), entry.hasUUID("photo") ? entry.getUUID("photo") : null));
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

    public record AgendaEvent(int id, long day, boolean yearly, String title) {
    }

    public record Photo(UUID id, long time, int folder) {
        public Photo(UUID id, long time) {
            this(id, time, 0);
        }
    }
}
