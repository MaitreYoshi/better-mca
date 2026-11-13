package com.maitreyoshi.mcavie.compat;

import com.maitreyoshi.mcavie.MCAVie;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Données de monde 26.x (SavedDataType + codec) en gardant nos méthodes fromNbt/save.
 * Lit aussi l'ancien fichier data/&lt;id&gt;.dat d'un monde venant de la 1.20.1.
 */
public final class Saved {
    private static final Map<String, SavedDataType<?>> TYPES = new ConcurrentHashMap<>();

    private Saved() {
    }

    @SuppressWarnings("unchecked")
    public static <T extends SavedData> T get(ServerLevel level, Function<CompoundTag, T> load, Supplier<T> create,
                                              BiFunction<T, CompoundTag, CompoundTag> save, String id) {
        SavedDataType<T> type = (SavedDataType<T>) TYPES.computeIfAbsent(id, k -> new SavedDataType<>(MCAVie.id(k), create,
                CompoundTag.CODEC.xmap(load, data -> save.apply(data, new CompoundTag())), (DataFixTypes) null));
        SavedDataStorage storage = level.getDataStorage();
        T data = storage.get(type);
        if (data == null) {
            data = legacy(storage, type, id);
            if (data != null) {
                storage.set(type, data);
                data.setDirty();
            } else {
                data = storage.computeIfAbsent(type);
            }
        }
        return data;
    }

    private static <T extends SavedData> T legacy(SavedDataStorage storage, SavedDataType<T> type, String id) {
        try {
            Field field = SavedDataStorage.class.getDeclaredField("dataFolder");
            field.setAccessible(true);
            Path folder = ((Path) field.get(storage)).toAbsolutePath().normalize();
            Path file = folder.resolve(id + ".dat").normalize();
            if (!file.startsWith(folder) || !Files.exists(file)) {
                return null;
            }
            CompoundTag tag = storage.readTagFromDisk(file, null, SharedConstants.getCurrentVersion().dataVersion().version());
            return type.codec().parse(NbtOps.INSTANCE, tag.getCompoundOrEmpty("data")).result().orElse(null);
        } catch (Exception e) {
            MCAVie.LOGGER.warn("Ancienne donnée {} illisible : {}", id, e.toString());
            return null;
        }
    }
}
