package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.SurnameText;
import com.maitreyoshi.mcavie.client.MCAVieClient;
import com.maitreyoshi.mcavie.client.SurnameFieldHolder;
import net.mca.MCA;
import net.mca.client.gui.DestinyScreen;
import net.mca.client.gui.VillagerEditorScreen;
import net.mca.util.compat.ButtonWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.UUID;

@Mixin(DestinyScreen.class)
public abstract class DestinyScreenMixin extends VillagerEditorScreen implements SurnameFieldHolder {
    @Unique
    private static final int ROW_HEIGHT = 22;

    @Shadow(remap = false)
    private ButtonWidget acceptWidget;

    @Unique
    @Nullable
    private EditBox mcavie$surnameField;

    @Unique
    @Nullable
    private String mcavie$typedSurname;

    private DestinyScreenMixin(UUID villagerUUID, UUID playerUUID) {
        super(villagerUUID, playerUUID);
    }

    @Inject(method = "setPage", at = @At("HEAD"), remap = false)
    private void mcavie$commitOnLeave(String newPage, CallbackInfo ci) {
        if ("general".equals(page) && !"general".equals(newPage)) {
            mcavie$commitSurname();
        }
    }

    @Override
    public void removed() {
        mcavie$commitSurname();
        super.removed();
    }

    @Unique
    private void mcavie$commitSurname() {
        if (mcavie$typedSurname == null) {
            return;
        }
        String cleaned = SurnameText.sanitize(mcavie$typedSurname);
        if (!cleaned.isEmpty() && !cleaned.equals(MCAVieClient.getOwnSurname())) {
            MCAVieClient.sendSurname(cleaned);
        }
    }

    @Inject(method = "setPage", at = @At("TAIL"), remap = false)
    private void mcavie$addSurnameField(String newPage, CallbackInfo ci) {
        if (!"general".equals(page)) {
            mcavie$surnameField = null;
            return;
        }

        int x = width / 2 - DATA_WIDTH / 2;
        int nameY = height / 2;

        for (GuiEventListener element : new ArrayList<>(children())) {
            if (element instanceof AbstractWidget widget && widget.getY() > nameY + 10) {
                widget.setY(widget.getY() + ROW_HEIGHT);
            }
        }

        EditBox field = new EditBox(font, x, nameY + ROW_HEIGHT, DATA_WIDTH / 3 * 2, 18, Component.translatable("gui.mca_vie.surname"));
        field.setMaxLength(SurnameText.MAX_LENGTH);
        field.setHint(Component.translatable("gui.mca_vie.surname"));
        field.setValue(mcavie$typedSurname != null ? mcavie$typedSurname : MCAVieClient.getOwnSurname());
        mcavie$surnameField = field;
        field.setResponder(surname -> {
            mcavie$typedSurname = surname;
            mcavie$refreshName();
        });
        addRenderableWidget(field);

        addRenderableWidget(new ButtonWidget(x + DATA_WIDTH / 3 * 2 + 1, nameY + ROW_HEIGHT - 1, DATA_WIDTH / 3 - 2, 20,
                Component.translatable("gui.button.random"), b -> MCAVieClient.requestRandomSurname()));

        EditBox nameField = ((VillagerEditorScreenAccessor) this).mcavie$getNameField();
        String surname = field.getValue();
        if (nameField != null && !surname.isBlank()) {
            String first = SurnameText.firstName(nameField.getValue(), surname);
            if (!first.isBlank() && !first.equals(nameField.getValue())) {
                nameField.setValue(first);
            }
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void mcavie$updateAcceptButton(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if ("general".equals(page) && acceptWidget != null && mcavie$surnameField != null) {
            EditBox nameField = ((VillagerEditorScreenAccessor) this).mcavie$getNameField();
            boolean nameOk = nameField != null && !MCA.isBlankString(nameField.getValue());
            boolean surnameOk = !MCA.isBlankString(mcavie$surnameField.getValue());
            acceptWidget.active = nameOk && surnameOk;
        }
    }

    @Unique
    private void mcavie$refreshName() {
        EditBox nameField = ((VillagerEditorScreenAccessor) this).mcavie$getNameField();
        if (nameField != null) {
            updateName(nameField.getValue());
        }
    }

    @Override
    @Nullable
    public String mcavie$getSurname() {
        return mcavie$surnameField == null ? null : mcavie$surnameField.getValue();
    }

    @Override
    public void mcavie$setSurname(String surname) {
        if (mcavie$surnameField != null) {
            mcavie$surnameField.setValue(surname);
        }
    }
}
