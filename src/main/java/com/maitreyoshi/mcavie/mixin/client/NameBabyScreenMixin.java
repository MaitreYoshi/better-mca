package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.SurnameText;
import com.maitreyoshi.mcavie.client.MCAVieClient;
import com.maitreyoshi.mcavie.client.SurnameFieldHolder;
import net.mca.client.gui.NameBabyScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
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

/**
 * Nommer son bébé : MCA n'a qu'une case, et le dernier mot tapé devenait le nom de famille (« Bernard Jacques »
 * donnait le nom de famille Jacques). On ajoute une case « Nom de famille », pré-remplie avec celui de la famille.
 */
@Mixin(NameBabyScreen.class)
public abstract class NameBabyScreenMixin extends Screen implements SurnameFieldHolder {
    @Unique
    private static final int ROW = 24;

    @Shadow(remap = false)
    private TextFieldWidget babyNameTextField;

    @Shadow(remap = false)
    @Final
    private PlayerEntity player;

    @Unique
    @Nullable
    private TextFieldWidget mcavie$surnameField;

    // MCA redonne le focus à la case prénom à chaque image : on retient quelle case le joueur a choisie.
    @Unique
    private boolean mcavie$surnameFocused;

    protected NameBabyScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void mcavie$addSurnameField(CallbackInfo ci) {
        babyNameTextField.setPlaceholder(Text.translatable("gui.mca_vie.first_name"));
        int x = width / 2 - 100;
        int y = height / 2 - 20 + ROW;
        for (Element element : new ArrayList<>(children())) {
            if (element instanceof ClickableWidget widget && widget.getY() >= y) {
                widget.setY(widget.getY() + ROW);
            }
        }
        String previous = mcavie$surnameField == null ? "" : mcavie$surnameField.getText();
        TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, 200, 20, Text.translatable("gui.mca_vie.surname"));
        field.setMaxLength(SurnameText.MAX_LENGTH);
        field.setPlaceholder(Text.translatable("gui.mca_vie.surname"));
        field.setText(previous);
        mcavie$surnameField = field;
        mcavie$surnameFocused = false;
        addDrawableChild(field);
        if (previous.isEmpty()) {
            MCAVieClient.requestBabySurname(player.getInventory().selectedSlot);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mcavie$surnameField != null) {
            if (mcavie$surnameField.isMouseOver(mouseX, mouseY)) {
                mcavie$surnameFocused = true;
            } else if (babyNameTextField.isMouseOver(mouseX, mouseY)) {
                mcavie$surnameFocused = false;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 258 && mcavie$surnameField != null) { // Tab : passe d'une case à l'autre
            mcavie$surnameFocused = !mcavie$surnameFocused;
            mcavie$applyFocus();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void mcavie$keepSurnameFocus(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
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
        String surname = mcavie$surnameField == null ? "" : SurnameText.sanitize(mcavie$surnameField.getText());
        if (firstName.isBlank() || surname.isEmpty()) {
            return firstName;
        }
        return SurnameText.withSurname(firstName, surname);
    }

    @Override
    @Nullable
    public String mcavie$getSurname() {
        return mcavie$surnameField == null ? null : mcavie$surnameField.getText();
    }

    @Override
    public void mcavie$setSurname(String surname) {
        if (mcavie$surnameField != null && mcavie$surnameField.getText().isBlank()) {
            mcavie$surnameField.setText(surname);
        }
    }
}
