package com.maitreyoshi.mcavie.item;

import com.maitreyoshi.mcavie.MCAVie;
import com.maitreyoshi.mcavie.phone.PhoneServer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

public final class BetterMcaItemGroup {
    private BetterMcaItemGroup() {
    }

    public static void register() {
        Registry.register(Registries.ITEM_GROUP, MCAVie.id("main"), FabricItemGroup.builder()
                .displayName(Text.translatable("itemGroup.mca_vie.main"))
                .icon(() -> new ItemStack(PhoneServer.PHONE))
                .entries((context, entries) -> {
                    // Ordre d'enregistrement : chaque nouvel item du mod apparaît automatiquement.
                    for (Item item : Registries.ITEM) {
                        if (item != PhoneServer.PHOTO && MCAVie.MOD_ID.equals(Registries.ITEM.getId(item).getNamespace())) {
                            entries.add(item);
                        }
                    }
                })
                .build());
    }
}
