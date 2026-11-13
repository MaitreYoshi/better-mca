package com.maitreyoshi.mcavie.work.client;

import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkManager;
import com.maitreyoshi.mcavie.work.block.WorkBlocks;
import com.maitreyoshi.mcavie.work.block.WorkstationBlock;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
    private final List<Button> actions = new ArrayList<>();

    private Job selected;
    private int scroll;
    private int detailScroll;
    private int detailOverflow;
    @Nullable
    private Job detailFor;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    @Nullable
    private Job shownFor;
    @Nullable
    private Component refusal;
    private int refusalTicks;
    // 2 s affiché puis 1 s de fondu
    private static final int REFUSAL_TICKS = 60;
    private static final int REFUSAL_FADE = 20;

    public JobScreen(Entity villager, @Nullable Screen parent) {
        super(Component.translatable("job.mca_vie.screen_title", villager.getName()));
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
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(width / 2 - 50, Math.min(height - BUTTON_HEIGHT - 2, top + panelHeight + 10), 100, BUTTON_HEIGHT).build());
    }

    private Job currentJob() {
        WorkClient.Info info = WorkClient.get(villager.getId());
        return info == null ? Job.NONE : info.job();
    }

    private void rebuildActions() {
        actions.forEach(this::removeWidget);
        actions.clear();
        Job current = currentJob();
        shownFor = current;
        int detailLeft = left + LIST_WIDTH + PAD;
        int detailWidth = panelWidth - LIST_WIDTH - PAD * 2;
        int y = top + panelHeight - BUTTON_HEIGHT - PAD;
        if (selected != current) {
            Button hire = Button.builder(Component.translatable("job.mca_vie.hire", selected.getName()), b -> hire())
                    .bounds(detailLeft, y, detailWidth, BUTTON_HEIGHT).build();
            actions.add(addRenderableWidget(hire));
            return;
        }
        int third = (detailWidth - PAD) / 3;
        boolean craft = selected.isCraft();
        Button show = Button.builder(Component.translatable("job.mca_vie.show"), b -> WorkClient.showWorkstation(villager.getUUID()))
                .bounds(detailLeft, y, third, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("job.mca_vie.show.tooltip"))).build();
        Button link = Button.builder(Component.translatable("job.mca_vie.choose_depot"), b -> {
            WorkClient.pickDepot(villager.getUUID());
            if (minecraft != null) {
                minecraft.setScreen(null);
            }
        }).bounds(detailLeft + third + PAD / 2, y, third, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("job.mca_vie.choose_depot.tooltip"))).build();
        show.active = craft;
        link.active = craft;
        Button fire = Button.builder(Component.translatable("job.mca_vie.fire").withStyle(ChatFormatting.RED), b -> confirm(Job.NONE))
                .bounds(detailLeft + (third + PAD / 2) * 2, y, detailWidth - (third + PAD / 2) * 2, BUTTON_HEIGHT).build();
        actions.add(addRenderableWidget(show));
        actions.add(addRenderableWidget(link));
        actions.add(addRenderableWidget(fire));
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
        if (minecraft == null) {
            return;
        }
        Job current = currentJob();
        Component message = target == Job.NONE
                ? Component.translatable("job.mca_vie.confirm_fire", villager.getName(), current.getName())
                : Component.translatable("job.mca_vie.confirm_change", villager.getName(), current.getName(), target.getName());
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                apply(target);
            } else if (minecraft != null) {
                minecraft.setScreen(this);
            }
        }, Component.translatable("job.mca_vie.confirm_title"), message));
    }

    private void apply(Job job) {
        WorkClient.setJob(villager.getUUID(), job);
        if (job != Job.NONE) {
            selected = job;
        }
        if (minecraft != null) {
            minecraft.setScreen(this);
        }
    }

    public void showRefusal(Component message) {
        refusal = message;
        refusalTicks = REFUSAL_TICKS;
    }

    @Override
    public void tick() {
        if (refusalTicks > 0 && --refusalTicks == 0) {
            refusal = null;
        }
        if (shownFor != currentJob()) {
            rebuildActions();
        }
    }

    @Override
    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredString(font, title, width / 2, top - 14, 0xFFFFFF);
        context.fill(left, top, left + panelWidth, top + panelHeight, PANEL);
        context.renderOutline(left, top, panelWidth, panelHeight, BORDER);
        renderList(context, mouseX, mouseY);
        renderDetail(context);
        super.render(context, mouseX, mouseY, delta);
        if (refusal != null) {
            float remaining = refusalTicks - delta;
            int alpha = (int) (255 * Math.max(0f, Math.min(1f, remaining / REFUSAL_FADE)));
            if (alpha > 4) {
                int detailLeft = left + LIST_WIDTH + PAD;
                int detailWidth = panelWidth - LIST_WIDTH - PAD * 2;
                int y = top + panelHeight - BUTTON_HEIGHT - PAD - font.lineHeight - 4;
                context.drawCenteredString(font, refusal, detailLeft + detailWidth / 2, y, (alpha << 24) | 0xFFFFFF);
            }
        }
    }

    private int visibleRows() {
        return (panelHeight - PAD * 2) / ROW_HEIGHT;
    }

    private void renderList(GuiGraphicsExtractor context, int mouseX, int mouseY) {
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
            context.renderItem(iconOf(job), x + 2, rowY + 2);
            Component name = job == current ? job.getName().copy().withStyle(ChatFormatting.GOLD) : job.getName();
            context.drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(name, w - 24)), x + 22, rowY + 6, 0xFFFFFF);
        }
        context.disableScissor();
    }

    private void renderDetail(GuiGraphicsExtractor context) {
        int x = left + LIST_WIDTH + PAD;
        int w = panelWidth - LIST_WIDTH - PAD * 2;
        int y = top + PAD;
        WorkClient.Info info = WorkClient.get(villager.getId());
        Job current = info == null ? Job.NONE : info.job();
        boolean isCurrent = selected == current;

        if (isCurrent && villager instanceof LivingEntity living) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(context, x + w - 16, y + 52, 24, 0, 0, living);
        }
        context.renderItem(iconOf(selected), x, y);
        context.drawString(font, selected.getName().copy().withStyle(ChatFormatting.BOLD), x + 20, y + 4, 0xFFFFFF);
        if (isCurrent) {
            context.drawString(font, Component.translatable("job.mca_vie.current").withStyle(ChatFormatting.GOLD),
                    x + 24 + font.width(selected.getName().copy().withStyle(ChatFormatting.BOLD)), y + 4, 0xFFFFFF);
        }
        y += 22;

        if (detailFor != selected) {
            detailFor = selected;
            detailScroll = 0;
        }
        int clipTop = y;
        int clipBottom = top + panelHeight - BUTTON_HEIGHT - PAD - 4;
        context.enableScissor(x, clipTop, x + w, clipBottom);
        y -= detailScroll;
        int contentTop = y;

        int textWidth = isCurrent ? w - 40 : w;
        for (FormattedCharSequence line : font.split(Component.translatable("job.mca_vie.desc." + selected.id()), textWidth)) {
            context.drawString(font, line, x, y, 0xD0D0D0);
            y += 10;
        }
        y += 6;

        WorkstationBlock station = WorkBlocks.forJob(selected);
        Component place = station != null
                ? Component.translatable("job.mca_vie.workstation_label", station.getName().copy().withStyle(ChatFormatting.WHITE))
                : Component.translatable("job.mca_vie.no_workstation_needed");
        context.drawString(font, place.copy().withStyle(ChatFormatting.GRAY), x, y, 0xFFFFFF);
        y += 12;
        y = renderItems(context, x, y, "job.mca_vie.consumes", consumes(selected));
        y = renderItems(context, x, y, "job.mca_vie.produces", produces(selected));
        if (selected.isCraft()) {
            context.drawString(font, Component.translatable("job.mca_vie.hours", WorkManager.START_HOUR, WorkManager.END_HOUR)
                    .withStyle(ChatFormatting.GRAY), x, y, 0xFFFFFF);
            y += 14;
        }

        if (isCurrent && info != null && selected.isCraft()) {
            y = renderProgress(context, info, x, y, w);
        }
        context.disableScissor();
        detailOverflow = Math.max(0, y - contentTop - (clipBottom - clipTop));
        detailScroll = Math.min(detailScroll, detailOverflow);
        if (detailOverflow > 0) {
            int track = clipBottom - clipTop;
            int thumb = Math.max(10, track * track / (track + detailOverflow));
            int thumbY = clipTop + (track - thumb) * detailScroll / detailOverflow;
            context.fill(x + w + 2, clipTop, x + w + 4, clipBottom, 0xFF2A2A2A);
            context.fill(x + w + 2, thumbY, x + w + 4, thumbY + thumb, 0xFFAAAAAA);
        }
    }

    private int renderItems(GuiGraphicsExtractor context, int x, int y, String key, Item[] items) {
        if (items.length == 0) {
            return y;
        }
        Component label = Component.translatable(key).withStyle(ChatFormatting.GRAY);
        context.drawString(font, label, x, y + 4, 0xFFFFFF);
        int ix = x + font.width(label) + 4;
        for (Item item : items) {
            context.renderItem(new ItemStack(item), ix, y);
            ix += 18;
        }
        return y + 18;
    }

    private int renderProgress(GuiGraphicsExtractor context, WorkClient.Info info, int x, int y, int w) {
        int level = Math.max(1, Math.min(5, info.level()));
        Component levelName = Component.translatable("job.mca_vie.level." + level).withStyle(ChatFormatting.GOLD);
        int next = WorkManager.nextLevelExperience(level);
        int from = WorkManager.levelExperience(level);
        Component xpText = next < 0 ? Component.translatable("job.mca_vie.max_level")
                : Component.translatable("job.mca_vie.level_xp", info.experience() - from, next - from);
        context.drawString(font, levelName, x, y, 0xFFFFFF);
        context.drawString(font, xpText.copy().withStyle(ChatFormatting.GRAY), x + w - font.width(xpText), y, 0xFFFFFF);
        y += 11;
        float progress = next < 0 ? 1F : Math.max(0F, Math.min(1F, (info.experience() - from) / (float) Math.max(1, next - from)));
        context.fill(x, y, x + w, y + 5, 0xFF2A2A2A);
        context.fill(x, y, x + (int) (w * progress), y + 5, 0xFF7FD34E);
        y += 10;
        context.drawString(font, Component.translatable("job.mca_vie.status_label", WorkClient.statusText(info)), x, y, 0xFFFFFF);
        y += 11;
        Component depot = Component.translatable(info.hasDepot() ? "job.mca_vie.has_depot" : "job.mca_vie.no_depot")
                .withStyle(info.hasDepot() ? ChatFormatting.GREEN : ChatFormatting.GOLD);
        for (FormattedCharSequence line : font.split(depot, w)) {
            context.drawString(font, line, x, y, 0xFFFFFF);
            y += 10;
        }
        return y;
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
        if (detailOverflow > 0) {
            detailScroll = Math.max(0, Math.min(detailOverflow, detailScroll - (int) Math.signum(amount) * 10));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
