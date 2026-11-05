package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.SurnameText;
import com.maitreyoshi.mcavie.client.SurnameFieldHolder;
import net.mca.client.gui.VillagerEditorScreen;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.UUID;

@Mixin(value = VillagerEditorScreen.class, remap = false)
public abstract class VillagerEditorScreenMixin extends Screen implements SurnameFieldHolder {
    @Unique
    private static final int ROW_HEIGHT = 22;
    @Unique
    private static final int FIELD_WIDTH = 175 / 3 * 2;

    @Shadow
    protected String page;

    @Shadow
    @Final
    UUID villagerUUID;

    @Shadow
    @Final
    UUID playerUUID;

    @Unique
    @Nullable
    private TextFieldWidget mcavie$editorSurnameField;

    protected VillagerEditorScreenMixin(Text title) {
        super(title);
    }

    @ModifyVariable(method = "updateName", at = @At("HEAD"), argsOnly = true)
    private String mcavie$appendSurname(String name) {
        if ((Object) this instanceof SurnameFieldHolder holder) {
            String surname = holder.mcavie$getSurname();
            if (surname != null && !surname.isBlank() && name != null && !name.isBlank()) {
                return SurnameText.withSurname(name, SurnameText.sanitize(surname));
            }
        }
        return name;
    }

    @Inject(method = "setPage", at = @At("TAIL"))
    private void mcavie$addVillagerSurnameField(String newPage, CallbackInfo ci) {
        mcavie$editorSurnameField = null;
        if ((Object) getClass() != VillagerEditorScreen.class || !"general".equals(page) || villagerUUID.equals(playerUUID)) {
            return;
        }
        TextFieldWidget nameField = ((VillagerEditorScreenAccessor) this).mcavie$getNameField();
        if (nameField == null) {
            return;
        }

        int x = nameField.getX();
        int nameY = nameField.getY();
        for (Element element : new ArrayList<>(children())) {
            if (element instanceof ClickableWidget widget && widget.getX() >= x && widget.getY() > nameY + 10) {
                widget.setY(widget.getY() + ROW_HEIGHT);
            }
        }

        String full = nameField.getText().trim();
        int space = full.lastIndexOf(' ');
        String first = space > 0 ? full.substring(0, space).trim() : full;
        String surname = space > 0 ? full.substring(space + 1).trim() : "";

        TextFieldWidget field = new TextFieldWidget(textRenderer, x, nameY + ROW_HEIGHT, FIELD_WIDTH, 18, Text.translatable("gui.mca_vie.surname"));
        field.setMaxLength(SurnameText.MAX_LENGTH);
        field.setPlaceholder(Text.translatable("gui.mca_vie.surname"));
        field.setText(surname);
        mcavie$editorSurnameField = field;
        field.setChangedListener(s -> {
            TextFieldWidget name = ((VillagerEditorScreenAccessor) this).mcavie$getNameField();
            if (name != null) {
                ((VillagerEditorScreen) (Object) this).updateName(name.getText());
            }
        });
        addDrawableChild(field);

        if (!first.isBlank() && !first.equals(full)) {
            nameField.setText(first);
        }
    }

    @Override
    @Nullable
    public String mcavie$getSurname() {
        return mcavie$editorSurnameField == null ? null : mcavie$editorSurnameField.getText();
    }

    @Override
    public void mcavie$setSurname(String surname) {
        if (mcavie$editorSurnameField != null) {
            mcavie$editorSurnameField.setText(surname);
        }
    }
}
