package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.SurnameText;
import com.maitreyoshi.mcavie.client.SurnameFieldHolder;
import net.mca.client.gui.VillagerEditorScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = VillagerEditorScreen.class, remap = false)
public abstract class VillagerEditorScreenMixin {
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
}
