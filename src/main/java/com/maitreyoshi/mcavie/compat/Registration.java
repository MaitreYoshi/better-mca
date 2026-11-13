package com.maitreyoshi.mcavie.compat;

import com.google.common.base.Suppliers;
import com.maitreyoshi.mcavie.MCAVie;
import dev.architectury.registry.registries.RegistrarManager;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

public final class Registration {
    private static final com.google.common.base.Supplier<RegistrarManager> MANAGER = Suppliers.memoize(() -> RegistrarManager.get(MCAVie.MOD_ID));

    private Registration() {
    }

    public static <T, R extends T> void register(ResourceKey<Registry<T>> registry, String name, Supplier<R> factory, Consumer<R> onRegistered) {
        MANAGER.get().get(registry).register(MCAVie.id(name), () -> {
            R value = factory.get();
            onRegistered.accept(value);
            return value;
        });
    }
}
