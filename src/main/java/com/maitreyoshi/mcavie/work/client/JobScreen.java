package com.maitreyoshi.mcavie.work.client;

import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkManager;
import com.maitreyoshi.mcavie.work.block.WorkBlocks;
import com.maitreyoshi.mcavie.work.block.WorkstationBlock;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// fiche métier : liste des métiers à gauche, fiche du métier sélectionné à droite
public class JobScreen extends Screen {
    private static final int MAX_WIDTH = 440;
    private static final int MAX_HEIGHT = 250;
    private static final int LIST_WIDTH = 120;
    private static final int ROW_HEIGHT = 20;
    private static final int PAD = 8;
    private static final int BUTTON_HEIGHT = 20;
    private static final int PANEL = 0xC0101010;
    private static final int PANEL_LIGHT = 0x40FFFFFF;
    private static final int SELECTED = 0x60FFFFFF;
    private static final int BORDER = 0xFF555555;

    private final Entity villager;
    @Nullable
    private final Screen parent;
    private final Job[] jobs = Arrays.stream(Job.values()).filter(j -> j.isSelectable() && j != Job.NONE).toArray(Job[]::new);
    private final List<ButtonWidget> actions = new ArrayList<>();

    private Job selected;
    private int scroll;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    @Nullable
    private Job shownFor;

    public JobScreen(Entity villager, @Nullable Screen parent) {
        super(Text.translatable("job.mca_vie.screen_title", villager.getName()));
        this.villager = villager;
        this.parent = parent;
        WorkClient.Info info = WorkClient.get(villager.getId());
        this.selected = info != null && info.job().isSelectable() && info.job() != Job.NONE ? info.job() : jobs[0];
    }

