package com.maitreyoshi.mcavie.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;

import java.util.UUID;
import java.util.function.Consumer;

/** Aides NBT 26.x : UUID stockés comme avant (tableau de 4 entiers) et données perso des objets (composant custom_data). */
public final class Nbt {
    private Nbt() {
    }

    public static UUID getUUID(CompoundTag tag, String key) {
        return tag.read(key, UUIDUtil.CODEC).orElse(null);
    }

    public static boolean hasUUID(CompoundTag tag, String key) {
        return tag.read(key, UUIDUtil.CODEC).isPresent();
    }

    public static void putUUID(CompoundTag tag, String key, UUID uuid) {
        tag.store(key, UUIDUtil.CODEC, uuid);
    }

    /** Copie des données perso de l'objet, ou null s'il n'en a pas (ancien hasTag/getTag). */
    public static CompoundTag tag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    public static boolean hasTag(ItemStack stack) {
        return stack.has(DataComponents.CUSTOM_DATA);
    }

    /** Modifie les données perso de l'objet (ancien getOrCreateTag puis écriture). */
    public static void update(ItemStack stack, Consumer<CompoundTag> editor) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, editor);
    }

    /** Position au format 1.20 (compound X/Y/Z), lisible par les anciennes sauvegardes. */
    public static CompoundTag pos(BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", pos.getX());
        tag.putInt("Y", pos.getY());
        tag.putInt("Z", pos.getZ());
        return tag;
    }

    public static BlockPos readPos(CompoundTag tag) {
        return new BlockPos(tag.getIntOr("X", 0), tag.getIntOr("Y", 0), tag.getIntOr("Z", 0));
    }

    public static Tag uuidTag(UUID uuid) {
        return UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE, uuid).getOrThrow();
    }

    public static UUID uuidOf(Tag tag) {
        return UUIDUtil.CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(null);
    }
}
