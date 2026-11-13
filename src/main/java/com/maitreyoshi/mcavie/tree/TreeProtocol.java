package com.maitreyoshi.mcavie.tree;

import com.maitreyoshi.mcavie.MCAVie;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.util.Util;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

public final class TreeProtocol {
    public static final Identifier REQUEST = MCAVie.id("tree_request");
    public static final Identifier RESPONSE = MCAVie.id("tree_response");

    public static final int MODE_TREE = 0;
    public static final int MODE_PATH = 1;
    public static final int MODE_SEARCH = 2;
    public static final int MODE_LINEAGE = 3;

    public static final int ALL_GENERATIONS = -1;

    private TreeProtocol() {
    }

    public record Request(int mode, UUID root, UUID other, int up, int down, String text) {
        public static Request tree(UUID root, int up, int down) {
            return new Request(MODE_TREE, root, Util.NIL_UUID, up, down, "");
        }

        public static Request path(UUID a, UUID b, int index) {
            return new Request(MODE_PATH, a, b, index, 0, "");
        }

        public static Request search(String text) {
            return new Request(MODE_SEARCH, Util.NIL_UUID, Util.NIL_UUID, 0, 0, text);
        }

        public static Request lineage(String surname) {
            return new Request(MODE_LINEAGE, Util.NIL_UUID, Util.NIL_UUID, 0, 0, surname);
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(mode);
            buf.writeUUID(root);
            buf.writeUUID(other);
            buf.writeVarInt(up + 1);
            buf.writeVarInt(down + 1);
            buf.writeUtf(text, 64);
        }

        public static Request read(FriendlyByteBuf buf) {
            return new Request(buf.readVarInt(), buf.readUUID(), buf.readUUID(), buf.readVarInt() - 1, buf.readVarInt() - 1, buf.readUtf(64));
        }
    }

    public record Response(int mode, UUID root, List<FamilyPerson> persons, List<UUID> path, @Nullable RelationInfo relation, UUID viewer, String text,
                           int index, int count) {
        public Response(int mode, UUID root, List<FamilyPerson> persons, List<UUID> path, @Nullable RelationInfo relation, UUID viewer, String text) {
            this(mode, root, persons, path, relation, viewer, text, 0, 0);
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(mode);
            buf.writeUUID(root);
            buf.writeVarInt(persons.size());
            persons.forEach(p -> p.write(buf));
            FamilyPerson.writeUuids(buf, path);
            buf.writeBoolean(relation != null);
            if (relation != null) {
                relation.write(buf);
            }
            buf.writeUUID(viewer);
            buf.writeUtf(text, 64);
            buf.writeVarInt(index);
            buf.writeVarInt(count);
        }

        public static Response read(FriendlyByteBuf buf) {
            int mode = buf.readVarInt();
            UUID root = buf.readUUID();
            int size = buf.readVarInt();
            List<FamilyPerson> persons = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                persons.add(FamilyPerson.read(buf));
            }
            List<UUID> path = FamilyPerson.readUuids(buf);
            RelationInfo relation = buf.readBoolean() ? RelationInfo.read(buf) : null;
            UUID viewer = buf.readUUID();
            String text = buf.readUtf(64);
            return new Response(mode, root, persons, path, relation, viewer, text, buf.readVarInt(), buf.readVarInt());
        }
    }
}
