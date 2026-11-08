package com.maitreyoshi.mcavie.phone.client;

import com.maitreyoshi.mcavie.phone.PhoneNetwork;
import com.maitreyoshi.mcavie.phone.PhoneServer;
import com.maitreyoshi.mcavie.phone.PhotoFrameEntity;
import com.maitreyoshi.mcavie.phone.PhotoItem;
import net.minecraft.client.MinecraftClient;
import com.maitreyoshi.mcavie.compat.client.ClientTickEvents;
import com.maitreyoshi.mcavie.compat.client.BuiltinItemRendererRegistry;
import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import com.maitreyoshi.mcavie.compat.client.ClientPlayConnectionEvents;
import com.maitreyoshi.mcavie.compat.client.ClientPlayNetworking;
import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class PhoneClient {
    public record Summary(int number, int unread, boolean outgoing, boolean photo, String preview, long time) {
    }

    public record Message(boolean outgoing, String text, long time, @Nullable UUID photo) {
    }

    public record Photo(UUID id, long time, int folder) {
    }

    public record Person(String name, @Nullable UUID uuid, String skin) {
    }

    public record Event(int id, long day, boolean yearly, String title) {
    }

    public record State(int number, boolean access, boolean owner, boolean hasCode, List<Integer> contacts, Map<Integer, Person> people, List<Summary> conversations, List<Photo> gallery, Map<Integer, String> folders, String wallpaper, List<Event> agenda) {
        public String nameOf(int other) {
            Person person = people.get(other);
            return person == null || person.name().isEmpty() ? String.format("%03d", other) : person.name();
        }

        @Nullable
        public Person person(int other) {
            return people.get(other);
        }

        public int unread() {
            return conversations.stream().mapToInt(Summary::unread).sum();
        }
    }

    private PhoneClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(PhoneNetwork.STATE, (client, handler, buf, responseSender) -> {
            boolean open = buf.readBoolean();
            int number = buf.readVarInt();
            boolean access = buf.readBoolean();
            boolean owner = false;
            boolean hasCode = false;
            List<Integer> contacts = new ArrayList<>();
            Map<Integer, Person> people = new HashMap<>();
            List<Summary> conversations = new ArrayList<>();
            List<Photo> gallery = new ArrayList<>();
            Map<Integer, String> folders = new LinkedHashMap<>();
            String wallpaper = "";
            List<Event> agenda = new ArrayList<>();
            if (access) {
                owner = buf.readBoolean();
                hasCode = buf.readBoolean();
                int count = buf.readVarInt();
                for (int i = 0; i < count; i++) {
                    contacts.add(buf.readVarInt());
                }
                count = buf.readVarInt();
                for (int i = 0; i < count; i++) {
                    int other = buf.readVarInt();
                    String name = buf.readString();
                    UUID uuid = buf.readBoolean() ? buf.readUuid() : null;
                    people.put(other, new Person(name, uuid, buf.readString()));
                }
                count = buf.readVarInt();
                for (int i = 0; i < count; i++) {
                    conversations.add(new Summary(buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readString(), buf.readVarLong()));
                }
                count = buf.readVarInt();
                for (int i = 0; i < count; i++) {
                    folders.put(buf.readVarInt(), buf.readString());
                }
                count = buf.readVarInt();
                for (int i = 0; i < count; i++) {
                    gallery.add(new Photo(buf.readUuid(), buf.readVarLong(), buf.readVarInt()));
                }
                wallpaper = buf.readString();
                count = buf.readVarInt();
                for (int i = 0; i < count; i++) {
                    agenda.add(new Event(buf.readVarInt(), buf.readVarLong(), buf.readBoolean(), buf.readString()));
                }
            }
            State state = new State(number, access, owner, hasCode, List.copyOf(contacts), Map.copyOf(people), List.copyOf(conversations), List.copyOf(gallery), Collections.unmodifiableMap(folders), wallpaper, List.copyOf(agenda));
            client.execute(() -> {
                CameraMode.update(state);
                if (client.currentScreen instanceof PhoneScreen screen && screen.number() == number) {
                    screen.applyState(state);
                } else if (open) {
                    CameraMode.stop(false);
                    client.setScreen(new PhoneScreen(state));
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(com.maitreyoshi.mcavie.phone.PhoneEmployees.LIST, (client, handler, buf, responseSender) -> {
            int count = buf.readVarInt();
            List<com.maitreyoshi.mcavie.phone.PhoneEmployees.Employee> list = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                list.add(com.maitreyoshi.mcavie.phone.PhoneEmployees.Employee.read(buf));
            }
            client.execute(() -> {
                if (client.currentScreen instanceof PhoneScreen screen) {
                    screen.applyEmployees(list);
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(com.maitreyoshi.mcavie.phone.EmployeeChat.LOG, (client, handler, buf, responseSender) -> {
            java.util.UUID uuid = buf.readUuid();
            List<com.maitreyoshi.mcavie.phone.EmployeeChat.Line> lines = com.maitreyoshi.mcavie.phone.EmployeeChat.readLog(buf);
            client.execute(() -> {
                if (client.currentScreen instanceof PhoneScreen screen) {
                    screen.applyEmployeeChat(uuid, lines);
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(PhoneNetwork.CONVERSATION, (client, handler, buf, responseSender) -> {
            int number = buf.readVarInt();
            int other = buf.readVarInt();
            int count = buf.readVarInt();
            List<Message> messages = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                messages.add(new Message(buf.readBoolean(), buf.readString(), buf.readVarLong(), buf.readBoolean() ? buf.readUuid() : null));
            }
            client.execute(() -> {
                if (client.currentScreen instanceof PhoneScreen screen && screen.number() == number) {
                    screen.applyConversation(other, List.copyOf(messages));
                }
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            PhoneSkins.clear();
            PhonePhotos.clear();
            CameraMode.stop(false);
        });
        ClientPlayNetworking.registerGlobalReceiver(PhoneNetwork.PHOTO, (client, handler, buf, responseSender) -> {
            UUID id = buf.readUuid();
            boolean full = buf.readBoolean();
            boolean jpeg = buf.readBoolean();
            byte[] data = buf.readByteArray(PhoneNetwork.MAX_FULL_BYTES);
            client.execute(() -> PhonePhotos.store(id, full, jpeg, data));
        });
        ClientPlayNetworking.registerGlobalReceiver(PhoneNetwork.NOTIFY, (client, handler, buf, responseSender) -> {
            int kind = buf.readVarInt();
            client.execute(() -> PhoneSettings.notify(kind == PhoneNetwork.NOTIFY_REMINDER ? PhoneSettings.KIND_REMINDER : PhoneSettings.KIND_MESSAGE));
        });
        CameraMode.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> PhonePhotos.tick());
        EntityRendererRegistry.register(() -> PhoneServer.PHOTO_FRAME, PhotoFrameRenderer::new);
        BuiltinItemRendererRegistry.INSTANCE.register(PhoneServer.PHOTO, new PhotoItemRenderer());
        ClientPlayNetworking.registerGlobalReceiver(PhoneNetwork.PRINT_VIEW, (client, handler, buf, responseSender) -> {
            UUID id = buf.readUuid();
            client.execute(() -> {
                if (client.currentScreen == null) {
                    client.setScreen(PhotoViewerScreen.print(id));
                }
            });
        });
        PhotoItem.placer = (clicked, side, hand, photo, top) -> MinecraftClient.getInstance().setScreen(PhotoSizeScreen.place(clicked, side, hand, photo, top));
        ClientPlayNetworking.registerGlobalReceiver(PhoneNetwork.PHOTO_RESIZE_OPEN, (client, handler, buf, responseSender) -> {
            int id = buf.readVarInt();
            client.execute(() -> {
                if (client.currentScreen == null && client.world != null && client.world.getEntityById(id) instanceof PhotoFrameEntity frame) {
                    client.setScreen(PhotoSizeScreen.resize(frame));
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(PhoneNetwork.PRINTED, (client, handler, buf, responseSender) -> client.execute(() -> {
            if (client.currentScreen instanceof PhotoViewerScreen || client.currentScreen instanceof PhoneScreen) {
                client.setScreen(null);
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(PhoneNetwork.ERROR, (client, handler, buf, responseSender) -> {
            Text text = buf.readText();
            client.execute(() -> {
                if (client.currentScreen instanceof PhoneScreen screen) {
                    screen.showError(text);
                }
            });
        });
    }

    public static void send(int number, int op, Consumer<PacketByteBuf> body) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(number);
        buf.writeVarInt(op);
        body.accept(buf);
        ClientPlayNetworking.send(PhoneNetwork.ACTION, buf);
    }
}
