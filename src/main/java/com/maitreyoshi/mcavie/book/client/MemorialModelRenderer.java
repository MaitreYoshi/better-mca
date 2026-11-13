package com.maitreyoshi.mcavie.book.client;

import com.maitreyoshi.mcavie.life.TombstoneBlocks;
import net.conczin.mca.client.gui.ExtendedBookScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Rendu 3D du monument aux morts pour MemorialModelPage, isolé pour ne pas charger de classes client sur un serveur dédié. */
public final class MemorialModelRenderer {
    private MemorialModelRenderer() {
    }


    /**
     * 26.x : plus de rendu 3D direct dans une interface ; on montre l'objet du monument agrandi.
     * (Un rendu 3D demanderait un « picture-in-picture » dédié.)
     */
    public static void render(ExtendedBookScreen screen, GuiGraphicsExtractor context) {
        context.pose().pushMatrix();
        context.pose().translate(screen.width / 2f - 2 - 32, 40);
        context.pose().scale(4, 4);
        context.item(new net.minecraft.world.item.ItemStack(TombstoneBlocks.MEMORIAL), 0, 0);
        context.pose().popMatrix();
    }
}
