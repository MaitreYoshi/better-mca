package com.maitreyoshi.mcavie.compat.client;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.IdentityHashMap;
import java.util.Map;

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
        void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay);
    }
}
