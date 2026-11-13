package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.client.InteractScreenHolder;
import com.maitreyoshi.mcavie.social.client.RelationsScreen;
import com.maitreyoshi.mcavie.work.client.JobScreen;
import net.mca.client.gui.AbstractDynamicScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

@Mixin(AbstractDynamicScreen.class)
public abstract class AbstractDynamicScreenMixin extends Screen {
    private AbstractDynamicScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "setLayout", at = @At("TAIL"), remap = false)
    private void mcavie$addJobButton(String guiKey, CallbackInfo ci) {
        if (!"main".equals(guiKey) || !((Object) this instanceof InteractScreenHolder holder)) {
            return;
        }
        boolean mercenary = holder.mcavie$getVillagerEntity() instanceof net.mca.entity.VillagerEntityMCA villager
                && villager.getProfession() == net.mca.ProfessionsMCA.MERCENARY.get();
        if (!mercenary) {
            for (GuiEventListener element : new ArrayList<>(children())) {
                if (element instanceof AbstractWidget widget && widget.getMessage().getContents() instanceof TranslatableContents content
                        && content.getKey().equals("gui.button.work")) {
                    removeWidget(widget);
                }
            }
        }
        AbstractWidget lowest = null;
        for (GuiEventListener element : new ArrayList<>(children())) {
            if (element instanceof AbstractWidget widget && isMcaButton(widget)
                    && (lowest == null || widget.getY() > lowest.getY())) {
                lowest = widget;
            }
        }
        int x = lowest != null ? lowest.getX() : width - 90;
        int y = lowest != null ? lowest.getY() + lowest.getHeight() + 2 : height / 2;
        int w = lowest != null ? lowest.getWidth() : 80;
        int h = lowest != null ? lowest.getHeight() : 20;

        addRenderableWidget(Button.builder(Component.translatable("job.mca_vie.button"), b ->
                        Minecraft.getInstance().setScreen(new JobScreen(holder.mcavie$getVillagerEntity(), this)))
                .bounds(x, y, w, h).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.mca_vie.relations.button"), b ->
                        Minecraft.getInstance().setScreen(new RelationsScreen(holder.mcavie$getVillagerEntity(), this)))
                .bounds(x, y + h + 2, w, h).build());
    }

    private static boolean isMcaButton(AbstractWidget widget) {
        return widget.getMessage().getContents() instanceof TranslatableContents content && content.getKey().startsWith("gui.button.");
    }
}
