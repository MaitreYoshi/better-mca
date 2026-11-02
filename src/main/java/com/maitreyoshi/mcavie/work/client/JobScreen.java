package com.maitreyoshi.mcavie.work.client;

import com.maitreyoshi.mcavie.work.Job;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class JobScreen extends Screen {
    private static final int BUTTON_WIDTH = 120;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 4;
    private static final int TOP_MIN = 62;
    private static final int FOOTER = 64;

    private final Entity villager;
    @Nullable
    private final Screen parent;
    private final List<JobButton> jobButtons = new ArrayList<>();

    public JobScreen(Entity villager, @Nullable Screen parent) {
        super(Text.translatable("job.mca_vie.screen_title", villager.getName()));
        this.villager = villager;
        this.parent = parent;
    }

    private record JobButton(Job job, ButtonWidget button) {
    }

    @Override
    protected void init() {
        jobButtons.clear();
        Job[] jobs = java.util.Arrays.stream(Job.values()).filter(Job::isSelectable).toArray(Job[]::new);
        int columns = 2;
        int rows = (jobs.length + columns - 1) / columns;
        while (columns < 4 && TOP_MIN + rows * (BUTTON_HEIGHT + GAP) + FOOTER > height) {
            columns++;
            rows = (jobs.length + columns - 1) / columns;
        }
        int buttonWidth = Math.min(BUTTON_WIDTH, (width - 20) / columns - GAP);
        int left = width / 2 - (columns * (buttonWidth + GAP) - GAP) / 2;
        int top = Math.max(TOP_MIN, height / 2 - (rows * (BUTTON_HEIGHT + GAP) + FOOTER) / 2);

        for (int i = 0; i < jobs.length; i++) {
            Job job = jobs[i];
            int x = left + (i % columns) * (buttonWidth + GAP);
            int y = top + (i / columns) * (BUTTON_HEIGHT + GAP);
            ButtonWidget button = addDrawableChild(ButtonWidget.builder(Text.literal("    ").append(job.getName()), b -> {
                WorkClient.setJob(villager.getUuid(), job);
                close();
            }).dimensions(x, y, buttonWidth, BUTTON_HEIGHT).build());
            jobButtons.add(new JobButton(job, button));
        }

        int bottom = top + rows * (BUTTON_HEIGHT + GAP) + 10;
        addDrawableChild(ButtonWidget.builder(Text.translatable("job.mca_vie.choose_depot"), b -> {
            WorkClient.pickDepot(villager.getUuid());
            if (client != null) {
                client.setScreen(null);
            }
        }).dimensions(width / 2 - BUTTON_WIDTH / 2, bottom, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.translatable("job.mca_vie.choose_depot.tooltip"))).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"), b -> close())
                .dimensions(width / 2 - 50, bottom + BUTTON_HEIGHT + GAP + 6, 100, BUTTON_HEIGHT).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xFFFFFF);

        WorkClient.Info info = WorkClient.get(villager.getId());
        Job current = info == null ? Job.NONE : info.job();
        for (JobButton jobButton : jobButtons) {
            jobButton.button().active = jobButton.job() != current;
        }

        super.render(context, mouseX, mouseY, delta);

        for (JobButton jobButton : jobButtons) {
            ButtonWidget b = jobButton.button();
            context.drawItem(jobButton.job().icon(), b.getX() + 4, b.getY() + 2);
        }

        if (info != null) {
            Text status = WorkClient.menuLine(info);
            context.drawCenteredTextWithShadow(textRenderer, status, width / 2, 36, 0xFFFFFF);
            Text depot = Text.translatable(info.hasDepot() ? "job.mca_vie.has_depot" : "job.mca_vie.no_depot")
                    .formatted(info.hasDepot() ? Formatting.GREEN : Formatting.GOLD);
            context.drawCenteredTextWithShadow(textRenderer, depot, width / 2, 48, 0xFFFFFF);
        }
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
