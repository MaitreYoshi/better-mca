package com.maitreyoshi.mcavie.village.client;

import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.tree.client.TreeTexts;
import com.maitreyoshi.mcavie.village.CivilRegistry;
import com.maitreyoshi.mcavie.village.Decree;
import com.maitreyoshi.mcavie.village.Prestige;
import com.maitreyoshi.mcavie.village.VillageConfig;
import com.maitreyoshi.mcavie.village.VillageDecrees;
import com.maitreyoshi.mcavie.village.VillageEconomy;
import com.maitreyoshi.mcavie.village.VillageNetwork;
import com.maitreyoshi.mcavie.village.VillageRights;
import com.maitreyoshi.mcavie.village.VillageState;
import com.maitreyoshi.mcavie.work.Job;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.mca.client.gui.BlueprintScreen;
import net.mca.client.gui.FamilyTreeScreen;
import net.mca.client.gui.widget.WidgetUtils;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.network.c2s.GetVillageRequest;
import net.mca.network.c2s.RenameVillageMessage;
import net.mca.network.c2s.ReportBuildingMessage;
import net.mca.network.c2s.SaveVillageMessage;
import net.mca.resources.BuildingTypes;
import net.mca.resources.Rank;
import net.mca.resources.data.BuildingType;
import net.mca.resources.data.tasks.Task;
import net.mca.server.world.data.Building;
import net.mca.server.world.data.Village;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Plan du village de Better MCA. Remplace l'écran de MCA à l'ouverture (même canal de données :
 * setVillage / setVillageData) et dessine tout dans un cadre unique qui s'adapte à la taille de l'écran.
 */
public class PlanScreen extends BlueprintScreen {
    private static final Identifier ICONS = new Identifier("mca", "textures/buildings.png");

    private static final int WHITE = 0xffffffff;
    private static final int LABEL = 0xffcccccc;
    private static final int GRAY = 0xffaaaaaa;
    private static final int DIM = 0xff777777;
    private static final int GOLD = 0xffffd27f;
    private static final int GREEN = 0xff88ff88;
    private static final int RED = 0xffff6666;

    private static final int LINE = 11;
    private static final int HEADER = 20;
    private static final int SIDEBAR = 84;
    private static final int FOOTER = 13;
    private static final int PAD = 6;

    enum Page {
        MAP(0), PRESTIGE(0), CATALOG(0), VILLAGERS(0),
        RULES(1), TREASURY(1), DECREES(1), MARKET(1),
        REGISTRY(2), JUSTICE(2),
        TITLE(3);

        final int group;

        Page(int group) {
            this.group = group;
        }

