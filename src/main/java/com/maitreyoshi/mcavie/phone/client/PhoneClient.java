package com.maitreyoshi.mcavie.phone.client;

import com.maitreyoshi.mcavie.phone.PhoneNetwork;
import com.maitreyoshi.mcavie.phone.PhoneServer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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

    public record Photo(UUID id, long time) {
    }

    public record Person(String name, @Nullable UUID uuid, String skin) {
    }

    public record State(int number, boolean access, boolean owner, boolean hasCode, List<Integer> contacts, Map<Integer, Person> people, List<Summary> conversations, List<Photo> gallery) {
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
                    gallery.add(new Photo(buf.readUuid(), buf.readVarLong()));
                }
            }
            State state = new State(number, access, owner, hasCode, List.copyOf(contacts), Map.copyOf(people), List.copyOf(conversations), List.copyOf(gallery));
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
        CameraMode.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> PhonePhotos.tick());
        EntityRendererRegistry.register(PhoneServer.PHOTO_FRAME, PhotoFrameRenderer::new);
        BuiltinItemRendererRegistry.INSTANCE.register(PhoneServer.PHOTO, new PhotoItemRenderer());
        ClientPlayNetworking.registerGlobalReceiver(PhoneNetwork.PRINT_VIEW, (client, handler, buf, responseSender) -> {
            UUID id = buf.readUuid();
            client.execute(() -> {
                if (client.currentScreen == null) {
                    client.setScreen(PhotoViewerScreen.print(id));
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
