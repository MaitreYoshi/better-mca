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

    /** Propriétés de bloc avec leur identifiant (obligatoire depuis 1.21.2). */
    public static net.minecraft.world.level.block.state.BlockBehaviour.Properties block(String name, net.minecraft.world.level.block.state.BlockBehaviour.Properties props) {
        return props.setId(ResourceKey.create(net.minecraft.core.registries.Registries.BLOCK, MCAVie.id(name)));
    }

    public static net.minecraft.world.item.Item.Properties item(String name) {
        return new net.minecraft.world.item.Item.Properties().setId(ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, MCAVie.id(name)));
    }

    public static net.minecraft.world.item.Item.Properties blockItem(String name) {
        return item(name).useBlockDescriptionPrefix();
    }

    public static ResourceKey<net.minecraft.world.entity.EntityType<?>> entity(String name) {
        return ResourceKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, MCAVie.id(name));
    }
}
