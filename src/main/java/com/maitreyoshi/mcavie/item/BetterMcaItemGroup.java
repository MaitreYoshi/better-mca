package com.maitreyoshi.mcavie.item;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.compat.Registration;
import com.maitreyoshi.mcavie.phone.PhoneServer;
import dev.architectury.registry.CreativeTabRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class BetterMcaItemGroup {
    private BetterMcaItemGroup() {
    }

    public static void register() {
        Registration.register(Registries.CREATIVE_MODE_TAB, "main", () -> CreativeTabRegistry.create(builder -> builder
                .title(Component.translatable("itemGroup.mca_vie.main"))
                .icon(() -> new ItemStack(PhoneServer.PHONE))
                .displayItems((context, entries) -> {
                    // Ordre d'enregistrement : chaque nouvel item du mod apparaît automatiquement.
                    for (Item item : BuiltInRegistries.ITEM) {
                        if (item != PhoneServer.PHOTO && MCAVie.MOD_ID.equals(BuiltInRegistries.ITEM.getKey(item).getNamespace())) {
                            entries.accept(item);
                        }
                    }
                })), group -> {
        });
    }
}
