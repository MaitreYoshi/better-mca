package com.maitreyoshi.mcavie.compat;

import net.minecraft.core.UUIDUtil;
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
}
