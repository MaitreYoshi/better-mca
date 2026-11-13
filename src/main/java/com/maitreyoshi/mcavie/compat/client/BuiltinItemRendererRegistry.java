package com.maitreyoshi.mcavie.compat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class BuiltinItemRendererRegistry {
    public static final BuiltinItemRendererRegistry INSTANCE = new BuiltinItemRendererRegistry();
    private final Map<Item, DynamicItemRenderer> renderers = new IdentityHashMap<>();

    private BuiltinItemRendererRegistry() {
    }

    public void register(Item item, DynamicItemRenderer renderer) {
        renderers.put(item, renderer);
    }

    public DynamicItemRenderer get(Item item) {
        return renderers.get(item);
    }

    @FunctionalInterface
    public interface DynamicItemRenderer {
        void render(ItemStack stack, ItemDisplayContext mode, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay);
    }
}
