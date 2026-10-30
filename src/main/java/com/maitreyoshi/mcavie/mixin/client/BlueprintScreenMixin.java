package com.maitreyoshi.mcavie.mixin.client;

import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.village.Decree;
import com.maitreyoshi.mcavie.village.VillageEconomy;
import com.maitreyoshi.mcavie.village.VillageNetwork;
import com.maitreyoshi.mcavie.village.VillageRights;
import com.maitreyoshi.mcavie.village.VillageState;
import com.maitreyoshi.mcavie.village.client.BlueprintPanels;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.mca.client.gui.BlueprintScreen;
import net.mca.resources.Rank;
import net.mca.resources.data.BuildingType;
import net.mca.resources.data.tasks.Task;
import net.mca.server.world.data.Building;
import net.mca.server.world.data.Village;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Mixin(BlueprintScreen.class)
public abstract class BlueprintScreenMixin extends Screen {
    @Unique
    private static final String TREASURY = "treasury";

    @Shadow(remap = false)
    private Village village;
    @Shadow(remap = false)
    private String page;
    @Shadow(remap = false)
    private BuildingType selectedBuilding;
    @Shadow(remap = false)
    private Rank rank;
    @Shadow(remap = false)
    private int reputation;
    @Shadow(remap = false)
    private Set<String> completedTasks;
    @Shadow(remap = false)
    private Map<Rank, List<Task>> tasks;

    @Unique
    private static final String DECREES = "decrees";
    @Unique
    private static final String MARKET = "market";
    @Unique
    private static final int PRODUCTS_PER_PAGE = 12;
    @Unique
    private static final String REGISTRY = "registry";
    @Unique
    private static final String JUSTICE = "justice";
    @Unique
    private int mcavie$registryFilter = -1;
    @Unique
    private int mcavie$registryPage;

    @Shadow(remap = false)
    private int pageNumber;

    @Unique
    private int mcavie$marketPage;
    @Unique
    private ButtonWidget mcavie$treasuryTab;
    @Unique
    private final java.util.Map<Decree, ButtonWidget> mcavie$decreeButtons = new java.util.EnumMap<>(Decree.class);

    protected BlueprintScreenMixin(Text title) {
        super(title);
    }

    @Shadow(remap = false)
    private void saveVillage() {
        throw new AssertionError();
    }

    @Shadow(remap = false)
    private void setPage(String page) {
        throw new AssertionError();
    }

    @Inject(method = "setVillage", at = @At("TAIL"), remap = false)
    private void mcavie$refreshPage(Village newVillage, CallbackInfo ci) {
        if (JUSTICE.equals(page) || MARKET.equals(page)) {
            setPage(page);
        }
    }

    @Inject(method = "changeTaxes", at = @At("HEAD"), cancellable = true, remap = false)
    private void mcavie$taxStep(float delta, CallbackInfo ci) {
        ci.cancel();
        int step = (delta > 0 ? 1 : -1) * (hasShiftDown() ? 5 : 1);
        int percent = Math.round(village.getTaxes() * 100) + step;
        int max = Math.round(VillageRights.maxTaxes(VillageState.of(village).rank) * 100);
        village.setTaxes(Math.max(0, Math.min(max, percent)) / 100f);
        saveVillage();
    }

