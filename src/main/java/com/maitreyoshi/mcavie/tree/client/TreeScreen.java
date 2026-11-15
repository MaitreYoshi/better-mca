package com.maitreyoshi.mcavie.tree.client;

import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.life.LifeCycle;
import com.maitreyoshi.mcavie.phone.PhoneNetwork;
import com.maitreyoshi.mcavie.phone.client.PhoneClient;
import com.maitreyoshi.mcavie.phone.client.PhonePhotos;
import com.maitreyoshi.mcavie.phone.client.PhoneScreen;
import com.maitreyoshi.mcavie.phone.client.PortraitPickerScreen;
import com.mojang.blaze3d.systems.RenderSystem;
import com.maitreyoshi.mcavie.tree.FamilyPerson;
import com.maitreyoshi.mcavie.tree.RelationInfo;
import com.maitreyoshi.mcavie.tree.TreeProtocol;
import com.maitreyoshi.mcavie.tree.client.TreeLayout.Card;
import com.maitreyoshi.mcavie.tree.client.TreeLayout.Mark;
import com.maitreyoshi.mcavie.tree.client.TreeLayout.Segment;
import net.mca.client.gui.InteractScreen;
import net.mca.client.gui.MCAScreens;
import net.mca.client.resources.Icon;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import com.maitreyoshi.mcavie.phone.client.PhoneSettings;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class TreeScreen extends Screen {
    private static final int PANEL_WIDTH = 124;
    private static final int TOP = 22;
    private static final int BOTTOM = 26;
    private static final int RESULT_HEIGHT = 20;
    private static final int LINEAGE_ROW_HEIGHT = 22;
    private static final float MIN_ZOOM = 0.03f;
    private static final float BLOCK_ZOOM = 0.08f;
    private static final float MAX_ZOOM = 2.0f;
    private static final float TEXT_ZOOM = 0.3f;
    private static final int MAX_GENERATIONS = 10;
    private static final int SEARCH_DELAY = 6;

    private static final int CARD_BG = 0xF0100010;
    private static final int CARD_BG_HOVER = 0xF0100040;
    private static final int CARD_BG_DEAD = 0xF0141218;
    private static final int CARD_BORDER = 0x505000FF;
    private static final int CARD_BORDER_HOVER = 0xFF7050D0;
    private static final int CARD_BORDER_DEAD = 0x50909090;
    private static final int CARD_BORDER_ROOT = 0xFFE0B040;

    private enum View {TREE, PATH, LINEAGE}

    @Nullable
    private final Screen parent;
    private boolean requested;
    private ButtonWidget relationsButton;
    @Nullable
    private List<FamilyPerson> treePersons;
    private View view = View.TREE;

    private UUID root;
    private int up = 2;
    private int down = 2;
    private boolean treeWaiting;
    private TreeLayout treeLayout = TreeLayout.EMPTY;
    private final Map<UUID, FamilyPerson> treeById = new HashMap<>();
    private final Map<UUID, String> knownNames = new HashMap<>();

    private UUID pathA;
    private UUID pathB;
    private int pathIndex;
    private int pathCount;
    private boolean pathWaiting;
    @Nullable
    private TreeProtocol.Response pathResponse;
    private TreeLayout pathLayout = TreeLayout.EMPTY;

    private String searchText = "";
    private int searchDelay = -1;
    private boolean searchWaiting;
    private List<FamilyPerson> searchResults = List.of();
    private double resultsScroll;
    private String lineageSurname = "";
    private boolean lineageWaiting;
    private List<FamilyPerson> lineage = List.of();
    private double lineageScroll;
    private List<LineageRow> lineageRows = List.of();
    private List<FamilyPerson> lineageRowsSource;
    private long lineageRowsDay;
    private int lineageRowsWidth;

    private double camX;
    private double camY;
    private float zoom = 1;
    private boolean keepCamera;

    private boolean pressInView;
    private double pressX;
    private double pressY;
    private boolean dragged;

    @Nullable
    private Details details;

    private ButtonWidget upMinus;
    private ButtonWidget upPlus;
    private ButtonWidget downMinus;
    private ButtonWidget downPlus;
    private ButtonWidget pathButton;
    private ButtonWidget lineageButton;
    private ButtonWidget centerButton;
    private ButtonWidget fitButton;
    private ButtonWidget previousLink;
    private ButtonWidget nextLink;
    private TextFieldWidget searchField;

    public TreeScreen(UUID root, @Nullable Screen parent) {
        super(Text.translatable("tree.mca_vie.title_empty"));
        this.root = root;
        this.parent = parent;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        assert client != null;
        client.setScreen(parent);
    }

    @Override
    protected void init() {
        int x0 = 6;
        int inner = PANEL_WIDTH - 12;
        int minusX = PANEL_WIDTH - 6 - 44;
        int plusX = PANEL_WIDTH - 6 - 14;

        upMinus = addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> setUp(previous(up))).dimensions(minusX, TOP + 14, 14, 14).build());
        upPlus = addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> setUp(next(up))).dimensions(plusX, TOP + 14, 14, 14).build());
        downMinus = addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> setDown(previous(down))).dimensions(minusX, TOP + 31, 14, 14).build());
        downPlus = addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> setDown(next(down))).dimensions(plusX, TOP + 31, 14, 14).build());

        pathButton = addDrawableChild(ButtonWidget.builder(Text.translatable("tree.mca_vie.path_button"), b -> togglePath())
                .dimensions(x0, TOP + 52, inner, 18).build());
        lineageButton = addDrawableChild(ButtonWidget.builder(Text.translatable("tree.mca_vie.lineage_button"), b -> toggleLineage())
                .dimensions(x0, TOP + 73, inner, 18).build());

        searchField = new TextFieldWidget(textRenderer, x0 + 1, TOP + 109, inner - 2, 16, Text.translatable("tree.mca_vie.search"));
        searchField.setMaxLength(32);
        searchField.setText(searchText);
        searchField.setPlaceholder(Text.translatable("tree.mca_vie.search_hint").formatted(Formatting.DARK_GRAY));
        searchField.setChangedListener(this::onSearchChanged);
        addDrawableChild(searchField);

        int barY = height - BOTTOM + 4;
        centerButton = addDrawableChild(ButtonWidget.builder(Text.translatable("tree.mca_vie.center"), b -> {
            zoom = 1;
            centerOnRoot();
        }).dimensions(PANEL_WIDTH + 6, barY, 70, 18).build());
        fitButton = addDrawableChild(ButtonWidget.builder(Text.translatable("tree.mca_vie.fit"), b -> fitAll())
                .dimensions(PANEL_WIDTH + 80, barY, 70, 18).build());
        relationsButton = addDrawableChild(ButtonWidget.builder(relationsText(), b -> toggleRelations())
                .dimensions(PANEL_WIDTH + 154, barY, 100, 18).build());
        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close())
                .dimensions(width - 6 - 80, barY, 80, 18).build());
        previousLink = addDrawableChild(ButtonWidget.builder(Text.literal("◀"), b -> changeLink(-1))
                .dimensions(viewLeft() + 4, viewTop() + 5, 18, 18).build());
        nextLink = addDrawableChild(ButtonWidget.builder(Text.literal("▶"), b -> changeLink(1))
                .dimensions(viewRight() - 4 - 18, viewTop() + 5, 18, 18).build());

        if (!requested) {
            requested = true;
            requestTree(root);
        } else {
            clampScrolls();
        }
        updateWidgets();
    }

    private static Text relationsText() {
        return Text.translatable(PhoneSettings.treeRelations() ? "tree.mca_vie.relations.on" : "tree.mca_vie.relations.off");
    }

    /** Affiche ou cache les liens de parenté jaunes, puis recalcule les cartes sans bouger la caméra. */
    private void toggleRelations() {
        PhoneSettings.treeYellow = !PhoneSettings.treeYellow;
        PhoneSettings.save();
        relationsButton.setMessage(relationsText());
        if (treePersons != null) {
            treeLayout = TreeLayout.family(treePersons, root, textRenderer, today(), up, down);
        }
        if (pathResponse != null) {
            pathLayout = TreeLayout.path(pathResponse.persons(), pathResponse.path(), textRenderer, today());
        }
    }

    private void updateWidgets() {
        upMinus.active = up != 1;
        upPlus.active = up != TreeProtocol.ALL_GENERATIONS;
        downMinus.active = down != 1;
        downPlus.active = down != TreeProtocol.ALL_GENERATIONS;
        Text back = Text.translatable("tree.mca_vie.back");
        pathButton.setMessage(view == View.PATH ? back : Text.translatable("tree.mca_vie.path_button"));
        lineageButton.setMessage(view == View.LINEAGE ? back : Text.translatable("tree.mca_vie.lineage_button"));
        lineageButton.active = view == View.LINEAGE || !rootSurname().isEmpty();
        centerButton.visible = view == View.TREE;
        fitButton.visible = view != View.LINEAGE;
        previousLink.visible = nextLink.visible = view == View.PATH && pathCount > 1;
    }

    @Override
    public void tick() {
        searchField.tick();
        if (searchDelay > 0 && --searchDelay == 0) {
            sendSearch();
        }
        updateWidgets();
    }

    private static int next(int value) {
        if (value == TreeProtocol.ALL_GENERATIONS) {
            return value;
        }
        return value >= MAX_GENERATIONS ? TreeProtocol.ALL_GENERATIONS : value + 1;
    }

    private static int previous(int value) {
        if (value == TreeProtocol.ALL_GENERATIONS) {
            return MAX_GENERATIONS;
        }
        return Math.max(1, value - 1);
    }

    private static String generationText(int value) {
        return value == TreeProtocol.ALL_GENERATIONS ? "∞" : String.valueOf(value);
    }

    private void setUp(int value) {
        up = value;
        requestTree(root);
    }

    private void setDown(int value) {
        down = value;
        requestTree(root);
    }

    private UUID viewer() {
        assert client != null && client.player != null;
        return client.player.getUuid();
    }

    private long today() {
        return client != null && client.world != null ? WorldCalendar.now(client.world).dayIndex() : 0;
    }

    private void requestTree(UUID id) {
        root = id;
        treeWaiting = true;
        TreeClient.send(TreeProtocol.Request.tree(id, up, down));
    }

    private void showTree(UUID id) {
        details = null;
        view = View.TREE;
        if (!id.equals(root) || treeLayout.isEmpty()) {
            requestTree(id);
        } else {
            centerOnRoot();
        }
        updateWidgets();
    }

    private void requestPath() {
        if (pathA == null || pathB == null) {
            return;
        }
        pathWaiting = true;
        pathResponse = null;
        pathLayout = TreeLayout.EMPTY;
        TreeClient.send(TreeProtocol.Request.path(pathA, pathB, pathIndex));
    }

    private void showPath(UUID a, UUID b) {
        details = null;
        view = View.PATH;
        if (!a.equals(pathA) || !b.equals(pathB)) {
            pathIndex = 0;
            pathCount = 0;
        }
        pathA = a;
        pathB = b;
        requestPath();
        updateWidgets();
    }

    private void changeLink(int delta) {
        if (pathCount <= 1) {
            return;
        }
        details = null;
        pathIndex = Math.floorMod(pathIndex + delta, pathCount);
        requestPath();
    }

    private void sendSearch() {
        searchDelay = -1;
        String text = searchText.trim();
        if (text.isEmpty()) {
            searchResults = List.of();
            searchWaiting = false;
            return;
        }
        searchWaiting = true;
        TreeClient.send(TreeProtocol.Request.search(text));
    }

    private void onSearchChanged(String text) {
        searchText = text;
        if (text.isBlank()) {
            searchDelay = -1;
            searchResults = List.of();
            searchWaiting = false;
        } else {
            searchDelay = SEARCH_DELAY;
        }
    }

    public void accept(TreeProtocol.Response response) {
        for (FamilyPerson person : response.persons()) {
            knownNames.put(person.id(), TreeTexts.name(person));
        }
        switch (response.mode()) {
            case TreeProtocol.MODE_TREE -> {
                if (!response.root().equals(root)) {
                    return;
                }
                treeWaiting = false;
                treeById.clear();
                response.persons().forEach(p -> treeById.put(p.id(), p));
                treePersons = response.persons();
                treeLayout = TreeLayout.family(response.persons(), root, textRenderer, today(), up, down);
                if (keepCamera) {
                    keepCamera = false;
                } else if (view == View.TREE) {
                    centerOnRoot();
                }
            }
            case TreeProtocol.MODE_PATH -> {
                if (!response.root().equals(pathA)
                        || !response.path().isEmpty() && !response.path().get(response.path().size() - 1).equals(pathB)
                        || pathCount > 0 && response.index() != pathIndex) {
                    return;
                }
                pathWaiting = false;
                pathIndex = response.index();
                pathCount = response.count();
                pathResponse = response;
                pathLayout = TreeLayout.path(response.persons(), response.path(), textRenderer, today());
                if (view == View.PATH) {
                    fitAll();
                }
            }
            case TreeProtocol.MODE_SEARCH -> {
                searchWaiting = false;
                searchResults = response.persons();
                resultsScroll = 0;
            }
            case TreeProtocol.MODE_LINEAGE -> {
                if (!response.text().equals(lineageSurname)) {
                    return;
                }
                lineageWaiting = false;
                lineage = response.persons();
                lineageScroll = 0;
            }
            default -> {
            }
        }
        if (searchField != null) {
            updateWidgets();
        }
    }

    private void togglePath() {
        if (view == View.PATH) {
            showTree(root);
        } else {
            showPath(viewer(), root);
        }
    }

    private String rootSurname() {
        FamilyPerson person = treeById.get(root);
        return person == null ? "" : TreeTexts.surname(person);
    }

    private void toggleLineage() {
        details = null;
        if (view == View.LINEAGE) {
            showTree(root);
            return;
        }
        String surname = rootSurname();
        if (surname.isEmpty()) {
            return;
        }
        view = View.LINEAGE;
        if (!surname.equals(lineageSurname) || lineage.isEmpty()) {
            lineageSurname = surname;
            lineage = List.of();
            lineageWaiting = true;
            TreeClient.send(TreeProtocol.Request.lineage(surname));
        }
        updateWidgets();
    }

    private int viewLeft() {
        return PANEL_WIDTH;
    }

    private int viewRight() {
        return width;
    }

    private int viewTop() {
        return TOP;
    }

    private int viewBottom() {
        return height - BOTTOM;
    }

    private double viewCenterX() {
        return (viewLeft() + viewRight()) / 2.0;
    }

    private double viewCenterY() {
        return (viewTop() + viewBottom()) / 2.0;
    }

    private boolean inView(double x, double y) {
        return x >= viewLeft() && x < viewRight() && y >= viewTop() && y < viewBottom();
    }

    private double worldX(double screenX) {
        return camX + (screenX - viewCenterX()) / zoom;
    }

    private double worldY(double screenY) {
        return camY + (screenY - viewCenterY()) / zoom;
    }

    private TreeLayout currentLayout() {
        return view == View.PATH ? pathLayout : treeLayout;
    }

    private void centerOnRoot() {
        Card card = treeLayout.root;
        if (card == null) {
            fitAll();
            return;
        }
        camX = card.centerX();
        camY = card.y + card.height / 2.0;
    }

    private void fitAll() {
        TreeLayout layout = currentLayout();
        if (layout.isEmpty()) {
            return;
        }
        double w = layout.maxX - layout.minX + 24;
        double h = layout.maxY - layout.minY + 24;
        double available = viewBottom() - viewTop() - (view == View.PATH ? 30 : 0);
        zoom = (float) Math.max(MIN_ZOOM, Math.min(1, Math.min((viewRight() - viewLeft()) / w, available / h)));
        camX = (layout.minX + layout.maxX) / 2.0;
        camY = (layout.minY + layout.maxY) / 2.0 - (view == View.PATH ? 15 / zoom : 0);
    }

    @Nullable
    private Card cardAt(double screenX, double screenY) {
        if (!inView(screenX, screenY) || view == View.LINEAGE) {
            return null;
        }
        double x = worldX(screenX);
        double y = worldY(screenY);
        for (Card card : currentLayout().cards) {
            if (card.contains(x, y)) {
                return card;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (details != null) {
            Runnable action = details.actionAt(mouseX, mouseY);
            boolean inside = details.contains(mouseX, mouseY);
            if (action != null) {
                playClick();
                details = null;
                action.run();
                return true;
            }
            details = null;
            if (inside) {
                return true;
            }
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && clickResult(mouseX, mouseY)) {
            return true;
        }
        if (view == View.LINEAGE) {
            if (button == 0 && inView(mouseX, mouseY)) {
                FamilyPerson person = lineageAt(mouseX, mouseY);
                if (person != null) {
                    playClick();
                    showTree(person.id());
                }
                return true;
            }
            return false;
        }
        if (inView(mouseX, mouseY)) {
            setFocused(null);
            if (button == 0) {
                pressInView = true;
                pressX = mouseX;
                pressY = mouseY;
                dragged = false;
                return true;
            }
            if (button == 1) {
                Card card = cardAt(mouseX, mouseY);
                if (card != null) {
                    details = new Details(card.person, (int) mouseX, (int) mouseY);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (pressInView && button == 0) {
            if (!dragged && Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) > 3) {
                dragged = true;
            }
            camX -= deltaX / zoom;
            camY -= deltaY / zoom;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (pressInView && button == 0) {
            pressInView = false;
            if (!dragged) {
                Card card = cardAt(mouseX, mouseY);
                if (card != null) {
                    playClick();
                    showTree(card.id());
                }
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (mouseX < PANEL_WIDTH && mouseY >= resultsTop()) {
            resultsScroll -= amount * RESULT_HEIGHT;
            clampScrolls();
            return true;
        }
        if (!inView(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, amount);
        }
        if (view == View.LINEAGE) {
            lineageScroll -= amount * LINEAGE_ROW_HEIGHT;
            clampScrolls();
            return true;
        }
        details = null;
        double x = worldX(mouseX);
        double y = worldY(mouseY);
        zoom = (float) Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * Math.pow(1.15, amount)));
        camX = x - (mouseX - viewCenterX()) / zoom;
        camY = y - (mouseY - viewCenterY()) / zoom;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (details != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            details = null;
            return true;
        }
        if (searchField.isFocused() && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            sendSearch();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void playClick() {
        assert client != null;
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1));
    }

    private void clampScrolls() {
        int visible = height - 6 - resultsTop();
        resultsScroll = Math.max(0, Math.min(resultsScroll, searchResults.size() * RESULT_HEIGHT - visible));
        int lineageVisible = viewBottom() - lineageTop();
        lineageScroll = Math.max(0, Math.min(lineageScroll, lineage.size() * LINEAGE_ROW_HEIGHT - lineageVisible));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.fill(0, TOP, PANEL_WIDTH - 1, height, 0x80000000);
        context.fill(viewLeft(), viewTop(), viewRight(), viewBottom(), 0x66000000);

        context.drawCenteredTextWithShadow(textRenderer, titleText(), (int) viewCenterX(), 8, 0xFFFFFF);

        switch (view) {
            case TREE -> renderTree(context, mouseX, mouseY);
            case PATH -> renderPath(context, mouseX, mouseY);
            case LINEAGE -> renderLineage(context, mouseX, mouseY);
        }
        renderPanel(context, mouseX, mouseY);

        super.render(context, mouseX, mouseY, delta);

        if (details != null) {
            details.render(context, mouseX, mouseY);
        }
    }

    private Text titleText() {
        return switch (view) {
            case PATH -> Text.translatable("tree.mca_vie.path_button");
            case LINEAGE -> Text.translatable("tree.mca_vie.lineage.title", lineageSurname);
            case TREE -> {
                FamilyPerson person = treeById.get(root);
                yield person == null ? title : Text.translatable("tree.mca_vie.title", TreeTexts.name(person));
            }
        };
    }

    private void renderTree(DrawContext context, int mouseX, int mouseY) {
        if (treeLayout.isEmpty()) {
            centeredMessage(context, Text.translatable(treeWaiting ? "tree.mca_vie.loading" : "tree.mca_vie.empty"));
            return;
        }
        renderLayout(context, treeLayout, mouseX, mouseY);
    }

    private void renderPath(DrawContext context, int mouseX, int mouseY) {
        renderLayout(context, pathLayout, mouseX, mouseY);

        Text sentence;
        if (pathWaiting || pathResponse == null) {
            sentence = Text.translatable("tree.mca_vie.loading");
        } else {
            sentence = pathSentence(pathResponse);
        }
        int cx = (int) viewCenterX();
        context.fill(viewLeft(), viewTop(), viewRight(), viewTop() + 28, 0x90000000);
        context.drawCenteredTextWithShadow(textRenderer, sentence, cx, viewTop() + 4, 0xFFFF55);
        MutableText slots = Text.translatable("tree.mca_vie.path.a", nameOf(pathA))
                .append("   ")
                .append(Text.translatable("tree.mca_vie.path.b", nameOf(pathB)));
        if (pathCount > 1) {
            slots.append("   ").append(Text.translatable("tree.mca_vie.path.link", pathIndex + 1, pathCount));
        }
        context.drawCenteredTextWithShadow(textRenderer, slots, cx, viewTop() + 16, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, Text.translatable("tree.mca_vie.path.pick"),
                viewLeft() + 6, viewBottom() - 12, 0x808080);
    }

    private Text pathSentence(TreeProtocol.Response response) {
        RelationInfo relation = response.relation();
        if (relation != null && "self".equals(relation.code())) {
            return Text.translatable("tree.mca_vie.path.self");
        }
        if (relation == null || "none".equals(relation.code()) || response.path().isEmpty()) {
            return Text.translatable("tree.mca_vie.path.none", nameOf(pathB), nameOf(pathA));
        }
        int gender = 0;
        for (FamilyPerson person : response.persons()) {
            if (person.id().equals(pathB)) {
                gender = person.gender();
            }
        }
        return Text.translatable("tree.mca_vie.path.relation", nameOf(pathB), TreeTexts.theRelation(relation, gender), nameOf(pathA));
    }

    private String nameOf(@Nullable UUID id) {
        if (id == null) {
            return "?";
        }
        if (client != null && client.player != null && id.equals(client.player.getUuid()) && !knownNames.containsKey(id)) {
            return client.player.getName().getString();
        }
        return knownNames.getOrDefault(id, "?");
    }

    @Nullable
    private FamilyPerson findPerson(UUID id) {
        FamilyPerson person = treeById.get(id);
        if (person == null && pathResponse != null) {
            person = pathResponse.persons().stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);
        }
        if (person == null) {
            person = searchResults.stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);
        }
        return person;
    }

    private void centeredMessage(DrawContext context, Text text) {
        context.drawCenteredTextWithShadow(textRenderer, text, (int) viewCenterX(), (int) viewCenterY() - 4, 0xAAAAAA);
    }

    private void renderLayout(DrawContext context, TreeLayout layout, int mouseX, int mouseY) {
        context.enableScissor(viewLeft(), viewTop(), viewRight(), viewBottom());
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(viewCenterX(), viewCenterY(), 0);
        matrices.scale(zoom, zoom, 1);
        matrices.translate(-camX, -camY, 0);

        double x0 = worldX(viewLeft()) - 2;
        double x1 = worldX(viewRight()) + 2;
        double y0 = worldY(viewTop()) - 2;
        double y1 = worldY(viewBottom()) + 2;
        int thickness = Math.max(1, (int) Math.ceil(1 / zoom));

        for (Segment segment : layout.segments) {
            int sx0 = Math.min(segment.x1(), segment.x2());
            int sx1 = Math.max(segment.x1(), segment.x2());
            int sy0 = Math.min(segment.y1(), segment.y2());
            int sy1 = Math.max(segment.y1(), segment.y2());
            if (sx1 < x0 || sx0 > x1 || sy1 < y0 || sy0 > y1) {
                continue;
            }
            drawSegment(context, sx0, sy0, sx1, sy1, segment.color(), segment.dashed(), thickness);
        }
        for (Mark mark : layout.marks) {
            if (mark.x() >= x0 && mark.x() <= x1 && mark.y() >= y0 && mark.y() <= y1) {
                boolean past = "married_past".equals(mark.icon());
                if (past) {
                    RenderSystem.setShaderColor(0.55f, 0.55f, 0.55f, 1);
                }
                drawIcon(context, past ? "married" : mark.icon(), mark.x() - 5, mark.y() - 5, 10);
                if (past) {
                    RenderSystem.setShaderColor(1, 1, 1, 1);
                }
            }
        }

        Card hovered = details == null && !pressInView ? cardAt(mouseX, mouseY) : null;
        boolean text = zoom >= TEXT_ZOOM;
        for (Card card : layout.cards) {
            if (card.right() < x0 || card.left() > x1 || card.bottom() < y0 || card.y > y1) {
                continue;
            }
            drawCard(context, card, card == hovered, text);
        }

        matrices.pop();
        context.disableScissor();
    }

    private static void drawSegment(DrawContext context, int x0, int y0, int x1, int y1, int color, boolean dashed, int thickness) {
        int half = thickness / 2;
        if (x0 == x1) {
            if (!dashed) {
                context.fill(x0 - half, y0, x0 - half + thickness, y1, color);
                return;
            }
            for (int y = y0; y < y1; y += 5) {
                context.fill(x0 - half, y, x0 - half + thickness, Math.min(y + 3, y1), color);
            }
        } else {
            if (!dashed) {
                context.fill(x0 - half, y0 - half, x1 - half + thickness, y0 - half + thickness, color);
                return;
            }
            for (int x = x0; x < x1; x += 5) {
                context.fill(x, y0 - half, Math.min(x + 3, x1), y0 - half + thickness, color);
            }
        }
    }

    private static void drawIcon(DrawContext context, String key, int x, int y, int size) {
        Icon icon = MCAScreens.getInstance().getIcon(key);
        context.drawTexture(InteractScreen.ICON_TEXTURES, x, y, size, size, icon.u(), icon.v(), 16, 16, 256, 256);
    }

    private void drawCard(DrawContext context, Card card, boolean hovered, boolean text) {
        int l = card.left();
        int t = card.y;
        int r = card.right();
        int b = card.bottom();
        if (zoom < BLOCK_ZOOM) {
            context.fill(l, t, r, b, card.highlighted ? CARD_BORDER_ROOT : hovered ? CARD_BORDER_HOVER : 0xFF000000 | card.colors[0]);
            return;
        }
        boolean dead = card.person.deceased();
        int background = hovered ? CARD_BG_HOVER : dead ? CARD_BG_DEAD : CARD_BG;
        int border = hovered ? CARD_BORDER_HOVER : dead ? CARD_BORDER_DEAD : CARD_BORDER;

        context.fill(l, t + 1, r, b - 1, background);
        context.fill(l + 1, t, r - 1, t + 1, background);
        context.fill(l + 1, b - 1, r - 1, b, background);
        context.drawBorder(l + 1, t + 1, r - l - 2, b - t - 2, border);
        if (card.highlighted) {
            context.drawBorder(l, t, r - l, b - t, CARD_BORDER_ROOT);
        }

        int x = l + TreeLayout.PADDING;
        if (card.person.portrait() != null) {
            drawPortrait(context, card.person.portrait(), l + 4, t + (b - t - TreeLayout.PORTRAIT) / 2);
            x += TreeLayout.PORTRAIT + TreeLayout.PORTRAIT_GAP;
        }
        if (!text) {
            context.fill(x - 1, t + 4, r - 4, t + 8, 0xFF000000 | card.colors[0]);
            return;
        }
        int y = t + (b - t - card.lines.length * TreeLayout.LINE_HEIGHT + 2) / 2;
        int nameX = x;
        if (dead) {
            drawIcon(context, "deceased", x - 1, y - 1, 10);
            nameX += TreeLayout.ICON_SPACE;
        }
        for (int i = 0; i < card.lines.length; i++) {
            context.drawText(textRenderer, card.lines[i], i == 0 ? nameX : x, y + i * TreeLayout.LINE_HEIGHT, card.colors[i], true);
        }
    }

    private static void drawPortrait(DrawContext context, UUID id, int x, int y) {
        int size = TreeLayout.PORTRAIT;
        PhonePhotos.Picture picture = PhonePhotos.portrait(id);
        if (picture == null) {
            context.fill(x, y, x + size, y + size, 0xFF202030);
            return;
        }
        context.drawTexture(picture.texture(), x, y, size, size, 0, 0, picture.width(), picture.height(), picture.width(), picture.height());
    }

    @Nullable
    private PhoneScreen phone() {
        return parent instanceof PhoneScreen screen && screen.state().access() ? screen : null;
    }

    public void refreshPortraits() {
        if (!treeLayout.isEmpty()) {
            keepCamera = view == View.TREE;
            requestTree(root);
        }
        if (pathResponse != null) {
            requestPath();
        }
    }

    private int lineageTop() {
        return viewTop() + 18;
    }

    @Nullable
    private FamilyPerson lineageAt(double mouseX, double mouseY) {
        if (mouseY < lineageTop() || mouseY >= viewBottom() || mouseX < viewLeft() || mouseX >= viewRight()) {
            return null;
        }
        int index = (int) ((mouseY - lineageTop() + lineageScroll) / LINEAGE_ROW_HEIGHT);
        return index >= 0 && index < lineage.size() ? lineage.get(index) : null;
    }

    private record LineageRow(Text status, int statusWidth, int statusColor, String name, String second) {
    }

    private List<LineageRow> lineageRows(long today, int width) {
        if (lineageRowsSource == lineage && lineageRowsDay == today && lineageRowsWidth == width) {
            return lineageRows;
        }
        List<LineageRow> rows = new ArrayList<>(lineage.size());
        for (FamilyPerson person : lineage) {
            boolean dead = person.deceased() || person.deathDay() != null;
            Text status = Text.translatable((dead ? "tree.mca_vie.dead." : "tree.mca_vie.alive.") + TreeTexts.g(person.gender()));
            int statusWidth = textRenderer.getWidth(status);
            String name = TreeTexts.trim(textRenderer, TreeTexts.name(person), width - statusWidth - 10);
            Text years = TreeTexts.yearsLine(person, today);
            String profession = TreeTexts.profession(person);
            String second = (years == null ? "" : years.getString())
                    + (years != null && !profession.isEmpty() ? "  ·  " : "")
                    + profession;
            rows.add(new LineageRow(status, statusWidth, dead ? 0x909090 : 0x55FF55, name, TreeTexts.trim(textRenderer, second, width)));
        }
        lineageRows = rows;
        lineageRowsSource = lineage;
        lineageRowsDay = today;
        lineageRowsWidth = width;
        return rows;
    }

    private void renderLineage(DrawContext context, int mouseX, int mouseY) {
        Text header = lineageWaiting
                ? Text.translatable("tree.mca_vie.loading")
                : Text.translatable("tree.mca_vie.lineage.count", lineage.size());
        context.drawCenteredTextWithShadow(textRenderer, header, (int) viewCenterX(), viewTop() + 5, 0xAAAAAA);
        if (!lineageWaiting && lineage.isEmpty()) {
            centeredMessage(context, Text.translatable("tree.mca_vie.no_result"));
            return;
        }

        long today = today();
        int left = viewLeft() + 8;
        int right = viewRight() - 8;
        FamilyPerson hovered = details == null ? lineageAt(mouseX, mouseY) : null;
        List<LineageRow> rows = lineageRows(today, right - left);
        context.enableScissor(viewLeft(), lineageTop(), viewRight(), viewBottom());
        for (int i = 0; i < lineage.size(); i++) {
            int y = lineageTop() + i * LINEAGE_ROW_HEIGHT - (int) lineageScroll;
            if (y + LINEAGE_ROW_HEIGHT < lineageTop() || y > viewBottom()) {
                continue;
            }
            FamilyPerson person = lineage.get(i);
            if (person == hovered) {
                context.fill(left - 4, y, right + 4, y + LINEAGE_ROW_HEIGHT - 1, 0x40FFFFFF);
            } else if (i % 2 == 0) {
                context.fill(left - 4, y, right + 4, y + LINEAGE_ROW_HEIGHT - 1, 0x20FFFFFF);
            }
            LineageRow row = rows.get(i);
            context.drawTextWithShadow(textRenderer, row.status(), right - row.statusWidth(), y + 2, row.statusColor());
            context.drawTextWithShadow(textRenderer, row.name(), left, y + 2, TreeTexts.nameColor(person.gender()));
            context.drawTextWithShadow(textRenderer, row.second(), left, y + 11, 0xAAAAAA);
        }
        context.disableScissor();
    }

    private int resultsTop() {
        return TOP + 130;
    }

    private void renderPanel(DrawContext context, int mouseX, int mouseY) {
        int x0 = 6;
        context.drawTextWithShadow(textRenderer, Text.translatable("tree.mca_vie.generations"), x0, TOP + 3, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.translatable("tree.mca_vie.up"), x0, TOP + 17, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, Text.translatable("tree.mca_vie.down"), x0, TOP + 34, 0xAAAAAA);
        int valueX = PANEL_WIDTH - 6 - 22;
        context.drawCenteredTextWithShadow(textRenderer, generationText(up), valueX, TOP + 17, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, generationText(down), valueX, TOP + 34, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.translatable("tree.mca_vie.search"), x0, TOP + 98, 0xFFFFFF);

        int top = resultsTop();
        int bottom = height - 6;
        if (searchResults.isEmpty()) {
            if (searchWaiting) {
                context.drawTextWithShadow(textRenderer, Text.translatable("tree.mca_vie.loading"), x0, top + 2, 0x808080);
            } else if (!searchText.isBlank() && searchDelay < 0) {
                context.drawTextWithShadow(textRenderer, Text.translatable("tree.mca_vie.no_result"), x0, top + 2, 0x808080);
            }
            return;
        }

        long today = today();
        boolean slots = view == View.PATH;
        int nameWidth = PANEL_WIDTH - 10 - (slots ? 30 : 0);
        context.enableScissor(0, top, PANEL_WIDTH, bottom);
        for (int i = 0; i < searchResults.size(); i++) {
            int y = top + i * RESULT_HEIGHT - (int) resultsScroll;
            if (y + RESULT_HEIGHT < top || y > bottom) {
                continue;
            }
            FamilyPerson person = searchResults.get(i);
            boolean hover = details == null && mouseX >= 2 && mouseX < PANEL_WIDTH - 2 && mouseY >= y && mouseY < y + RESULT_HEIGHT
                    && mouseY >= top && mouseY < bottom;
            if (hover) {
                context.fill(2, y, PANEL_WIDTH - 3, y + RESULT_HEIGHT - 1, 0x40FFFFFF);
            }
            context.drawTextWithShadow(textRenderer, TreeTexts.trim(textRenderer, TreeTexts.name(person), nameWidth),
                    x0, y + 1, TreeTexts.nameColor(person.gender()));
            Text years = TreeTexts.yearsLine(person, today);
            if (years != null) {
                context.drawTextWithShadow(textRenderer, TreeTexts.trim(textRenderer, years.getString(), nameWidth), x0, y + 10, 0x909090);
            }
            if (slots) {
                drawSlot(context, "A", slotX(0), y + 4, person.id().equals(pathA), mouseX, mouseY);
                drawSlot(context, "B", slotX(1), y + 4, person.id().equals(pathB), mouseX, mouseY);
            }
        }
        context.disableScissor();
    }

    private static int slotX(int slot) {
        return PANEL_WIDTH - 32 + slot * 14;
    }

    private void drawSlot(DrawContext context, String letter, int x, int y, boolean selected, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + 12 && mouseY >= y && mouseY < y + 12;
        context.fill(x, y, x + 12, y + 12, selected ? 0xFF6040A0 : hover ? 0xFF404060 : 0xFF202030);
        context.drawBorder(x, y, 12, 12, selected ? CARD_BORDER_ROOT : 0xFF707090);
        context.drawCenteredTextWithShadow(textRenderer, letter, x + 6, y + 2, 0xFFFFFF);
    }

    private boolean clickResult(double mouseX, double mouseY) {
        int top = resultsTop();
        if (mouseX < 2 || mouseX >= PANEL_WIDTH - 2 || mouseY < top || mouseY >= height - 6) {
            return false;
        }
        int index = (int) ((mouseY - top + resultsScroll) / RESULT_HEIGHT);
        if (index < 0 || index >= searchResults.size()) {
            return false;
        }
        FamilyPerson person = searchResults.get(index);
        playClick();
        if (view == View.PATH) {
            if (mouseX >= slotX(0) && mouseX < slotX(0) + 12) {
                showPath(person.id(), pathB);
            } else {
                showPath(pathA, person.id());
            }
        } else {
            showTree(person.id());
        }
        return true;
    }

    private final class Details {
        private static final int LINE = 11;
        private final List<Text> lines = new ArrayList<>();
        private final List<Integer> colors = new ArrayList<>();
        private final List<Runnable> actions = new ArrayList<>();
        private final int x;
        private final int y;
        private final int w;
        private final int h;

        private Details(FamilyPerson person, int mouseX, int mouseY) {
            long today = today();
            String g = TreeTexts.g(person.gender());
            add(Text.literal(TreeTexts.name(person)), TreeTexts.nameColor(person.gender()));
            if (PhoneSettings.treeRelations() && TreeTexts.isShown(person.relationToViewer())) {
                add(Text.literal(TreeTexts.yourRelation(person.relationToViewer(), person.gender())), 0xFFFF55);
            }
            String profession = TreeTexts.profession(person);
            if (!profession.isEmpty()) {
                add(Text.literal(profession), 0xAAAAAA);
            }

            Long birth = person.birthDay();
            Long death = person.deathDay();
            boolean dead = person.deceased() || death != null;
            List<Long> lives = person.pastLives();
            if (birth != null) {
                add(Text.translatable("tree.mca_vie.born." + g, TreeTexts.date(person.exactBirth(), birth)), 0xFFFFFF);
            } else {
                add(Text.translatable("tree.mca_vie.born_unknown"), 0x909090);
            }
            for (int i = 0; i + 1 < lives.size(); i += 2) {
                add(Text.translatable("tree.mca_vie.died." + g, TreeTexts.calendarDate(lives.get(i))), 0xC0C0C0);
                add(Text.translatable("tree.mca_vie.resurrected." + g, TreeTexts.calendarDate(lives.get(i + 1))), 0xB48CFF);
            }
            if (birth != null && !dead) {
                add(Text.translatable("tree.mca_vie.details.age", TreeTexts.age(TreeTexts.liveAge(person.exactBirth(), birth, today))), 0xFFFFFF);
            }
            if (death != null) {
                if (birth != null) {
                    add(Text.translatable("tree.mca_vie.died_age." + g, TreeTexts.date(person.exactDeath(), death),
                            TreeTexts.age(person.exactDeath() != null ? TreeTexts.ageBetween(person.exactBirth(), birth, person.exactDeath())
                                    : LifeCycle.ageInYears(birth, death))), 0xC0C0C0);
                } else {
                    add(Text.translatable("tree.mca_vie.died." + g, TreeTexts.date(person.exactDeath(), death)), 0xC0C0C0);
                }
            } else if (dead) {
                add(Text.translatable("tree.mca_vie.dead." + g), 0xC0C0C0);
            }

            List<UUID> spouses = person.spouses();
            int pastFrom = 0;
            if (!spouses.isEmpty() && spouses.get(0).equals(person.spouse())) {
                FamilyPerson current = findPerson(spouses.get(0));
                String key = "tree.mca_vie.details.spouse." + (current == null ? "m" : TreeTexts.g(current.gender()));
                add(Text.translatable(key, spouseName(spouses.get(0))), 0xFFFFFF);
                pastFrom = 1;
            }
            if (spouses.size() > pastFrom) {
                List<String> names = new ArrayList<>();
                for (UUID id : spouses.subList(pastFrom, spouses.size())) {
                    names.add(spouseName(id));
                }
                String joined = TreeTexts.trim(textRenderer, String.join(", ", names), 200);
                add(Text.translatable("tree.mca_vie.details.past_spouses", joined), 0xC0C0C0);
            }
            if (!person.children().isEmpty()) {
                add(Text.translatable("tree.mca_vie.details.children", person.children().size()), 0xFFFFFF);
            }
            if (person.happiness() >= 0 && !dead) {
                add(Text.translatable("tree.mca_vie.details.happiness", person.happiness()), 0xFFFFFF);
            }

            if (view != View.TREE || !person.id().equals(root)) {
                addAction(Text.translatable("tree.mca_vie.details.show_tree"), () -> showTree(person.id()));
            }
            if (!person.id().equals(viewer())) {
                addAction(Text.translatable("tree.mca_vie.details.show_link"), () -> showPath(viewer(), person.id()));
            }
            PhoneScreen phone = phone();
            if (phone != null) {
                boolean has = person.portrait() != null;
                addAction(Text.translatable(has ? "tree.mca_vie.details.change_portrait" : "tree.mca_vie.details.set_portrait"),
                        () -> client.setScreen(new PortraitPickerScreen(TreeScreen.this, phone.state(), person.id(), TreeTexts.name(person))));
                if (has) {
                    addAction(Text.translatable("tree.mca_vie.details.remove_portrait"), () -> {
                        PhoneClient.send(phone.number(), PhoneNetwork.OP_SET_PORTRAIT, buf -> {
                            buf.writeUuid(person.id());
                            buf.writeUuid(Util.NIL_UUID);
                        });
                        refreshPortraits();
                    });
                }
            }

            int width = 0;
            for (Text line : lines) {
                width = Math.max(width, textRenderer.getWidth(line));
            }
            w = width + 12;
            h = lines.size() * LINE + 8;
            int px = mouseX + 8;
            int py = mouseY + 4;
            if (px + w > TreeScreen.this.width - 2) {
                px = Math.max(2, mouseX - 8 - w);
            }
            if (py + h > TreeScreen.this.height - 2) {
                py = Math.max(2, TreeScreen.this.height - 2 - h);
            }
            x = px;
            y = py;
        }

        private String spouseName(UUID id) {
            FamilyPerson spouse = findPerson(id);
            if (spouse == null) {
                return knownNames.getOrDefault(id, "?");
            }
            return spouse.deceased() ? TreeTexts.name(spouse) + " ✝" : TreeTexts.name(spouse);
        }

        private void add(Text text, int color) {
            lines.add(text);
            colors.add(color);
            actions.add(null);
        }

        private void addAction(Text text, Runnable action) {
            lines.add(text);
            colors.add(0x55FFFF);
            actions.add(action);
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        }

        private int lineAt(double mouseX, double mouseY) {
            if (!contains(mouseX, mouseY)) {
                return -1;
            }
            int index = (int) ((mouseY - y - 4) / LINE);
            return index >= 0 && index < lines.size() ? index : -1;
        }

        @Nullable
        private Runnable actionAt(double mouseX, double mouseY) {
            int index = lineAt(mouseX, mouseY);
            return index < 0 ? null : actions.get(index);
        }

        private void render(DrawContext context, int mouseX, int mouseY) {
            MatrixStack matrices = context.getMatrices();
            matrices.push();
            matrices.translate(0, 0, 400);
            context.fill(x, y + 1, x + w, y + h - 1, CARD_BG);
            context.fill(x + 1, y, x + w - 1, y + 1, CARD_BG);
            context.fill(x + 1, y + h - 1, x + w - 1, y + h, CARD_BG);
            context.drawBorder(x + 1, y + 1, w - 2, h - 2, 0xFF5000FF);
            int hovered = lineAt(mouseX, mouseY);
            for (int i = 0; i < lines.size(); i++) {
                int color = colors.get(i);
                if (actions.get(i) != null && i == hovered) {
                    color = 0xFFFF55;
                }
                context.drawTextWithShadow(textRenderer, lines.get(i), x + 6, y + 5 + i * LINE, color);
            }
            matrices.pop();
        }
    }
}
