package com.maitreyoshi.mcavie.phone;

import com.maitreyoshi.mcavie.compat.Saved;
import com.maitreyoshi.mcavie.compat.Nbt;
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
        return Saved.get(server.overworld(), PhoneRegistry::fromNbt, PhoneRegistry::new, PhoneRegistry::save, DATA_ID);
    }

    private static PhoneRegistry fromNbt(CompoundTag nbt) {
        PhoneRegistry registry = new PhoneRegistry();
        CompoundTag all = nbt.getCompoundOrEmpty("phones");
        for (String key : all.keySet()) {
            try {
                int number = Integer.parseInt(key);
                if (number >= 0 && number < NUMBERS) {
                    registry.phones.put(number, Phone.fromNbt(all.getCompoundOrEmpty(key)));
                }
            } catch (IllegalArgumentException e) {
                MCAVie.LOGGER.warn("Téléphone invalide dans le registre : {}", key);
            }
        }
        return registry;
    }

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
                Nbt.putUUID(nbt, "owner", owner);
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
                Nbt.putUUID(entry, "id", photo.id());
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
            if (Nbt.hasUUID(nbt, "owner")) {
                phone.owner = Nbt.getUUID(nbt, "owner");
            }
            phone.ownerName = nbt.getStringOr("ownerName", "");
            phone.ownerSkin = nbt.getStringOr("ownerSkin", "");
            if (nbt.contains("code")) {
                phone.code = nbt.getStringOr("code", "");
            }
            for (Tag element : nbt.getListOrEmpty("contacts")) {
                CompoundTag contact = (CompoundTag) element;
                phone.contacts.add(contact.getIntOr("number", 0));
            }
            for (Tag element : nbt.getListOrEmpty("conversations")) {
                CompoundTag entry = (CompoundTag) element;
                phone.conversations.put(entry.getIntOr("number", 0), Conversation.fromNbt(entry));
            }
            for (Tag element : nbt.getListOrEmpty("folders")) {
                CompoundTag entry = (CompoundTag) element;
                phone.folders.put(entry.getIntOr("id", 0), entry.getStringOr("name", ""));
            }
            phone.nextFolder = Math.max(1, nbt.getIntOr("nextFolder", 0));
            phone.wallpaper = nbt.getStringOr("wallpaper", "");
            for (Tag element : nbt.getListOrEmpty("agenda")) {
                CompoundTag entry = (CompoundTag) element;
                phone.agenda.add(new AgendaEvent(entry.getIntOr("id", 0), entry.getLongOr("day", 0L), entry.getBooleanOr("yearly", false), entry.getStringOr("title", "")));
            }
            phone.nextEvent = Math.max(1, nbt.getIntOr("nextEvent", 0));
            phone.agendaDay = nbt.contains("agendaDay") ? nbt.getLongOr("agendaDay", 0L) : -1;
            for (Tag element : nbt.getListOrEmpty("gallery")) {
                CompoundTag entry = (CompoundTag) element;
                if (Nbt.hasUUID(entry, "id")) {
                    int folder = entry.getIntOr("folder", 0);
                    phone.gallery.add(new Photo(Nbt.getUUID(entry, "id"), entry.getLongOr("time", 0L), phone.folders.containsKey(folder) ? folder : 0));
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
                    Nbt.putUUID(entry, "photo", message.photo());
                }
                list.add(entry);
            }
            nbt.put("messages", list);
            return nbt;
        }

        static Conversation fromNbt(CompoundTag nbt) {
            Conversation conversation = new Conversation();
            conversation.unread = nbt.getIntOr("unread", 0);
            conversation.lastTime = nbt.getLongOr("lastTime", 0L);
            for (Tag element : nbt.getListOrEmpty("messages")) {
                CompoundTag entry = (CompoundTag) element;
                conversation.messages.add(new Message(entry.getBooleanOr("out", false), entry.getStringOr("text", ""), entry.getLongOr("time", 0L), Nbt.hasUUID(entry, "photo") ? Nbt.getUUID(entry, "photo") : null));
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