        Text label() {
            return Text.translatable("gui.mca_vie.plan.page." + name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    private static final String[] GROUPS = {"village", "manage", "civic", "me"};

    private Village village;
    private Rank rank;
    private int reputation;
    private boolean isVillage;
    private Set<String> completedTasks;
    private Map<Rank, List<Task>> tasks;
    private boolean loaded;
    private boolean requested;

    private Page page = Page.MAP;
    private boolean renaming;
    private String renameText;
    private TextFieldWidget renameField;
    private int scroll;
    private int scrollMax;

    private BuildingType selectedBuilding;
    private final Map<BuildingType, ButtonWidget> catalogButtons = new java.util.HashMap<>();
    private int registryFilter = -1;
    private final ButtonWidget[] registryButtons = new ButtonWidget[4];
    private int marketPage;
    private int justicePage;
    private UUID hoveredVillager;

    // cadre, recalculé à chaque construction
    private int fx, fy, fw, fh;
    private int cx, cy, cw, ch;

    public PlanScreen() {
        super();
    }

    // ---- données ----

    @Override
    public void setVillage(Village village) {
        this.village = village;
        this.loaded = true;
        if (client != null) {
            rebuild();
        }
    }

    @Override
    public void setVillageData(Rank rank, int reputation, boolean isVillage, Set<String> completedTasks, Map<Rank, List<Task>> tasks) {
        this.rank = rank;
        this.reputation = reputation;
        this.isVillage = isVillage;
        this.completedTasks = completedTasks;
        this.tasks = tasks;
        if (client != null) {
            rebuild();
        }
    }

    @Override
    public void init() {
        if (!requested) {
            requested = true;
            NetworkHandler.sendToServer(new GetVillageRequest());
        }
        rebuild();
    }

    private boolean allowed(Rank required) {
        return rank != null && rank.isAtLeast(required);
    }

    private static Text locked(Rank required) {
        return Text.translatable("gui.mca_vie.plan.locked", Text.translatable(required.getTranslationKey()));
    }

    private long today() {
        return client != null && client.world != null ? WorldCalendar.now(client.world).dayIndex() : 0;
    }

    private void refreshVillage() {
        NetworkHandler.sendToServer(new GetVillageRequest());
    }

    private void send(Identifier channel, int value) {
        if (village == null) {
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(village.getId());
        buf.writeInt(value);
        ClientPlayNetworking.send(channel, buf);
    }

    // ---- construction des widgets ----

    private void rebuild() {
        if (renameField != null) {
            renameText = renameField.getText();
        }
        clearChildren();
        renameField = null;
        catalogButtons.clear();
        java.util.Arrays.fill(registryButtons, null);

        fw = Math.min(width - 8, 500);
        fh = Math.min(height - 8, 300);
        fx = (width - fw) / 2;
        fy = (height - fh) / 2;
        cx = fx + SIDEBAR + PAD;
        cy = fy + HEADER + PAD;
        cw = fx + fw - PAD - cx;
        ch = fy + fh - FOOTER - 4 - cy;

        if (!loaded) {
            return;
        }
        if (village == null) {
            buildEmpty();
            return;
        }

        // en-tête : renommer, actualiser
        int nameWidth = textRenderer.getWidth(villageName());
        ButtonWidget rename = addDrawableChild(ButtonWidget.builder(Text.literal("✎"), b -> {
            renaming = true;
            renameText = village.getName();
            rebuild();
        }).dimensions(fx + PAD + nameWidth + 4, fy + 3, 14, 14).tooltip(Tooltip.of(allowed(VillageRights.RENAME)
                ? Text.translatable("gui.blueprint.renameVillage") : locked(VillageRights.RENAME))).build());
        rename.active = allowed(VillageRights.RENAME);
        rename.visible = isVillage;
        ButtonWidget refresh = addDrawableChild(ButtonWidget.builder(Text.literal("⟳"), b -> {
            NetworkHandler.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.FULL_SCAN));
            refreshVillage();
        }).dimensions(fx + fw - 20, fy + 3, 16, 14).tooltip(Tooltip.of(allowed(VillageRights.RIGHTS)
                ? Text.translatable("gui.mca_vie.plan.refresh") : locked(VillageRights.RIGHTS))).build());
        refresh.active = allowed(VillageRights.RIGHTS);

        if (renaming) {
            buildRename();
            return;
        }
        switch (page) {
            case MAP -> buildMap();
            case CATALOG -> buildCatalog();
            case RULES -> buildRules();
            case TREASURY -> buildTreasury();
            case DECREES -> buildDecrees();
            case MARKET -> buildMarket();
            case REGISTRY -> buildRegistry();
            case JUSTICE -> buildJustice();
            default -> {
            }
        }
    }

    private ButtonWidget button(Text label, ButtonWidget.PressAction action, int x, int y, int w, int h, Rank required, Text tooltip) {
        boolean ok = required == null || allowed(required);
        ButtonWidget.Builder builder = ButtonWidget.builder(label, action).dimensions(x, y, w, h);
        Text tip = ok ? tooltip : locked(required);
        if (tip != null) {
            builder.tooltip(Tooltip.of(tip));
        }
        ButtonWidget button = addDrawableChild(builder.build());
        button.active = ok;
        return button;
    }

    private void buildEmpty() {
        int x = fx + fw / 2;
        int y = fy + fh / 2 + 4;
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.blueprint.addRoom"), b -> {
            NetworkHandler.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.ADD_ROOM));
            refreshVillage();
            close();
        }).dimensions(x - 102, y, 100, 20).tooltip(Tooltip.of(Text.translatable("gui.blueprint.addRoom.tooltip"))).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.blueprint.addBuilding"), b -> {
            NetworkHandler.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.ADD));
            refreshVillage();
            close();
        }).dimensions(x + 2, y, 100, 20).tooltip(Tooltip.of(Text.translatable("gui.blueprint.addBuilding.tooltip"))).build());
    }

    private void buildRename() {
        int w = Math.min(200, cw);
        renameField = addDrawableChild(new TextFieldWidget(textRenderer, cx, cy + 14, w, 18, Text.translatable("gui.blueprint.renameVillage")));
        renameField.setMaxLength(32);
        renameField.setText(renameText == null ? village.getName() : renameText);
        setInitialFocus(renameField);
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.blueprint.cancel"), b -> {
            renaming = false;
            renameText = null;
            rebuild();
        }).dimensions(cx, cy + 38, w / 2 - 2, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.blueprint.rename"), b -> {
            NetworkHandler.sendToServer(new RenameVillageMessage(village.getId(), renameField.getText()));
            renaming = false;
            renameText = null;
            refreshVillage();
            rebuild();
        }).dimensions(cx + w / 2 + 2, cy + 38, w / 2 - 2, 18).build());
    }

    private int mapSize() {
        return Math.max(60, Math.min(ch, cw - 112));
    }

    private void buildMap() {
        int x = cx + mapSize() + 8;
        int w = cx + cw - x;
        int y = cy;
        Rank need = VillageRights.RIGHTS;
        button(Text.translatable("gui.blueprint.addRoom"), b -> report(ReportBuildingMessage.Action.ADD_ROOM), x, y, w, 18, need,
                Text.translatable("gui.blueprint.addRoom.tooltip"));
        button(Text.translatable("gui.blueprint.addBuilding"), b -> report(ReportBuildingMessage.Action.ADD), x, y + 20, w, 18, need,
                Text.translatable("gui.blueprint.addBuilding.tooltip"));
        button(Text.translatable("gui.blueprint.removeBuilding"), b -> report(ReportBuildingMessage.Action.REMOVE), x, y + 40, w, 18, need,
                Text.translatable("gui.mca_vie.plan.remove.tooltip"));
        button(Text.translatable(village.isAutoScan() ? "gui.mca_vie.plan.autoscan.on" : "gui.mca_vie.plan.autoscan.off"),
                b -> report(ReportBuildingMessage.Action.AUTO_SCAN), x, y + 60, w, 18, need, Text.translatable("gui.blueprint.autoScan.tooltip"));
        button(Text.translatable("gui.blueprint.restrictAccess"), b -> {
            NetworkHandler.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.FORCE_TYPE, "blocked"));
            refreshVillage();
        }, x, y + 80, w, 18, need, Text.translatable("gui.blueprint.restrictAccess.tooltip"));
    }

    private void report(ReportBuildingMessage.Action action) {
        NetworkHandler.sendToServer(new ReportBuildingMessage(action));
        refreshVillage();
    }

    private void buildCatalog() {
        int i = 0;
        for (BuildingType bt : BuildingTypes.getInstance()) {
            if (!bt.visible()) {
                continue;
            }
            int x = cx + (i % 5) * 21;
            int y = cy + (i / 5) * 21;
            if (y + 20 > cy + ch) {
                break;
            }
            TexturedButtonWidget widget = new TexturedButtonWidget(x, y, 20, 20, bt.iconU(), bt.iconV() + 20, 20, ICONS, 256, 256, b -> {
                selectedBuilding = bt;
                scroll = 0;
            }, Text.translatable("buildingType." + bt.name()));
            widget.setTooltip(Tooltip.of(Text.translatable("buildingType." + bt.name())));
            catalogButtons.put(bt, addDrawableChild(widget));
            i++;
        }
    }

    private void buildRules() {
        int right = cx + cw;
        Rank[] required = rulesRequired();
        for (int i = 0; i < 4; i++) {
            int y = cy + i * rulesRow() - 4;
            int row = i;
            Text tip = row < 3 ? Text.translatable("gui.mca_vie.plan.rules.step") : null;
            button(Text.literal("-"), b -> changeRule(row, -1), right - 76, y, 18, 16, required[i], tip);
            button(Text.literal("+"), b -> changeRule(row, 1), right - 18, y, 18, 16, required[i], tip);
        }
    }

    private int rulesRow() {
        return Math.max(40, Math.min(48, ch / 4));
    }

    private static Rank[] rulesRequired() {
        return new Rank[]{VillageRights.SET_TAXES, VillageRights.SET_BIRTHS, VillageRights.SET_MARRIAGES, VillageRights.SET_TAXES};
    }

    private int[] ruleValues() {
        return new int[]{Math.round(village.getTaxes() * 100), Math.round(village.getPopulationThreshold() * 100),
                Math.round(village.getMarriageThreshold() * 100), VillageState.of(village).natureShare};
    }

    private void changeRule(int row, int direction) {
        if (row == 3) {
            send(VillageNetwork.NATURE, direction);
            return;
        }
        int step = hasShiftDown() ? 1 : 5;
        int value = ruleValues()[row] + direction * step;
        if (row == 0) {
            int max = Math.round(VillageRights.maxTaxes(VillageState.of(village).rank) * 100);
            village.setTaxes(Math.max(0, Math.min(max, value)) / 100f);
        } else if (row == 1) {
            village.setPopulationThreshold(Math.max(0, Math.min(100, value)) / 100f);
        } else {
            village.setMarriageThreshold(Math.max(0, Math.min(100, value)) / 100f);
        }
        NetworkHandler.sendToServer(new SaveVillageMessage(village));
        refreshVillage();
    }

    private boolean treasuryActions() {
        VillageState state = VillageState.of(village);
        return allowed(VillageRights.VIEW_TREASURY) && state.economyActive;
    }

    private void buildTreasury() {
        if (!treasuryActions()) {
            return;
        }
        int labelW = 50;
        int y = cy + ch - 38;
        int x = cx + labelW;
        // la part retirable se calcule à la levée du dimanche ; sans elle, on explique au lieu de laisser cliquer dans le vide
        boolean nothing = VillageEconomy.availableShare(VillageState.of(village)) <= 0;
        Text empty = nothing ? withdrawNone() : null;
        ButtonWidget[] withdraws = {
                button(Text.literal("1"), b -> withdraw(1), x, y, 30, 18, VillageRights.WITHDRAW, empty),
                button(Text.literal("10"), b -> withdraw(10), x + 32, y, 30, 18, VillageRights.WITHDRAW, empty),
                button(Text.translatable("gui.mca_vie.treasury.all"), b -> withdraw((int) Math.min(Integer.MAX_VALUE,
                        VillageEconomy.availableShare(VillageState.of(village)))), x + 64, y, 44, 18, VillageRights.WITHDRAW, empty)};
        if (nothing) {
            for (ButtonWidget w : withdraws) {
                w.active = false;
            }
        }
        y += 20;
        button(Text.literal("1"), b -> send(VillageNetwork.DEPOSIT, 1), x, y, 30, 18, VillageRights.DEPOSIT, null);
        button(Text.literal("10"), b -> send(VillageNetwork.DEPOSIT, 10), x + 32, y, 30, 18, VillageRights.DEPOSIT, null);
        button(Text.translatable("gui.mca_vie.treasury.all"), b -> send(VillageNetwork.DEPOSIT, 64 * 36), x + 64, y, 44, 18, VillageRights.DEPOSIT,
                Text.translatable("gui.mca_vie.plan.deposit_all"));
    }

    private static Text withdrawNone() {
        return Text.translatable("gui.mca_vie.plan.withdraw_none", com.maitreyoshi.mcavie.village.VillageConfig.get().playerSharePercent);
    }

    private void withdraw(int amount) {
        if (amount > 0) {
            send(VillageNetwork.WITHDRAW, amount);
        }
    }

    private int decreeButtonWidth() {
        return Math.min(140, cw / 2 - 4);
    }

    private void buildDecrees() {
        long today = today();
        VillageState state = VillageState.of(village);
        int y = cy;
        for (Decree decree : Decree.values()) {
            MutableText reason = VillageDecrees.unavailable(village, decree, today);
            boolean active = state.activeDecrees.contains(decree);
            Text label = decree.kind == Decree.Kind.TOGGLE && active ? Text.translatable("gui.mca_vie.decree.stop", decree.getName()) : decree.getName();
            ButtonWidget b = button(label, w -> launch(decree), cx, y, decreeButtonWidth(), 19, VillageRights.DECREES, decree.getDescription());
            if (reason != null) {
                b.active = false;
            }
            y += 21;
        }
    }

    private void launch(Decree decree) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(village.getId());
        buf.writeInt(decree.ordinal());
        ClientPlayNetworking.send(VillageNetwork.DECREE, buf);
    }

    private int marketPerPage() {
        return Math.max(2, (ch - 36 - 22) / 20 * 2);
    }

    private void buildMarket() {
        VillageState state = VillageState.of(village);
        List<String> products = BlueprintPanels.marketProducts();
        int perPage = marketPerPage();
        int pages = Math.max(1, (products.size() + perPage - 1) / perPage);
        marketPage = Math.max(0, Math.min(pages - 1, marketPage));
        int colW = (cw - 4) / 2;
        Text inactive = Text.translatable("gui.mca_vie.market.inactive");
        for (int i = 0; i < perPage; i++) {
            int index = marketPage * perPage + i;
            if (index >= products.size()) {
                break;
            }
            String item = products.get(index);
            int x = cx + (i % 2) * (colW + 4);
            int y = cy + 36 + (i / 2) * 20;
            ButtonWidget b = addDrawableChild(ButtonWidget.builder(BlueprintPanels.productLabel(item), w -> order(item))
                    .dimensions(x, y, colW, 18)
                    .tooltip(Tooltip.of(state.marketActive ? BlueprintPanels.productTooltip(state, item) : inactive)).build());
            b.active = state.marketActive;
        }
        int y = cy + ch - 18;
        ButtonWidget prev = addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
            marketPage--;
            rebuild();
        }).dimensions(cx, y, 18, 18).build());
        prev.active = marketPage > 0;
        ButtonWidget next = addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
            marketPage++;
            rebuild();
        }).dimensions(cx + 52, y, 18, 18).build());
        next.active = marketPage < pages - 1;
        ButtonWidget collect = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mca_vie.market.collect"), b -> {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeInt(village.getId());
            ClientPlayNetworking.send(VillageNetwork.COLLECT, buf);
        }).dimensions(cx + cw - 80, y, 80, 18).tooltip(Tooltip.of(Text.translatable("gui.mca_vie.plan.collect.tooltip"))).build());
        collect.active = client != null && client.player != null
                && state.orders.stream().anyMatch(o -> o.ready && o.player.equals(client.player.getUuid()));
    }

    private int marketPages() {
        return Math.max(1, (BlueprintPanels.marketProducts().size() + marketPerPage() - 1) / marketPerPage());
    }

    private void order(String item) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(village.getId());
        buf.writeString(item, 128);
        buf.writeInt(hasShiftDown() ? 5 : 1);
        ClientPlayNetworking.send(VillageNetwork.ORDER, buf);
    }

    private void buildRegistry() {
        int w = (cw - 6) / 4;
        for (int i = -1; i < 3; i++) {
            int filter = i;
            ButtonWidget b = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mca_vie.registry.filter." + (filter + 1)), w2 -> {
                registryFilter = filter;
                scroll = 0;
                rebuild();
            }).dimensions(cx + (i + 1) * (w + 2), cy, w, 16).build());
            registryButtons[i + 1] = b;
        }
    }

    private int justiceRows() {
        return Math.max(1, Math.min(4, (ch - 36 - 44) / 22));
    }

    private void buildJustice() {
        VillageState state = VillageState.of(village);
        int rows = justiceRows();
        int pages = Math.max(1, (state.cases.size() + rows - 1) / rows);
        justicePage = Math.max(0, Math.min(pages - 1, justicePage));
        int y = cy + 36;
        Text noPrison = Text.translatable("gui.mca_vie.plan.justice.no_prison");
        for (int i = 0; i < rows; i++) {
            int index = justicePage * rows + i;
            if (index >= state.cases.size()) {
                break;
            }
            UUID suspect = state.cases.get(index).suspect;
            ButtonWidget prison = button(Text.translatable("gui.mca_vie.justice.prison"), b -> judge(suspect, 0), cx + cw - 92, y, 45, 18,
                    VillageRights.JUSTICE, state.prisonActive ? null : noPrison);
            if (!state.prisonActive) {
                prison.active = false;
            }
            button(Text.translatable("gui.mca_vie.justice.pardon"), b -> judge(suspect, 1), cx + cw - 45, y, 45, 18, VillageRights.JUSTICE, null);
            y += 22;
        }
        if (pages > 1) {
            int py = cy + 22;
            ButtonWidget prev = addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
                justicePage--;
                rebuild();
            }).dimensions(cx + cw - 38, py, 18, 12).build());
            prev.active = justicePage > 0;
            ButtonWidget next = addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
                justicePage++;
                rebuild();
            }).dimensions(cx + cw - 18, py, 18, 12).build());
            next.active = justicePage < pages - 1;
        }
    }

    private void judge(UUID suspect, int verdict) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(village.getId());
        buf.writeUuid(suspect);
        buf.writeInt(verdict);
        ClientPlayNetworking.send(VillageNetwork.JUDGE, buf);
    }

    // ---- rendu ----

    private Text villageName() {
        if (village == null) {
            return Text.empty();
        }
        return isVillage ? Text.literal(village.getName()) : Text.translatable("gui.blueprint.settlement");
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.fill(fx, fy, fx + fw, fy + fh, 0xe0101014);
        context.drawBorder(fx, fy, fw, fh, 0x60ffffff);

        Text footer = null;
        if (!loaded) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.mca_vie.plan.waiting"), fx + fw / 2, fy + fh / 2 - 4, GRAY);
        } else if (village == null) {
            BlueprintPanels.wrapped(context, textRenderer, Text.translatable("gui.blueprint.empty"), fx + 20, fy + fh / 2 - 26, fw - 40, WHITE);
        } else {
            renderHeader(context);
            renderSidebar(context, mouseX, mouseY);
            hoveredVillager = null;
            if (renaming) {
                context.drawTextWithShadow(textRenderer, Text.translatable("gui.blueprint.renameVillage"), cx, cy, WHITE);
            } else {
                footer = renderPage(context, mouseX, mouseY);
            }
        }

        for (Element child : children()) {
            if (child instanceof Drawable drawable) {
                drawable.render(context, mouseX, mouseY, delta);
            }
        }
        if (village != null && !renaming) {
            renderOverlays(context, mouseX, mouseY);
        }
        if (footer != null) {
            renderFooter(context, footer, mouseX, mouseY);
        }
    }

    private void renderHeader(DrawContext context) {
        context.fill(fx + 1, fy + 1, fx + fw - 1, fy + HEADER, 0x40ffffff);
        Text name = villageName();
        context.drawTextWithShadow(textRenderer, name, fx + PAD, fy + 6, WHITE);
        VillageState state = VillageState.of(village);
        Text title = rank == null ? Text.literal("?") : Text.translatable(rank.getTranslationKey());
        Text info = Text.translatable("gui.mca_vie.plan.header",
                state.rank.getName().copy().formatted(Formatting.GOLD),
                title.copy().formatted(Formatting.GOLD),
                Text.literal(String.valueOf(reputation)).formatted(Formatting.GOLD)).formatted(Formatting.GRAY);
        int left = fx + PAD + textRenderer.getWidth(name) + (isVillage ? 24 : 8);
        int right = fx + fw - 26;
        int available = right - left;
        if (available > 20) {
            OrderedText line = Language.getInstance().reorder(textRenderer.trimToWidth(info, available));
            context.drawTextWithShadow(textRenderer, line, right - textRenderer.getWidth(line), fy + 6, GRAY);
        }
    }

    private int tabY(Page target) {
        int y = fy + HEADER + 4;
        int group = -1;
        for (Page p : Page.values()) {
            if (p.group != group) {
                group = p.group;
                y += 11;
            }
            if (p == target) {
                return y;
            }
            y += 12;
        }
        return y;
    }

    private boolean tabLocked(Page p) {
        return p == Page.TREASURY && !allowed(VillageRights.VIEW_TREASURY);
    }

    private void renderSidebar(DrawContext context, int mouseX, int mouseY) {
        context.fill(fx + 1, fy + HEADER, fx + SIDEBAR, fy + fh - 1, 0x30000000);
        context.fill(fx + SIDEBAR, fy + HEADER, fx + SIDEBAR + 1, fy + fh - 1, 0x40ffffff);
        int group = -1;
        for (Page p : Page.values()) {
            int y = tabY(p);
            if (p.group != group) {
                group = p.group;
                context.drawTextWithShadow(textRenderer, Text.translatable("gui.mca_vie.plan.group." + GROUPS[group]), fx + 5, y - 10, DIM);
            }
            boolean selected = p == page && !renaming;
            boolean hover = mouseX >= fx + 2 && mouseX < fx + SIDEBAR - 1 && mouseY >= y - 2 && mouseY < y + 10;
            if (selected) {
                context.fill(fx + 2, y - 2, fx + SIDEBAR - 1, y + 10, 0x60ffd27f);
                context.fill(fx + 2, y - 2, fx + 4, y + 10, GOLD);
            } else if (hover) {
                context.fill(fx + 2, y - 2, fx + SIDEBAR - 1, y + 10, 0x30ffffff);
            }
            int color = selected ? WHITE : tabLocked(p) ? DIM : LABEL;
            context.drawTextWithShadow(textRenderer, Language.getInstance().reorder(textRenderer.trimToWidth(p.label(), SIDEBAR - 16)), fx + 10, y, color);
        }
    }

    private Page tabAt(double mouseX, double mouseY) {
        if (village == null || mouseX < fx + 2 || mouseX >= fx + SIDEBAR - 1) {
            return null;
        }
        for (Page p : Page.values()) {
            int y = tabY(p);
            if (mouseY >= y - 2 && mouseY < y + 10) {
                return p;
            }
        }
        return null;
    }

    /** Ligne d'aide : gris par défaut, le texte stylé (rouge pour un blocage) garde sa couleur. */
    private void renderFooter(DrawContext context, Text footer, int mouseX, int mouseY) {
        int color = GRAY;
        int y = fy + fh - FOOTER + 1;
        context.fill(cx - 2, y - 3, cx + cw + 2, y - 2, 0x30ffffff);
        boolean trimmed = textRenderer.getWidth(footer) > cw;
        context.drawTextWithShadow(textRenderer, Language.getInstance().reorder(textRenderer.trimToWidth(footer, cw)), cx, y, color);
        if (trimmed && mouseX >= cx && mouseX < cx + cw && mouseY >= y - 1 && mouseY < y + 10) {
            context.drawOrderedTooltip(textRenderer, textRenderer.wrapLines(footer, 220), mouseX, mouseY);
        }
    }

    /** Zone de texte qui défile à la molette, découpée au cadre. */
    private int beginScroll(DrawContext context, int top) {
        context.enableScissor(cx - 1, top, cx + cw + 1, cy + ch);
        return top - scroll;
    }

    private void endScroll(DrawContext context, int top, int startY, int endY) {
        context.disableScissor();
        int area = cy + ch - top;
        int content = endY - startY;
        scrollMax = Math.max(0, content - area);
        scroll = Math.max(0, Math.min(scroll, scrollMax));
        if (scrollMax > 0) {
            int barH = Math.max(10, area * area / content);
            int barY = top + (area - barH) * scroll / scrollMax;
            context.fill(cx + cw - 2, top, cx + cw, top + area, 0x30ffffff);
            context.fill(cx + cw - 2, barY, cx + cw, barY + barH, 0xa0ffffff);
        }
    }

    private Text renderPage(DrawContext context, int mouseX, int mouseY) {
        return switch (page) {
            case MAP -> renderMap(context, mouseX, mouseY);
            case PRESTIGE -> {
                int y = beginScroll(context, cy);
                int end = BlueprintPanels.renderRank(context, textRenderer, village, cx, y, cw - 6);
                endScroll(context, cy, y, end);
                yield scrollMax > 0 ? Text.translatable("gui.mca_vie.plan.hint.scroll") : Text.translatable("gui.mca_vie.plan.hint.prestige");
            }
            case CATALOG -> renderCatalog(context);
            case VILLAGERS -> renderVillagers(context, mouseX, mouseY);
            case RULES -> renderRules(context);
            case TREASURY -> renderTreasury(context);
            case DECREES -> renderDecrees(context);
            case MARKET -> renderMarket(context);
            case REGISTRY -> renderRegistry(context, mouseX, mouseY);
            case JUSTICE -> renderJustice(context);
            case TITLE -> {
                int y = beginScroll(context, cy);
                int end = BlueprintPanels.renderPersonal(context, textRenderer, rank, reputation,
                        tasks == null || rank == null ? null : tasks.get(rank.promote()), completedTasks, cx, y, cw - 6);
                endScroll(context, cy, y, end);
                yield Text.translatable("gui.mca_vie.plan.hint.title");
            }
        };
    }

    private void renderOverlays(DrawContext context, int mouseX, int mouseY) {
        if (page == Page.CATALOG && selectedBuilding != null) {
            ButtonWidget b = catalogButtons.get(selectedBuilding);
            if (b != null) {
                context.drawBorder(b.getX() - 1, b.getY() - 1, b.getWidth() + 2, b.getHeight() + 2, GOLD);
            }
        }
        if (page == Page.REGISTRY) {
            ButtonWidget b = registryButtons[registryFilter + 1];
            if (b != null) {
                context.drawBorder(b.getX() - 1, b.getY() - 1, b.getWidth() + 2, b.getHeight() + 2, GOLD);
            }
        }
        if (page == Page.MAP) {
            renderMapTooltips(context, mouseX, mouseY);
        }
    }

    // carte

    private Text renderMap(DrawContext context, int mouseX, int mouseY) {
        int size = mapSize();
        WidgetUtils.drawRectangle(context, cx, cy, cx + size, cy + size, 0x60ffffff);
        context.enableScissor(cx, cy, cx + size, cy + size);
        int half = size / 2;
        float sc = Math.min((float) half / (village.getBox().getMaxBlockCount() + 3) * 2, 2.0f);
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(cx + half, cy + half, 0);
        matrices.scale(sc, sc, 1.0f);
        matrices.translate(-village.getCenter().getX(), -village.getCenter().getZ(), 0);
        if (client != null && client.player != null) {
            int px = (int) client.player.getX();
            int pz = (int) client.player.getZ();
            WidgetUtils.drawRectangle(context, px - 1, pz - 1, px + 1, pz + 1, 0xffff00ff);
        }
        for (Building building : village.getBuildings().values()) {
            if (!building.isComplete()) {
                continue;
            }
            BuildingType bt = building.getBuildingType();
            BlockPos c = building.getCenter();
            if (!bt.isIcon()) {
                BlockPos p0 = building.getPos0();
                BlockPos p1 = building.getPos1();
                WidgetUtils.drawRectangle(context, p0.getX(), p0.getZ(), p1.getX(), p1.getZ(), bt.getColor());
            }
            if (bt.isIcon() || bt.visible()) {
                drawBuildingIcon(context, ICONS, c.getX(), c.getZ(), bt.iconU(), bt.iconV());
            }
        }
        matrices.pop();
        context.disableScissor();

        int x = cx + size + 8;
        int w = cx + cw - x;
        int y = cy + 104;
        VillageState state = VillageState.of(village);
        Prestige.Result result = Prestige.evaluateCached(village, state.rank);
        y = BlueprintPanels.wrapped(context, textRenderer, Text.translatable("gui.mca_vie.plan.map.rank", state.rank.getName()), x, y, w, GOLD);
        y = BlueprintPanels.wrapped(context, textRenderer, Text.translatable("gui.mca_vie.plan.map.prestige", BlueprintPanels.format(result.prestige())), x, y, w, WHITE);
        y = BlueprintPanels.wrapped(context, textRenderer, Text.translatable("gui.mca_vie.plan.map.buildings", village.getBuildings().size()), x, y, w, WHITE);
        BlueprintPanels.wrapped(context, textRenderer, Text.translatable("gui.mca_vie.plan.map.population", village.getPopulation(), village.getMaxPopulation()), x, y, w, WHITE);

        if (!village.isAutoScan() && village.getBuildings().size() <= 1) {
            return Text.translatable("gui.mca_vie.plan.hint.autoscan");
        }
        return Text.translatable("gui.mca_vie.plan.hint.map");
    }

    private void renderMapTooltips(DrawContext context, int mouseX, int mouseY) {
        int size = mapSize();
        if (mouseX < cx || mouseX >= cx + size || mouseY < cy || mouseY >= cy + size) {
            return;
        }
        int half = size / 2;
        float sc = Math.min((float) half / (village.getBox().getMaxBlockCount() + 3) * 2, 2.0f);
        int localX = (int) ((mouseX - (cx + half)) / sc + village.getCenter().getX());
        int localZ = (int) ((mouseY - (cy + half)) / sc + village.getCenter().getZ());
        List<Building> hover = new ArrayList<>();
        for (Building building : village.getBuildings().values()) {
            if (!building.isComplete()) {
                continue;
            }
            BuildingType bt = building.getBuildingType();
            if (bt.isIcon()) {
                BlockPos c = building.getCenter();
                if (c.getSquaredDistance(new Vec3i(localX, c.getY(), localZ)) < 36) {
                    hover.add(building);
                }
            } else {
                BlockPos p0 = building.getPos0();
                BlockPos p1 = building.getPos1();
                if (localX >= p0.getX() - 1 && localX <= p1.getX() + 1 && localZ >= p0.getZ() - 1 && localZ <= p1.getZ() + 1) {
                    hover.add(building);
                }
            }
        }
        if (hover.isEmpty()) {
            return;
        }
        hover.sort((a, b) -> b.getCenter().getY() - a.getCenter().getY());
        List<Text> lines = new ArrayList<>();
        for (Building b : hover) {
            if (!lines.isEmpty()) {
                lines.add(Text.empty());
            }
            lines.addAll(buildingTooltip(b));
        }
        context.drawTooltip(textRenderer, lines, mouseX, mouseY);
    }

    private List<Text> buildingTooltip(Building building) {
        List<Text> lines = new ArrayList<>();
        BuildingType bt = BuildingTypes.getInstance().getBuildingType(building.getType());
        lines.add(Text.translatable("buildingType." + bt.name()).formatted(Formatting.WHITE));
        for (String name : village.getResidents(building.getId())) {
            lines.add(Text.literal(name).formatted(Formatting.AQUA));
        }
        BlueprintPanels.appendTooltip(lines, village, building);
        for (Map.Entry<Identifier, List<BlockPos>> block : building.getBlocks().entrySet()) {
            lines.add(Text.literal(block.getValue().size() + " x ").append(blockName(block.getKey())).formatted(Formatting.DARK_GRAY));
        }
        return lines;
    }

    private static Text blockName(Identifier id) {
        if (Registries.BLOCK.containsId(id)) {
            return Text.translatable(Registries.BLOCK.get(id).getTranslationKey());
        }
        return Text.translatable("tag." + id);
    }

    // catalogue

    private Text renderCatalog(DrawContext context) {
        int x = cx + 112;
        int w = cx + cw - x - 6;
        int y = beginScroll(context, cy);
        int start = y;
        if (selectedBuilding == null) {
            y = BlueprintPanels.wrapped(context, textRenderer, Text.translatable("gui.blueprint.buildingTypes"), x, y, w, GRAY);
        } else {
            y = BlueprintPanels.wrapped(context, textRenderer, Text.translatable("buildingType." + selectedBuilding.name()), x, y, w, selectedBuilding.getColor() | 0xff000000);
            y = BlueprintPanels.wrapped(context, textRenderer, Text.translatable("buildingType." + selectedBuilding.name() + ".description").formatted(Formatting.ITALIC), x, y, w, GRAY);
            y += 4;
            y = BlueprintPanels.renderCatalogInfo(context, textRenderer, selectedBuilding, x, y, w);
            if (!selectedBuilding.getGroups().isEmpty()) {
                y += 4;
                y = BlueprintPanels.wrapped(context, textRenderer, Text.translatable("gui.mca_vie.plan.blocks"), x, y, w, LABEL);
                for (Map.Entry<Identifier, Integer> b : selectedBuilding.getGroups().entrySet()) {
                    y = BlueprintPanels.wrapped(context, textRenderer, Text.literal(b.getValue() + " x ").append(blockName(b.getKey())), x + 4, y, w - 4, WHITE);
                }
            }
        }
        endScroll(context, cy, start, y);
        return Text.translatable("gui.blueprint.catalogHint");
    }

    // villageois

    private List<Map.Entry<UUID, String>> residents() {
        return village.getResidentNames().entrySet().stream().sorted(Map.Entry.comparingByValue()).toList();
    }

    private Text renderVillagers(DrawContext context, int mouseX, int mouseY) {
        VillageState state = VillageState.of(village);
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.mca_vie.plan.villagers.count", village.getPopulation(), village.getMaxPopulation()), cx, cy, LABEL);
        int top = cy + 14;
        int y = beginScroll(context, top);
        int start = y;
        for (Map.Entry<UUID, String> resident : residents()) {
            boolean hover = mouseX >= cx && mouseX < cx + cw - 4 && mouseY >= Math.max(top, y - 1) && mouseY < Math.min(cy + ch, y + LINE - 1);
            if (hover) {
                context.fill(cx - 1, y - 1, cx + cw - 4, y + LINE - 1, 0x30ffffff);
                hoveredVillager = resident.getKey();
            }
            context.drawTextWithShadow(textRenderer, resident.getValue(), cx + 2, y, hover ? GOLD : WHITE);
            VillageState.TaxEntry entry = state.taxRoll.get(resident.getKey());
            if (entry != null) {
                Job job = Job.values()[Math.max(0, Math.min(Job.values().length - 1, entry.job()))];
                Text line = Text.translatable("gui.mca_vie.villagers.tax", job.getName(),
                        Text.translatable("job.mca_vie.level." + entry.level()), BlueprintPanels.format(Math.round(entry.amount() * 10) / 10.0));
                int room = cw - 12 - textRenderer.getWidth(resident.getValue()) - 8;
                if (room > 20) {
                    OrderedText trimmed = Language.getInstance().reorder(textRenderer.trimToWidth(line, room));
                    context.drawTextWithShadow(textRenderer, trimmed, cx + cw - 6 - textRenderer.getWidth(trimmed), y, GRAY);
                }
            }
            y += LINE;
        }
        endScroll(context, top, start, y);
        return Text.translatable("gui.mca_vie.plan.hint.villagers");
    }

    // règles

    private Text renderRules(DrawContext context) {
        VillageState state = VillageState.of(village);
        int[] values = ruleValues();
        Rank[] required = rulesRequired();
        int max = Math.round(VillageRights.maxTaxes(state.rank) * 100);
        int equilibrium = VillageEconomy.equilibrium(village, Prestige.evaluateCached(village, state.rank), values[0]);
        boolean storage = Prestige.hasActive(Prestige.evaluateCached(village, state.rank), village, "storage");
        Text[] labels = {Text.translatable("gui.blueprint.taxes"), Text.translatable("gui.blueprint.birth"),
                Text.translatable("gui.blueprint.marriage"), Text.translatable("gui.mca_vie.rules.nature")};
        Text[] hints = {Text.translatable("gui.mca_vie.rules.tax_line", max, state.rank.getName(), equilibrium),
                Text.translatable("gui.mca_vie.rules.birth_hint"), Text.translatable("gui.mca_vie.rules.marriage_hint"),
                Text.translatable(storage ? "gui.mca_vie.rules.nature_hint" : "gui.mca_vie.rules.nature_storage")};
        int textW = cw - 84;
        int valueX = cx + cw - 38;
        Text footer = Text.translatable("gui.mca_vie.plan.hint.rules");
        for (int i = 0; i < 4; i++) {
            int y = cy + i * rulesRow();
            boolean ok = allowed(required[i]);
            context.drawTextWithShadow(textRenderer, labels[i], cx, y, WHITE);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(values[i] + " %"), valueX, y, ok ? GOLD : DIM);
            int lineY = BlueprintPanels.wrapped(context, textRenderer, hints[i], cx, y + LINE, textW, i == 3 && !storage ? RED : GRAY);
            if (!ok) {
                context.drawTextWithShadow(textRenderer, locked(required[i]), cx, lineY, RED);
            }
        }
        return footer;
    }

    // trésor

    private Text renderTreasury(DrawContext context) {
        if (!allowed(VillageRights.VIEW_TREASURY)) {
            BlueprintPanels.wrapped(context, textRenderer, Text.translatable("gui.mca_vie.plan.locked_page", Text.translatable(VillageRights.VIEW_TREASURY.getTranslationKey())), cx, cy, cw, RED);
            return null;
        }
        boolean actions = treasuryActions();
        int bottom = actions ? cy + ch - 42 : cy + ch;
        context.enableScissor(cx - 1, cy, cx + cw + 1, bottom);
        int y = cy - scroll;
        int end = BlueprintPanels.renderTreasury(context, textRenderer, village, cx, y, cw - 6);
        context.disableScissor();
        int area = bottom - cy;
        scrollMax = Math.max(0, end - y - area);
        scroll = Math.max(0, Math.min(scroll, scrollMax));
        if (actions) {
            int by = cy + ch - 38;
            context.fill(cx - 2, by - 4, cx + cw + 2, by - 3, 0x30ffffff);
            context.drawTextWithShadow(textRenderer, Text.translatable("gui.mca_vie.treasury.withdraw"), cx, by + 5, allowed(VillageRights.WITHDRAW) ? WHITE : DIM);
            context.drawTextWithShadow(textRenderer, Text.translatable("gui.mca_vie.treasury.deposit"), cx, by + 25, allowed(VillageRights.DEPOSIT) ? WHITE : DIM);
            int x = cx + 50 + 112;
            if (!allowed(VillageRights.WITHDRAW)) {
                Text why = locked(VillageRights.WITHDRAW);
                context.drawTextWithShadow(textRenderer, Language.getInstance().reorder(textRenderer.trimToWidth(why, cx + cw - x)), x, by + 5, RED);
            } else if (VillageEconomy.availableShare(VillageState.of(village)) <= 0) {
                context.drawTextWithShadow(textRenderer, Language.getInstance().reorder(textRenderer.trimToWidth(
                        Text.translatable("gui.mca_vie.plan.withdraw_empty"), cx + cw - x)), x, by + 5, GRAY);
            }
        }
        return scrollMax > 0 ? Text.translatable("gui.mca_vie.plan.hint.scroll") : Text.translatable("gui.mca_vie.plan.hint.treasury");
    }

    // décrets

    private Text renderDecrees(DrawContext context) {
        long today = today();
        VillageState state = VillageState.of(village);
        int x = cx + decreeButtonWidth() + 6;
        int w = cx + cw - x;
        int y = cy;
        for (Decree decree : Decree.values()) {
            MutableText reason = VillageDecrees.unavailable(village, decree, today);
            boolean active = state.activeDecrees.contains(decree);
            Text status;
            int color;
            if (reason != null) {
                status = reason;
                color = GRAY;
            } else if (decree.kind == Decree.Kind.TOGGLE) {
                status = Text.translatable(active ? "gui.mca_vie.decree.active" : "gui.mca_vie.decree.weekly");
                color = active ? GREEN : WHITE;
            } else if (decree == Decree.RENOVATION) {
                status = Text.translatable("gui.mca_vie.decree.renovation_cost", VillageConfig.get().renovationWeeks);
                color = WHITE;
            } else {
                status = Text.translatable("gui.mca_vie.decree.cost", VillageDecrees.launchCost(village, decree));
                color = WHITE;
            }
            List<OrderedText> lines = textRenderer.wrapLines(status, Math.max(40, w));
            if (lines.size() == 1) {
                context.drawTextWithShadow(textRenderer, lines.get(0), x, y + 5, color);
            } else {
                context.drawTextWithShadow(textRenderer, lines.get(0), x, y, color);
                context.drawTextWithShadow(textRenderer, lines.get(1), x, y + 10, color);
            }
            y += 21;
        }
        if (!allowed(VillageRights.DECREES)) {
            return Text.translatable("gui.mca_vie.decree.rank_low", Text.translatable(VillageRights.DECREES.getTranslationKey())).formatted(Formatting.RED);
        }
        return Text.translatable("gui.mca_vie.plan.hint.decrees");
    }

    // marché

    private Text renderMarket(DrawContext context) {
        VillageState state = VillageState.of(village);
        VillageConfig config = VillageConfig.get();
        int y = cy;
        if (!state.marketActive) {
            context.drawTextWithShadow(textRenderer, Language.getInstance().reorder(textRenderer.trimToWidth(Text.translatable("gui.mca_vie.market.inactive"), cw)), cx, y, RED);
        } else {
            Text day = Text.translatable("calendar.mca_vie.day." + config.marketDayOfWeek);
            context.drawTextWithShadow(textRenderer, Text.translatable("gui.mca_vie.market.day", day, config.marketStartHour, config.marketEndHour), cx, y, GOLD);
        }
        y += LINE;
        if (state.lastMarketSales > 0 || state.lastMarketOrders > 0) {
            context.drawTextWithShadow(textRenderer, Text.translatable("gui.mca_vie.market.last", state.lastMarketSales, state.lastMarketOrders), cx, y, GRAY);
        }
        y += LINE;
        UUID me = client == null || client.player == null ? null : client.player.getUuid();
        long waiting = state.orders.stream().filter(o -> o.player.equals(me) && !o.ready).count();
        long ready = state.orders.stream().filter(o -> o.player.equals(me) && o.ready).count();
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.mca_vie.market.orders", waiting, ready), cx, y, WHITE);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal((marketPage + 1) + " / " + marketPages()), cx + 35, cy + ch - 13, GRAY);
        return Text.translatable("gui.mca_vie.plan.hint.market");
    }

    // registre

    private List<CivilRegistry.Entry> registryEntries() {
        return VillageState.of(village).registry.stream().filter(e -> registryFilter < 0 || e.kind() == registryFilter).toList();
    }

    private Text renderRegistry(DrawContext context, int mouseX, int mouseY) {
        List<CivilRegistry.Entry> entries = registryEntries();
        int top = cy + 22;
        int y = beginScroll(context, top);
        int start = y;
        Text hovered = null;
        if (entries.isEmpty()) {
            context.drawTextWithShadow(textRenderer, Text.translatable("gui.mca_vie.registry.empty"), cx, y, GRAY);
            y += LINE;
        }
        int width = cw - 6;
        for (CivilRegistry.Entry e : entries) {
            Text date = Text.empty().append(TreeTexts.date(e.day())).append(" : ");
            MutableText line = BlueprintPanels.registryLine(e);
            int dateWidth = Math.min(textRenderer.getWidth(date), width / 2);
            int rest = width - dateWidth;
            context.drawTextWithShadow(textRenderer, Language.getInstance().reorder(textRenderer.trimToWidth(date, dateWidth)), cx, y, GRAY);
            context.drawTextWithShadow(textRenderer, Language.getInstance().reorder(textRenderer.trimToWidth(line, rest)), cx + dateWidth, y, BlueprintPanels.registryColor(e));
            if ((textRenderer.getWidth(line) > rest || textRenderer.getWidth(date) > dateWidth)
                    && mouseX >= cx && mouseX < cx + width && mouseY >= Math.max(top, y - 1) && mouseY < Math.min(cy + ch, y + LINE - 1)) {
                hovered = Text.empty().append(date).append(line);
            }
            y += LINE;
        }
        endScroll(context, top, start, y);
        if (hovered != null) {
            context.drawOrderedTooltip(textRenderer, textRenderer.wrapLines(hovered, 240), mouseX, mouseY);
        }
        int year = WorldCalendar.displayYearOfDay(today());
        int[] counts = new int[3];
        for (CivilRegistry.Entry e : VillageState.of(village).registry) {
            if (e.kind() >= 0 && e.kind() < 3 && WorldCalendar.displayYearOfDay(e.day()) == year) {
                counts[e.kind()]++;
            }
        }
        return Text.translatable("gui.mca_vie.registry.year", year, counts[0], counts[1], counts[2]);
    }

    // justice

    private Text renderJustice(DrawContext context) {
        VillageState state = VillageState.of(village);
        VillageConfig config = VillageConfig.get();
        long today = today();
        Text status = state.prisonActive ? Text.translatable("gui.mca_vie.justice.prison_ok", config.prisonDays)
                : Text.translatable("gui.mca_vie.justice.no_prison", config.building("prison") == null ? "" : config.building("prison").requiredRank().getName());
        List<OrderedText> statusLines = textRenderer.wrapLines(status, cw);
        for (int i = 0; i < Math.min(2, statusLines.size()); i++) {
            context.drawTextWithShadow(textRenderer, statusLines.get(i), cx, cy + i * 10, state.prisonActive ? GREEN : GRAY);
        }
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.mca_vie.plan.justice.cases", state.cases.size()), cx, cy + 24, LABEL);
        int rows = justiceRows();
        int y = cy + 36;
        if (state.cases.isEmpty()) {
            context.drawTextWithShadow(textRenderer, Text.translatable("gui.mca_vie.justice.no_case"), cx + 4, y + 5, GRAY);
        }
        int textW = cw - 98;
        for (int i = 0; i < rows; i++) {
            int index = justicePage * rows + i;
            if (index >= state.cases.size()) {
                break;
            }
            VillageState.Case c = state.cases.get(index);
            long left = Math.max(0, c.day + config.judgmentDays - today);
            context.drawTextWithShadow(textRenderer, Language.getInstance().reorder(textRenderer.trimToWidth(Text.translatable("gui.mca_vie.justice.case", c.name, c.amount), textW)), cx + 4, y, WHITE);
            context.drawTextWithShadow(textRenderer, Language.getInstance().reorder(textRenderer.trimToWidth(Text.translatable("gui.mca_vie.justice.case_due", left), textW)), cx + 4, y + 10, GRAY);
            y += 22;
        }
        int top = cy + 36 + rows * 22 + 4;
        context.fill(cx - 2, top - 3, cx + cw + 2, top - 2, 0x30ffffff);
        int py = beginScroll(context, top);
        int start = py;
        py = BlueprintPanels.wrapped(context, textRenderer, Text.translatable("gui.mca_vie.justice.prisoners", state.prisoners.size()), cx, py, cw - 6, GOLD);
        for (VillageState.Prisoner p : state.prisoners.values()) {
            py = BlueprintPanels.wrapped(context, textRenderer, Text.translatable("gui.mca_vie.justice.prisoner", p.name, Math.max(0, p.releaseDay - today)), cx + 6, py, cw - 12, GRAY);
        }
        endScroll(context, top, start, py);
        if (!allowed(VillageRights.JUSTICE)) {
            return Text.translatable("gui.mca_vie.justice.rank_low", Text.translatable(VillageRights.JUSTICE.getTranslationKey()),
                    rank == null ? Text.literal("?") : Text.translatable(rank.getTranslationKey())).formatted(Formatting.RED);
        }
        return Text.translatable("gui.mca_vie.justice.hint", config.crimeHappiness);
    }

    // ---- entrées ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !renaming) {
            Page tab = tabAt(mouseX, mouseY);
            if (tab != null) {
                if (tab != page) {
                    page = tab;
                    scroll = 0;
                    rebuild();
                }
                if (client != null) {
                    client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
                return true;
            }
            if (page == Page.VILLAGERS && hoveredVillager != null && client != null) {
                client.setScreen(new FamilyTreeScreen(hoveredVillager));
                return true;
            }
        }
        for (Element child : children()) {
            if (child.mouseClicked(mouseX, mouseY, button)) {
                setFocused(child);
                if (button == 0) {
                    setDragging(true);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (mouseX >= cx && mouseX < cx + cw && mouseY >= cy && mouseY < cy + ch) {
            scroll = Math.max(0, Math.min(scrollMax, scroll - (int) (amount * LINE * 2)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }
}
