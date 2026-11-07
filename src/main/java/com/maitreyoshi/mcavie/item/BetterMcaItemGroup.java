package com.maitreyoshi.mcavie.item;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.compat.Registration;
import com.maitreyoshi.mcavie.phone.PhoneServer;
import dev.architectury.registry.CreativeTabRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;

public final class BetterMcaItemGroup {
    private BetterMcaItemGroup() {
    }

    public static void register() {
        Registration.register(RegistryKeys.ITEM_GROUP, "main", () -> CreativeTabRegistry.create(builder -> builder
                .displayName(Text.translatable("itemGroup.mca_vie.main"))
                .icon(() -> new ItemStack(PhoneServer.PHONE))
                .entries((context, entries) -> {
                    // Ordre d'enregistrement : chaque nouvel item du mod apparaît automatiquement.
                    for (Item item : Registries.ITEM) {
                        if (item != PhoneServer.PHOTO && MCAVie.MOD_ID.equals(Registries.ITEM.getId(item).getNamespace())) {
                            entries.add(item);
                        }
                    }
                })), group -> {
        });
    }
}