    @Inject(method = "setPage", at = @At("TAIL"), remap = false)
    private void mcavie$addTreasuryTab(String newPage, CallbackInfo ci) {
        if (page == null || page.equals("rename") || page.equals("empty") || page.equals("waiting") || village == null) {
            return;
        }
        if (mcavie$treasuryTab != null && children().contains(mcavie$treasuryTab)) {
            return;
        }
        int bx = width / 2 - 180;
        int by = height / 2 - 56 + 6 * 22;
        mcavie$treasuryTab = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mca_vie.treasury.tab"), b -> setPage(TREASURY))
                .dimensions(bx, by, 80, 20).build());
        mcavie$treasuryTab.active = !page.equals(TREASURY);
        ButtonWidget decreesTab = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mca_vie.decree.tab"), b -> setPage(DECREES))
                .dimensions(bx, by + 22, 80, 20).build());
        decreesTab.active = !page.equals(DECREES);
        ButtonWidget marketTab = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mca_vie.market.tab"), b -> setPage(MARKET))
                .dimensions(bx, by + 44, 80, 20).build());
        marketTab.active = !page.equals(MARKET);
        ButtonWidget registryTab = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mca_vie.registry.tab"), b -> setPage(REGISTRY))
                .dimensions(bx, by + 66, 80, 20).build());
        registryTab.active = !page.equals(REGISTRY);
        ButtonWidget justiceTab = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mca_vie.justice.tab"), b -> setPage(JUSTICE))
                .dimensions(bx, by + 88, 80, 20).build());
        justiceTab.active = !page.equals(JUSTICE);
        mcavie$layoutTabs(bx);
        if (page.equals(MARKET)) {
            mcavie$addMarketWidgets();
        }
        if (page.equals(REGISTRY)) {
            mcavie$addRegistryWidgets();
        }
        if (page.equals(JUSTICE)) {
            mcavie$addJusticeWidgets();
        }

        mcavie$decreeButtons.clear();
        if (page.equals(DECREES)) {
            int y = height / 2 - 80;
            for (Decree decree : Decree.values()) {
                ButtonWidget button = addDrawableChild(ButtonWidget.builder(decree.getName(), b -> mcavie$launch(decree))
                        .dimensions(width / 2 - 92, y, 124, 20)
                        .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(decree.getDescription()))
                        .build());
                mcavie$decreeButtons.put(decree, button);
                y += 22;
            }
        }

        if (page.equals(TREASURY) && rank != null && rank.isAtLeast(VillageRights.WITHDRAW)) {
            int x = width / 2 + 60;
            int y = height / 2 + 62;
            addDrawableChild(ButtonWidget.builder(Text.literal("1"), b -> mcavie$withdraw(1)).dimensions(x, y, 24, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("10"), b -> mcavie$withdraw(10)).dimensions(x + 26, y, 28, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mca_vie.treasury.all"), b ->
                    mcavie$withdraw((int) Math.min(Integer.MAX_VALUE, VillageEconomy.availableShare(VillageState.of(village))))).dimensions(x + 56, y, 50, 20).build());
        }
        if (page.equals(TREASURY) && rank != null && rank.isAtLeast(VillageRights.DEPOSIT)) {
            int x = width / 2 + 60;
            int y = height / 2 + 84;
            addDrawableChild(ButtonWidget.builder(Text.literal("1"), b -> mcavie$send(VillageNetwork.DEPOSIT, 1)).dimensions(x, y, 24, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("10"), b -> mcavie$send(VillageNetwork.DEPOSIT, 10)).dimensions(x + 26, y, 28, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mca_vie.treasury.all"), b -> mcavie$send(VillageNetwork.DEPOSIT, 64 * 36))
                    .dimensions(x + 56, y, 50, 20).build());
        }
        if (page.equals("rules")) {
            boolean allowed = rank != null && rank.isAtLeast(VillageRights.SET_TAXES);
            int y = height / 2 - 50;
            ButtonWidget less = addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> mcavie$send(VillageNetwork.NATURE, -1))
                    .dimensions(width / 2 + 80, y, 20, 20).build());
            ButtonWidget more = addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> mcavie$send(VillageNetwork.NATURE, 1))
                    .dimensions(width / 2 + 150, y, 20, 20).build());
            less.active = allowed;
            more.active = allowed;
        }
    }

    @Unique
    private void mcavie$launch(Decree decree) {
        if (village == null) {
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(village.getId());
        buf.writeInt(decree.ordinal());
        ClientPlayNetworking.send(VillageNetwork.DECREE, buf);
    }

    @Unique
    private void mcavie$layoutTabs(int bx) {
        List<ButtonWidget> tabs = children().stream()
                .filter(c -> c instanceof ButtonWidget w && w.getX() == bx && w.getWidth() == 80)
                .map(c -> (ButtonWidget) c)
                .sorted(java.util.Comparator.comparingInt(ButtonWidget::getY))
                .toList();
        int step = Math.max(14, Math.min(22, (height - 30) / Math.max(1, tabs.size())));
        int y = Math.max(28, (height - tabs.size() * step) / 2);
        for (ButtonWidget tab : tabs) {
            tab.setY(y);
            if (step < 22) {
                ((com.maitreyoshi.mcavie.mixin.client.ClickableWidgetAccessor) tab).mcavie$setHeight(step - 2);
            }
            y += step;
        }
    }

    @Unique
    private void mcavie$addMarketWidgets() {
        List<String> products = BlueprintPanels.marketProducts();
        int pages = Math.max(1, (products.size() + PRODUCTS_PER_PAGE - 1) / PRODUCTS_PER_PAGE);
        mcavie$marketPage = Math.max(0, Math.min(pages - 1, mcavie$marketPage));
        VillageState state = VillageState.of(village);
        for (int i = 0; i < PRODUCTS_PER_PAGE; i++) {
            int index = mcavie$marketPage * PRODUCTS_PER_PAGE + i;
            if (index >= products.size()) {
                break;
            }
            String item = products.get(index);
            int x = i < PRODUCTS_PER_PAGE / 2 ? width / 2 - 92 : width / 2 + 44;
            int y = height / 2 - 62 + (i % (PRODUCTS_PER_PAGE / 2)) * 22;
            ButtonWidget button = addDrawableChild(ButtonWidget.builder(BlueprintPanels.productLabel(item), b -> mcavie$order(item))
                    .dimensions(x, y, 130, 20)
                    .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(BlueprintPanels.productTooltip(state, item)))
                    .build());
            button.active = state.marketActive;
        }
        ButtonWidget previous = addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
            mcavie$marketPage--;
            setPage(MARKET);
        }).dimensions(width / 2 - 92, height / 2 + 72, 20, 20).build());
        previous.active = mcavie$marketPage > 0;
        ButtonWidget next = addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
            mcavie$marketPage++;
            setPage(MARKET);
        }).dimensions(width / 2 - 70, height / 2 + 72, 20, 20).build());
        next.active = mcavie$marketPage < pages - 1;
        ButtonWidget collect = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mca_vie.market.collect"), b -> {
            if (village != null) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeInt(village.getId());
                ClientPlayNetworking.send(VillageNetwork.COLLECT, buf);
            }
        }).dimensions(width / 2 + 94, height / 2 + 72, 80, 20).build());
        collect.active = client != null && client.player != null
                && state.orders.stream().anyMatch(o -> o.ready && o.player.equals(client.player.getUuid()));
    }

    @Unique
    private void mcavie$addJusticeWidgets() {
        VillageState state = VillageState.of(village);
        boolean judge = rank != null && rank.isAtLeast(VillageRights.JUSTICE);
        int y = height / 2 - 62;
        for (int i = 0; i < Math.min(BlueprintPanels.JUSTICE_ROWS, state.cases.size()); i++) {
            java.util.UUID suspect = state.cases.get(i).suspect;
            ButtonWidget prison = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mca_vie.justice.prison"), b -> mcavie$judge(suspect, 0))
                    .dimensions(width / 2 + 88, y, 44, 20).build());
            ButtonWidget pardon = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mca_vie.justice.pardon"), b -> mcavie$judge(suspect, 1))
                    .dimensions(width / 2 + 134, y, 44, 20).build());
            prison.active = judge && state.prisonActive;
            pardon.active = judge;
            y += 22;
        }
    }

    @Unique
    private void mcavie$judge(java.util.UUID suspect, int verdict) {
        if (village == null) {
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(village.getId());
        buf.writeUuid(suspect);
        buf.writeInt(verdict);
        ClientPlayNetworking.send(VillageNetwork.JUDGE, buf);
    }

    @Unique
    private void mcavie$addRegistryWidgets() {
        for (int i = -1; i < 3; i++) {
            int filter = i;
            ButtonWidget button = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mca_vie.registry.filter." + (filter + 1)), b -> {
                mcavie$registryFilter = filter;
                mcavie$registryPage = 0;
                setPage(REGISTRY);
            }).dimensions(width / 2 - 92 + (i + 1) * 66, height / 2 - 84, 64, 20).build());
            button.active = mcavie$registryFilter != filter;
        }
        int pages = BlueprintPanels.registryPages(village, mcavie$registryFilter);
        mcavie$registryPage = Math.max(0, Math.min(pages - 1, mcavie$registryPage));
        ButtonWidget previous = addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
            mcavie$registryPage--;
            setPage(REGISTRY);
        }).dimensions(width / 2 - 92, height / 2 + 72, 20, 20).build());
        previous.active = mcavie$registryPage > 0;
        ButtonWidget next = addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
            mcavie$registryPage++;
            setPage(REGISTRY);
        }).dimensions(width / 2 - 70, height / 2 + 72, 20, 20).build());
        next.active = mcavie$registryPage < pages - 1;
    }

    @Unique
    private void mcavie$order(String item) {
        if (village == null) {
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(village.getId());
        buf.writeString(item, 128);
        buf.writeInt(hasShiftDown() ? 5 : 1);
        ClientPlayNetworking.send(VillageNetwork.ORDER, buf);
    }

    @Unique
    private void mcavie$send(net.minecraft.util.Identifier channel, int value) {
        if (village == null) {
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(village.getId());
        buf.writeInt(value);
        ClientPlayNetworking.send(channel, buf);
    }

    @Unique
    private void mcavie$withdraw(int amount) {
        if (village == null || amount <= 0) {
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(village.getId());
        buf.writeInt(amount);
        ClientPlayNetworking.send(VillageNetwork.WITHDRAW, buf);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void mcavie$render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (village == null || page == null) {
            return;
        }
        switch (page) {
            case "rank" -> {
                int left = width / 2 - 92;
                int right = width / 2 + 36;
                BlueprintPanels.renderPersonal(context, textRenderer, village, rank, reputation,
                        tasks == null || rank == null ? null : tasks.get(rank.promote()), completedTasks, left, height / 2 - 80, right - left - 8);
                BlueprintPanels.renderRank(context, textRenderer, village, right, height / 2 - 80, BlueprintPanels.columnWidth(width, right, 180));
            }
            case "map", "advanced" -> BlueprintPanels.renderMapHeader(context, textRenderer, village, width / 2, height / 2 - 78);
            case "catalog" -> {
                if (selectedBuilding != null) {
                    BlueprintPanels.renderCatalogInfo(context, textRenderer, selectedBuilding, width / 2 + 35, height / 2 + 70);
                }
            }
            case "rules" -> {
                BlueprintPanels.renderTaxCap(context, textRenderer, village, width / 2, height / 2 + 78, 180);
                BlueprintPanels.renderNature(context, textRenderer, village, width / 2 + 125, height / 2 - 60, 110);
            }
            case DECREES -> {
                long today = client != null && client.world != null ? WorldCalendar.now(client.world).dayIndex() : 0;
                BlueprintPanels.renderDecrees(context, textRenderer, village, rank, today, mcavie$decreeButtons, width / 2 + 38, height / 2 - 80);
            }
            case "villagers" -> BlueprintPanels.renderTaxRoll(context, textRenderer, village, pageNumber, width / 2 + 56, height / 2 - 51);
            case MARKET -> BlueprintPanels.renderMarket(context, textRenderer, village, client == null || client.player == null ? null : client.player.getUuid(),
                    width / 2 + 41, height / 2 - 88, width / 2 - 46, height / 2 + 78);
            case REGISTRY -> {
                long today = client != null && client.world != null ? WorldCalendar.now(client.world).dayIndex() : 0;
                BlueprintPanels.renderRegistry(context, textRenderer, village, mcavie$registryFilter, mcavie$registryPage, today,
                        width / 2 - 92, height / 2 - 58, width / 2 + 180 - (width / 2 - 92));
            }
            case JUSTICE -> {
                long today = client != null && client.world != null ? WorldCalendar.now(client.world).dayIndex() : 0;
                BlueprintPanels.renderJustice(context, textRenderer, village, rank, today, width / 2 - 92, height / 2 - 84, width / 2 + 84 - (width / 2 - 92));
            }
            case TREASURY -> BlueprintPanels.renderTreasury(context, textRenderer, village, rank, width / 2 - 85, height / 2 - 80);
            default -> {
            }
        }
    }

    @Inject(method = "renderStats", at = @At("HEAD"), cancellable = true, remap = false)
    private void mcavie$hideStats(DrawContext context, CallbackInfo ci) {
        if ("rank".equals(page)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderTasks", at = @At("HEAD"), cancellable = true, remap = false)
    private void mcavie$hideTasks(DrawContext context, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "getBuildingTooltip", at = @At("RETURN"), remap = false)
    private void mcavie$tooltip(Building building, CallbackInfoReturnable<List<Text>> cir) {
        if (village != null) {
            BlueprintPanels.appendTooltip(cir.getReturnValue(), village, building);
        }
    }
}
