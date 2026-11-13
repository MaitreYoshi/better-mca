package com.maitreyoshi.mcavie.book;

import com.maitreyoshi.mcavie.book.client.MemorialModelRenderer;
import net.mca.client.book.pages.Page;
import net.mca.client.gui.ExtendedBookScreen;
import net.minecraft.client.gui.DrawContext;

/** Page de livre montrant le monument aux morts en 3D (rendu dans {@link MemorialModelRenderer}). */
public class MemorialModelPage extends Page {
    @Override
    public void render(ExtendedBookScreen screen, DrawContext context, int mouseX, int mouseY, float delta) {
        MemorialModelRenderer.render(screen, context);
    }
}
