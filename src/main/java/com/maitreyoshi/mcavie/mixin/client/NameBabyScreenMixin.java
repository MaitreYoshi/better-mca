package com.maitreyoshi.mcavie.mixin.client;

import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import com.maitreyoshi.mcavie.SurnameText;
import com.maitreyoshi.mcavie.client.MCAVieClient;
import com.maitreyoshi.mcavie.client.SurnameFieldHolder;
import net.conczin.mca.client.gui.NameBabyScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

@Mixin(NameBabyScreen.class)
public abstract class NameBabyScreenMixin extends Screen implements SurnameFieldHolder {
    @Unique
    private static final int ROW = 24;

    @Shadow(remap = false)
    private EditBox babyNameTextField;

    @Shadow(remap = false)
    @Final
    private Player player;

    @Unique
    @Nullable
    private EditBox mcavie$surnameField;

    @Unique
    private boolean mcavie$surnameFocused;

    protected NameBabyScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void mcavie$addSurnameField(CallbackInfo ci) {
        babyNameTextField.setHint(Component.translatable("gui.mca_vie.first_name"));
        int x = width / 2 - 100;
        int y = height / 2 - 20 + ROW;
        for (GuiEventListener element : new ArrayList<>(children())) {
            if (element instanceof AbstractWidget widget && widget.getY() >= y) {
                widget.setY(widget.getY() + ROW);
            }
        }
        String previous = mcavie$surnameField == null ? "" : mcavie$surnameField.getValue();
        EditBox field = new EditBox(font, x, y, 200, 20, Component.translatable("gui.mca_vie.surname"));
        field.setMaxLength(SurnameText.MAX_LENGTH);
        field.setHint(Component.translatable("gui.mca_vie.surname"));
        field.setValue(previous);
        mcavie$surnameField = field;
        mcavie$surnameFocused = false;
        addRenderableWidget(field);
        if (previous.isEmpty()) {
            MCAVieClient.requestBabySurname(player.getInventory().selected);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (mcavie$surnameField != null) {
            if (mcavie$surnameField.isMouseOver(mouseX, mouseY)) {
                mcavie$surnameFocused = true;
            } else if (babyNameTextField.isMouseOver(mouseX, mouseY)) {
                mcavie$surnameFocused = false;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        int scanCode = event.scancode();
        int modifiers = event.modifiers();
        if (keyCode == 258 && mcavie$surnameField != null) {
            mcavie$surnameFocused = !mcavie$surnameFocused;
            mcavie$applyFocus();
            return true;
        }
        return super.keyPressed(event);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void mcavie$keepSurnameFocus(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        mcavie$applyFocus();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void mcavie$tickSurname(CallbackInfo ci) {
        if (mcavie$surnameField != null) {
            mcavie$surnameField.tick();
        }
    }

    @Unique
    private void mcavie$applyFocus() {
        if (mcavie$surnameField != null) {
            setFocused(mcavie$surnameFocused ? mcavie$surnameField : babyNameTextField);
        }
    }

    @ModifyArg(method = "lambda$init$0", remap = false, index = 1,
            at = @At(value = "INVOKE", target = "Lnet/mca/network/c2s/BabyNamingVillagerMessage;<init>(ILjava/lang/String;)V"))
    private String mcavie$appendSurname(String firstName) {
        String surname = mcavie$surnameField == null ? "" : SurnameText.sanitize(mcavie$surnameField.getValue());
        if (firstName.isBlank() || surname.isEmpty()) {
            return firstName;
        }
        return SurnameText.withSurname(firstName, surname);
    }

    @Override
    @Nullable
    public String mcavie$getSurname() {
        return mcavie$surnameField == null ? null : mcavie$surnameField.getValue();
    }

    @Override
    public void mcavie$setSurname(String surname) {
        if (mcavie$surnameField != null && mcavie$surnameField.getValue().isBlank()) {
            mcavie$surnameField.setValue(surname);
        }
    }
}
