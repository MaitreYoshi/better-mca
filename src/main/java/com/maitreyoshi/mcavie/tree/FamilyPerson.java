package com.maitreyoshi.mcavie.tree;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record FamilyPerson(
        UUID id,
        String name,
        String surname,
        int gender,
        boolean player,
        boolean deceased,
        UUID father,
        UUID mother,
        List<UUID> spouses,
        List<UUID> children,
        String professionKey,
        @Nullable Long birthDay,
        @Nullable Long deathDay,
        int happiness,
        @Nullable UUID portrait,
        @Nullable RelationInfo relationToViewer,
        List<Long> pastLives,
        @Nullable Long exactBirth,
        @Nullable Long exactDeath,
        // conjoint actuel, ou défunt conjoint tant que le survivant ne s'est pas remarié ; NIL sinon
        UUID spouse,
        // anciens conjoints dont on a divorcé
        List<UUID> divorced,
        // anciens mariages terminés sans divorce (veuvage suivi d'un remariage)
        List<UUID> formerSpouses
) {
    public FamilyPerson(UUID id, String name, String surname, int gender, boolean player, boolean deceased, UUID father, UUID mother,
                        List<UUID> spouses, List<UUID> children, String professionKey, @Nullable Long birthDay, @Nullable Long deathDay,
                        int happiness, @Nullable UUID portrait, @Nullable RelationInfo relationToViewer) {
        this(id, name, surname, gender, player, deceased, father, mother, spouses, children, professionKey, birthDay, deathDay,
                happiness, portrait, relationToViewer, List.of(), null, null, Util.NIL_UUID, List.of(), List.of());
    }

    public boolean hasFather() {
        return !Util.NIL_UUID.equals(father);
    }

    public boolean hasMother() {
        return !Util.NIL_UUID.equals(mother);
    }

    public void write(PacketByteBuf buf) {
        buf.writeUuid(id);
        buf.writeString(name);
        buf.writeString(surname);
        buf.writeVarInt(gender);
        buf.writeBoolean(player);
        buf.writeBoolean(deceased);
        buf.writeUuid(father);
        buf.writeUuid(mother);
        writeUuids(buf, spouses);
        writeUuids(buf, children);
        buf.writeString(professionKey);
        writeNullableLong(buf, birthDay);
        writeNullableLong(buf, deathDay);
        buf.writeVarInt(happiness + 1);
        buf.writeBoolean(portrait != null);
        if (portrait != null) {
            buf.writeUuid(portrait);
        }
        buf.writeBoolean(relationToViewer != null);
        if (relationToViewer != null) {
            relationToViewer.write(buf);
        }
        buf.writeVarInt(pastLives.size());
        pastLives.forEach(buf::writeLong);
        writeNullableLong(buf, exactBirth);
        writeNullableLong(buf, exactDeath);
        buf.writeUuid(spouse);
        writeUuids(buf, divorced);
        writeUuids(buf, formerSpouses);
    }

    public static FamilyPerson read(PacketByteBuf buf) {
        return new FamilyPerson(
                buf.readUuid(),
                buf.readString(),
                buf.readString(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readUuid(),
                buf.readUuid(),
                readUuids(buf),
                readUuids(buf),
                buf.readString(),
                readNullableLong(buf),
                readNullableLong(buf),
                buf.readVarInt() - 1,
                buf.readBoolean() ? buf.readUuid() : null,
                buf.readBoolean() ? RelationInfo.read(buf) : null,
                readLongs(buf),
                readNullableLong(buf),
                readNullableLong(buf),
                buf.readUuid(),
                readUuids(buf),
                readUuids(buf)
        );
    }

    private static List<Long> readLongs(PacketByteBuf buf) {
        int size = buf.readVarInt();
        List<Long> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(buf.readLong());
        }
        return list;
    }

    static void writeUuids(PacketByteBuf buf, List<UUID> uuids) {
        buf.writeVarInt(uuids.size());
        uuids.forEach(buf::writeUuid);
    }

    static List<UUID> readUuids(PacketByteBuf buf) {
        int size = buf.readVarInt();
        List<UUID> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(buf.readUuid());
        }
        return list;
    }

    private static void writeNullableLong(PacketByteBuf buf, @Nullable Long value) {
        buf.writeBoolean(value != null);
        if (value != null) {
            buf.writeLong(value);
        }
    }

    @Nullable
    private static Long readNullableLong(PacketByteBuf buf) {
        return buf.readBoolean() ? buf.readLong() : null;
    }
}