    @Override
    protected void init() {
        panelWidth = Math.min(MAX_WIDTH, width - 20);
        panelHeight = Math.min(MAX_HEIGHT, height - 52);
        left = (width - panelWidth) / 2;
        top = Math.max(26, (height - panelHeight) / 2);
        rebuildActions();
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"), b -> close())
                .dimensions(width / 2 - 50, Math.min(height - BUTTON_HEIGHT - 2, top + panelHeight + 10), 100, BUTTON_HEIGHT).build());
    }

    private Job currentJob() {
        WorkClient.Info info = WorkClient.get(villager.getId());
        return info == null ? Job.NONE : info.job();
    }

    // boutons de la fiche : embaucher, ou gérer le métier actuel
    private void rebuildActions() {
        actions.forEach(this::remove);
        actions.clear();
        Job current = currentJob();
        shownFor = current;
        int detailLeft = left + LIST_WIDTH + PAD;
        int detailWidth = panelWidth - LIST_WIDTH - PAD * 2;
        int y = top + panelHeight - BUTTON_HEIGHT - PAD;
        if (selected != current) {
            ButtonWidget hire = ButtonWidget.builder(Text.translatable("job.mca_vie.hire", selected.getName()), b -> hire())
                    .dimensions(detailLeft, y, detailWidth, BUTTON_HEIGHT).build();
            actions.add(addDrawableChild(hire));
            return;
        }
        int third = (detailWidth - PAD) / 3;
        boolean craft = selected.isCraft();
        ButtonWidget show = ButtonWidget.builder(Text.translatable("job.mca_vie.show"), b -> WorkClient.showWorkstation(villager.getUuid()))
                .dimensions(detailLeft, y, third, BUTTON_HEIGHT)
                .tooltip(Tooltip.of(Text.translatable("job.mca_vie.show.tooltip"))).build();
        ButtonWidget link = ButtonWidget.builder(Text.translatable("job.mca_vie.choose_depot"), b -> {
            WorkClient.pickDepot(villager.getUuid());
            if (client != null) {
                client.setScreen(null);
            }
        }).dimensions(detailLeft + third + PAD / 2, y, third, BUTTON_HEIGHT)
                .tooltip(Tooltip.of(Text.translatable("job.mca_vie.choose_depot.tooltip"))).build();
        show.active = craft;
        link.active = craft;
        ButtonWidget fire = ButtonWidget.builder(Text.translatable("job.mca_vie.fire").formatted(Formatting.RED), b -> confirm(Job.NONE))
                .dimensions(detailLeft + (third + PAD / 2) * 2, y, detailWidth - (third + PAD / 2) * 2, BUTTON_HEIGHT).build();
        actions.add(addDrawableChild(show));
        actions.add(addDrawableChild(link));
        actions.add(addDrawableChild(fire));
    }

    private void hire() {
        Job current = currentJob();
        if (current.isCraft() || current.isMcaCombat()) {
            confirm(selected);
        } else {
            apply(selected);
        }
    }

    private void confirm(Job target) {
        if (client == null) {
            return;
        }
        Job current = currentJob();
        Text message = target == Job.NONE
                ? Text.translatable("job.mca_vie.confirm_fire", villager.getName(), current.getName())
                : Text.translatable("job.mca_vie.confirm_change", villager.getName(), current.getName(), target.getName());
        client.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                apply(target);
            } else if (client != null) {
                client.setScreen(this);
            }
        }, Text.translatable("job.mca_vie.confirm_title"), message));
    }

    private void apply(Job job) {
        WorkClient.setJob(villager.getUuid(), job);
        if (job != Job.NONE) {
            selected = job;
        }
        if (client != null) {
            client.setScreen(this);
        }
    }

    @Override
    public void tick() {
        // la réponse du serveur arrive après le clic : on remet les boutons à jour quand le métier change
        if (shownFor != currentJob()) {
            rebuildActions();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top - 14, 0xFFFFFF);
        context.fill(left, top, left + panelWidth, top + panelHeight, PANEL);
        context.drawBorder(left, top, panelWidth, panelHeight, BORDER);
        renderList(context, mouseX, mouseY);
        renderDetail(context);
        super.render(context, mouseX, mouseY, delta);
    }

    private int visibleRows() {
        return (panelHeight - PAD * 2) / ROW_HEIGHT;
    }

    private void renderList(DrawContext context, int mouseX, int mouseY) {
        int x = left + PAD / 2;
        int y = top + PAD;
        int w = LIST_WIDTH - PAD;
        Job current = currentJob();
        context.fill(left + LIST_WIDTH, top + PAD, left + LIST_WIDTH + 1, top + panelHeight - PAD, BORDER);
        context.enableScissor(left, top + PAD, left + LIST_WIDTH, top + panelHeight - PAD);
        for (int i = 0; i < jobs.length; i++) {
            Job job = jobs[i];
            int rowY = y + (i - scroll) * ROW_HEIGHT;
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (job == selected) {
                context.fill(x, rowY, x + w, rowY + ROW_HEIGHT, SELECTED);
            } else if (hovered) {
                context.fill(x, rowY, x + w, rowY + ROW_HEIGHT, PANEL_LIGHT);
            }
            context.drawItem(iconOf(job), x + 2, rowY + 2);
            Text name = job == current ? job.getName().copy().formatted(Formatting.GOLD) : job.getName();
            context.drawTextWithShadow(textRenderer, net.minecraft.util.Language.getInstance().reorder(textRenderer.trimToWidth(name, w - 24)), x + 22, rowY + 6, 0xFFFFFF);
        }
        context.disableScissor();
    }

    private void renderDetail(DrawContext context) {
        int x = left + LIST_WIDTH + PAD;
        int w = panelWidth - LIST_WIDTH - PAD * 2;
        int y = top + PAD;
        WorkClient.Info info = WorkClient.get(villager.getId());
        Job current = info == null ? Job.NONE : info.job();
        boolean isCurrent = selected == current;

        if (isCurrent && villager instanceof LivingEntity living) {
            InventoryScreen.drawEntity(context, x + w - 16, y + 52, 24, 0, 0, living);
        }
        context.drawItem(iconOf(selected), x, y);
        context.drawTextWithShadow(textRenderer, selected.getName().copy().formatted(Formatting.BOLD), x + 20, y + 4, 0xFFFFFF);
        if (isCurrent) {
            context.drawTextWithShadow(textRenderer, Text.translatable("job.mca_vie.current").formatted(Formatting.GOLD),
                    x + 24 + textRenderer.getWidth(selected.getName().copy().formatted(Formatting.BOLD)), y + 4, 0xFFFFFF);
        }
        y += 22;

        int textWidth = isCurrent ? w - 40 : w;
        for (OrderedText line : textRenderer.wrapLines(Text.translatable("job.mca_vie.desc." + selected.id()), textWidth)) {
            context.drawTextWithShadow(textRenderer, line, x, y, 0xD0D0D0);
            y += 10;
        }
        y += 6;

        WorkstationBlock station = WorkBlocks.forJob(selected);
        Text place = station != null
                ? Text.translatable("job.mca_vie.workstation_label", station.getName().copy().formatted(Formatting.WHITE))
                : Text.translatable("job.mca_vie.no_workstation_needed");
        context.drawTextWithShadow(textRenderer, place.copy().formatted(Formatting.GRAY), x, y, 0xFFFFFF);
        y += 12;
        y = renderItems(context, x, y, "job.mca_vie.consumes", consumes(selected));
        y = renderItems(context, x, y, "job.mca_vie.produces", produces(selected));
        if (selected.isCraft()) {
            context.drawTextWithShadow(textRenderer, Text.translatable("job.mca_vie.hours", WorkManager.START_HOUR, WorkManager.END_HOUR)
                    .formatted(Formatting.GRAY), x, y, 0xFFFFFF);
            y += 14;
        }

        if (isCurrent && info != null && selected.isCraft()) {
            renderProgress(context, info, x, y, w);
        }
    }

    private int renderItems(DrawContext context, int x, int y, String key, Item[] items) {
        if (items.length == 0) {
            return y;
        }
        Text label = Text.translatable(key).formatted(Formatting.GRAY);
        context.drawTextWithShadow(textRenderer, label, x, y + 4, 0xFFFFFF);
        int ix = x + textRenderer.getWidth(label) + 4;
        for (Item item : items) {
            context.drawItem(new ItemStack(item), ix, y);
            ix += 18;
        }
        return y + 18;
    }

    // niveau, barre d'expérience, état en direct et poste lié
    private void renderProgress(DrawContext context, WorkClient.Info info, int x, int y, int w) {
        int level = Math.max(1, Math.min(5, info.level()));
        Text levelName = Text.translatable("job.mca_vie.level." + level).formatted(Formatting.GOLD);
        int next = WorkManager.nextLevelExperience(level);
        int from = WorkManager.levelExperience(level);
        Text xpText = next < 0 ? Text.translatable("job.mca_vie.max_level")
                : Text.translatable("job.mca_vie.level_xp", info.experience() - from, next - from);
        context.drawTextWithShadow(textRenderer, levelName, x, y, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, xpText.copy().formatted(Formatting.GRAY), x + w - textRenderer.getWidth(xpText), y, 0xFFFFFF);
        y += 11;
        float progress = next < 0 ? 1F : Math.max(0F, Math.min(1F, (info.experience() - from) / (float) Math.max(1, next - from)));
        context.fill(x, y, x + w, y + 5, 0xFF2A2A2A);
        context.fill(x, y, x + (int) (w * progress), y + 5, 0xFF7FD34E);
        y += 10;
        context.drawTextWithShadow(textRenderer, Text.translatable("job.mca_vie.status_label", WorkClient.statusText(info)), x, y, 0xFFFFFF);
        y += 11;
        Text depot = Text.translatable(info.hasDepot() ? "job.mca_vie.has_depot" : "job.mca_vie.no_depot")
                .formatted(info.hasDepot() ? Formatting.GREEN : Formatting.GOLD);
        for (OrderedText line : textRenderer.wrapLines(depot, w)) {
            context.drawTextWithShadow(textRenderer, line, x, y, 0xFFFFFF);
            y += 10;
        }
    }

    private static ItemStack iconOf(Job job) {
        WorkstationBlock station = WorkBlocks.forJob(job);
        return station != null ? new ItemStack(station) : job.icon();
    }

    private static Item[] produces(Job job) {
        return switch (job) {
            case FARMER -> new Item[]{Items.WHEAT, Items.CARROT, Items.POTATO, Items.BEETROOT, Items.PUMPKIN};
            case LUMBERJACK -> new Item[]{Items.OAK_LOG, Items.OAK_SAPLING, Items.STICK, Items.APPLE};
            case FISHER -> new Item[]{Items.COD, Items.SALMON, Items.TROPICAL_FISH};
            case BREEDER -> new Item[]{Items.WHITE_WOOL, Items.EGG};
            case BAKER -> new Item[]{Items.BREAD};
            case MINER -> new Item[]{Items.COBBLESTONE, Items.COAL, Items.RAW_IRON, Items.RAW_COPPER};
            case COOK -> new Item[]{Items.COOKED_BEEF, Items.BAKED_POTATO, Items.MUSHROOM_STEW, Items.BEETROOT_SOUP};
            default -> new Item[0];
        };
    }

    private static Item[] consumes(Job job) {
        return switch (job) {
            case FARMER -> new Item[]{Items.WHEAT_SEEDS};
            case BLACKSMITH -> new Item[]{Items.IRON_INGOT};
            case BAKER -> new Item[]{Items.WHEAT};
            case COOK -> new Item[]{Items.BEEF, Items.POTATO, Items.BOWL};
            default -> new Item[0];
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = left + PAD / 2;
        if (mouseX >= x && mouseX < x + LIST_WIDTH - PAD && mouseY >= top + PAD && mouseY < top + panelHeight - PAD) {
            int index = (int) ((mouseY - top - PAD) / ROW_HEIGHT) + scroll;
            if (index >= 0 && index < jobs.length) {
                selected = jobs[index];
                rebuildActions();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (mouseX < left + LIST_WIDTH) {
            scroll = Math.max(0, Math.min(Math.max(0, jobs.length - visibleRows()), scroll - (int) Math.signum(amount)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
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
