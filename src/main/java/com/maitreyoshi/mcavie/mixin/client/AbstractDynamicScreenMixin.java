package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.client.InteractScreenHolder;
import com.maitreyoshi.mcavie.social.client.RelationsScreen;
import com.maitreyoshi.mcavie.work.client.JobScreen;
import net.mca.client.gui.AbstractDynamicScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

@Mixin(AbstractDynamicScreen.class)
public abstract class AbstractDynamicScreenMixin extends Screen {
    private AbstractDynamicScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "setLayout", at = @At("TAIL"), remap = false)
    private void mcavie$addJobButton(String guiKey, CallbackInfo ci) {
        if (!"main".equals(guiKey) || !((Object) this instanceof InteractScreenHolder holder)) {
            return;
        }
        ClickableWidget lowest = null;
        for (Element element : new ArrayList<>(children())) {
            if (element instanceof ClickableWidget widget && isMcaButton(widget)
                    && (lowest == null || widget.getY() > lowest.getY())) {
                lowest = widget;
            }
        }
        int x = lowest != null ? lowest.getX() : width - 90;
        int y = lowest != null ? lowest.getY() + lowest.getHeight() + 2 : height / 2;
        int w = lowest != null ? lowest.getWidth() : 80;
        int h = lowest != null ? lowest.getHeight() : 20;

        addDrawableChild(ButtonWidget.builder(Text.translatable("job.mca_vie.button"), b ->
                        MinecraftClient.getInstance().setScreen(new JobScreen(holder.mcavie$getVillagerEntity(), this)))
                .dimensions(x, y, w, h).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mca_vie.relations.button"), b ->
                        MinecraftClient.getInstance().setScreen(new RelationsScreen(holder.mcavie$getVillagerEntity(), this)))
                .dimensions(x, y + h + 2, w, h).build());
    }

    private static boolean isMcaButton(ClickableWidget widget) {
        return widget.getMessage().getContent() instanceof TranslatableTextContent content && content.getKey().startsWith("gui.button.");
    }
}
