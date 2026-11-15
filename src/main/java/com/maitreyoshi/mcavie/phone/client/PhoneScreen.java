package com.maitreyoshi.mcavie.phone.client;

import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.calendar.WorldCalendar.CalendarDate;
import com.maitreyoshi.mcavie.compat.PacketByteBufs;
import com.maitreyoshi.mcavie.compat.client.ClientPlayNetworking;
import com.maitreyoshi.mcavie.phone.EmployeeChat;
import com.maitreyoshi.mcavie.phone.PhoneAgenda;
import com.maitreyoshi.mcavie.phone.PhoneEmployees;
import com.maitreyoshi.mcavie.phone.PhoneNetwork;
import com.maitreyoshi.mcavie.phone.client.PhoneClient.Message;
import com.maitreyoshi.mcavie.phone.client.PhoneClient.State;
import com.maitreyoshi.mcavie.phone.client.PhoneClient.Summary;
import com.maitreyoshi.mcavie.tree.client.TreeClient;
import com.maitreyoshi.mcavie.work.Job;
import com.maitreyoshi.mcavie.work.WorkStatus;
import com.mojang.blaze3d.systems.RenderSystem;
import java.awt.Color;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

public class PhoneScreen extends Screen {
    private static final int BEZEL = 6;
    private static final int STATUS = 14;
    private static final int HOME_BAR = 12;
    private static final int HEADER = 24;
    private static final int ROW = 30;
    private static final long TOAST_TICKS = 60;

    private static final int BODY = 0xFF0D0E12;
    private static final int FRAME = 0xFF3A3D46;
    private static int BG = 0xFF14161C;
    private static int CARD = 0xFF1F232B;
    private static int CARD_HOVER = 0xFF2A2F3A;
    private static int LINE = 0xFF272B34;
    private static final int ACCENT = 0xFF3B82F6;
    private static final int ACCENT_HOVER = 0xFF5B9BFF;
    private static final int DANGER = 0xFFE5484D;
    private static int TEXT = 0xFFF2F3F5;
    private static int MUTED = 0xFF8B909A;
    private static final int WHITE = 0xFFF2F3F5;
    private static final int[][] WALLPAPERS = {
            {0xFF1E3A8A, 0xFF6D28D9},
            {0xFFF97316, 0xFFBE185D},
            {0xFF065F46, 0xFF65A30D},
            {0xFF0E7490, 0xFF1E3A8A},
            {0xFF0F172A, 0xFF334155},
            {0xFF9D174D, 0xFFF472B6}
    };
    private static final int[] AVATARS = {0xFFE5484D, 0xFFF76B15, 0xFFFFB224, 0xFF30A46C, 0xFF12A594, 0xFF0090FF, 0xFF6E56CF, 0xFFD6409F};

    private enum View { LOCK, HOME, CONTACTS, ADD_CONTACT, MESSAGES, NEW_MESSAGE, CONVERSATION, SETTINGS, SET_CODE, CAMERA, TREE, GALLERY, PICK_CONTACT, FOLDER_NAME, MAP, WALLPAPER, CROP, AGENDA, AGENDA_EDIT, HOME_APPS, EMPLOYEES, EMPLOYEE, EMPLOYEE_CHAT }

    private record App(String key, View view, IntSupplier badge) {
        Identifier texture() {
            return new Identifier("mca_vie", "textures/gui/phone/app_" + key + ".png");
        }
    }

    private record Hit(int x, int y, int w, int h, Runnable action) {
    }

    private record Cell(int x, int y, int w, int h, UUID id) {
    }

    private record Drop(int x, int y, int w, int h, int folder) {
    }

    private final List<App> apps = List.of(
            new App("messages", View.MESSAGES, () -> this.state.unread()),
            new App("contacts", View.CONTACTS, () -> 0),
            new App("camera", View.CAMERA, () -> 0),
            new App("gallery", View.GALLERY, () -> 0),
            new App("tree", View.TREE, () -> 0),
            new App("map", View.MAP, () -> 0),
            new App("agenda", View.AGENDA, () -> this.eventsToday()),
            new App("employees", View.EMPLOYEES, () -> (int) this.employees.stream().filter(PhoneScreen::employeeAlert).count()),
            new App("settings", View.SETTINGS, () -> 0)
    );

    private final List<Hit> hits = new ArrayList<>();
    private final List<Cell> cells = new ArrayList<>();
    private final List<Drop> drops = new ArrayList<>();
    private int folder;
    private int editingFolder;
    private String folderInput = "";
    private UUID pressed;
    private double pressX;
    private double pressY;
    private boolean dragging;
    private State state;
    private View view;
    private int other = -1;
    private UUID photo;
    private List<Message> messages = List.of();
    private int scroll;
    private String draft = "";
    private String numberInput = "";
    private String codeInput = "";
    private Text toast;
    private long toastUntil;
    private long ticks;
    private int mouseX;
    private int mouseY;
    private TextFieldWidget mainField;
    private static final float[] MAP_SCALES = {0.5f, 1f, 2f, 3f};
    private int mapZoom = 1;
    private double mapPanX;
    private double mapPanZ;
    private boolean mapDragging;
    private float mapAngle;
    private static final float MAX_CROP_ZOOM = 4f;
    private UUID cropId;
    private float cropZoom = 1f;
    private float cropU = 0.5f;
    private float cropV = 0.5f;
    private float cropScale = 1f;
    private int cropWidth = 1;
    private int cropHeight = 1;

    private record Crop(UUID id, float zoom, float u, float v) {
    }

    private LocalDate agendaMonth;
    private long agendaSelected = Long.MIN_VALUE;
    private String eventInput = "";
    private boolean eventYearly;
    private List<PhoneEmployees.Employee> employees = List.of();
    private boolean employeesLoaded;
    private UUID employee;
    private boolean confirmFire;
    private List<EmployeeChat.Line> employeeChat = List.of();
    private boolean employeeChatLoaded;
    // réponse du PNJ retenue quelques instants pendant qu'il « écrit »
    private boolean awaitingReply;
    private List<EmployeeChat.Line> pendingChat;
    private long revealTick;

    public PhoneScreen(State state) {
        super(Text.translatable("item.mca_vie.phone"));
        PhoneSettings.load();
        applyTheme();
        this.state = state;
        this.view = state.access() ? View.HOME : View.LOCK;
    }

    public int number() {
        return state.number();
    }

    public State state() {
        return state;
    }

    public void applyState(State newState) {
        boolean wasLocked = !state.access();
        state = newState;
        if (!state.access()) {
            if (view != View.LOCK) {
                switchTo(View.LOCK);
            }
            return;
        }
        if (wasLocked || view == View.LOCK) {
            switchTo(View.HOME);
            return;
        }
        if (view == View.PICK_CONTACT && state.gallery().stream().noneMatch(p -> p.id().equals(photo))) {
            switchTo(View.GALLERY);
            return;
        }
        if (folder != 0 && !state.folders().containsKey(folder)) {
            folder = 0;
            scroll = 0;
        }
        if (view == View.SET_CODE) {
            switchTo(View.SETTINGS);
            return;
        }
        if (view == View.CONVERSATION) {
            for (Summary summary : state.conversations()) {
                if (summary.number() == other && summary.unread() > 0) {
                    requestConversation();
                }
            }
        }
    }

    public void applyConversation(int number, List<Message> list) {
        if (view == View.CONVERSATION && number == other) {
            messages = list;
            scroll = 0;
        }
    }

    public void showError(Text text) {
        toast = text;
        toastUntil = ticks + TOAST_TICKS;
    }

    @Override
    public void tick() {
        ticks++;
        if ((view == View.EMPLOYEES || view == View.EMPLOYEE) && ticks % 100 == 0 || view == View.HOME && ticks % 400 == 1) {
            requestEmployees();
        }
        if (pendingChat != null && ticks >= revealTick) {
            employeeChat = pendingChat;
            pendingChat = null;
            scroll = 0;
        }
        if (mainField != null) {
            mainField.tick();
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static float ui() {
        return PhoneSettings.textScale();
    }

    private int lw() {
        return Math.round(width / ui());
    }

    private int lh() {
        return Math.round(height / ui());
    }

    private void scissor(DrawContext context, int x1, int y1, int x2, int y2) {
        float s = ui();
        context.enableScissor(Math.round(x1 * s), Math.round(y1 * s), Math.round(x2 * s), Math.round(y2 * s));
    }

    // Ombre seulement pour les textes clairs : en theme clair, l'ombre noire sous un texte fonce le rend illisible.
    private static boolean shadow(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return !PhoneSettings.light || (r * 299 + g * 587 + b * 114) / 1000 > 160;
    }

    private void centered(DrawContext context, String text, int x, int y, int color) {
        context.drawText(textRenderer, text, x - textRenderer.getWidth(text) / 2, y, color, shadow(color));
    }

    private void centered(DrawContext context, Text text, int x, int y, int color) {
        context.drawText(textRenderer, text, x - textRenderer.getWidth(text) / 2, y, color, shadow(color));
    }

    private void centered(DrawContext context, OrderedText text, int x, int y, int color) {
        context.drawText(textRenderer, text, x - textRenderer.getWidth(text) / 2, y, color, shadow(color));
    }

    private static void applyTheme() {
        boolean light = PhoneSettings.light;
        BG = light ? 0xFFF2F3F7 : 0xFF14161C;
        CARD = light ? 0xFFFFFFFF : 0xFF1F232B;
        CARD_HOVER = light ? 0xFFE4E7ED : 0xFF2A2F3A;
        LINE = light ? 0xFFDCDFE6 : 0xFF272B34;
        TEXT = light ? 0xFF15171C : 0xFFF2F3F5;
        MUTED = light ? 0xFF6B7080 : 0xFF8B909A;
    }

    private int phoneHeight() {
        return Math.min(300, lh() - 16);
    }

    private int phoneWidth() {
        return Math.min(lw() - 8, Math.max(150, Math.round(phoneHeight() * 0.56f)));
    }

    private int phoneLeft() {
        return (lw() - phoneWidth()) / 2;
    }

    private int phoneTop() {
        return (lh() - phoneHeight()) / 2;
    }

    private int sx() {
        return phoneLeft() + BEZEL;
    }

    private int sy() {
        return phoneTop() + BEZEL;
    }

    private int sw() {
        return phoneWidth() - 2 * BEZEL;
    }

    private int sh() {
        return phoneHeight() - 2 * BEZEL;
    }

    private int contentTop() {
        return sy() + STATUS + (view == View.HOME || view == View.LOCK ? 0 : HEADER);
    }

    private int contentBottom() {
        return sy() + sh() - HOME_BAR;
    }

    private void switchTo(View next) {
        view = next;
        scroll = 0;
        pressed = null;
        dragging = false;
        mapDragging = false;
        if (next == View.MAP) {
            mapPanX = 0;
            mapPanZ = 0;
            mapZoom = PhoneSettings.mapZoom;
        }
        if (next == View.HOME) {
            folder = 0;
        }
        confirmFire = false;
        if (next == View.EMPLOYEES) {
            requestEmployees();
        }
        clearAndInit();
    }

    private View parent() {
        return switch (view) {
            case ADD_CONTACT -> View.CONTACTS;
            case NEW_MESSAGE, CONVERSATION -> View.MESSAGES;
            case SET_CODE, WALLPAPER, HOME_APPS -> View.SETTINGS;
            case CROP -> View.WALLPAPER;
            case PICK_CONTACT, FOLDER_NAME -> View.GALLERY;
            case AGENDA_EDIT -> View.AGENDA;
            case EMPLOYEE -> View.EMPLOYEES;
            case EMPLOYEE_CHAT -> View.EMPLOYEE;
            default -> View.HOME;
        };
    }

    private void send(int op, Consumer<PacketByteBuf> body) {
        PhoneClient.send(state.number(), op, body);
    }

    private void requestConversation() {
        int target = other;
        send(PhoneNetwork.OP_CONVERSATION, buf -> buf.writeVarInt(target));
    }

    private void openConversation(int number) {
        other = number;
        messages = List.of();
        draft = "";
        switchTo(View.CONVERSATION);
        requestConversation();
    }

    @Override
    protected void init() {
        mainField = null;
        switch (view) {
            case CONVERSATION -> mainField = field(sx() + 14, contentBottom() - 20, sw() - 48, PhoneNetwork.MAX_TEXT, draft, s -> draft = s, false);
            case EMPLOYEE_CHAT -> mainField = field(sx() + 14, contentBottom() - 20, sw() - 48, EmployeeChat.MAX_TEXT, draft, s -> draft = s, false);
            case FOLDER_NAME -> mainField = field(sx() + 14, contentTop() + 30, sw() - 28, PhoneNetwork.MAX_FOLDER_NAME, folderInput, s -> folderInput = s, false);
            case AGENDA_EDIT -> mainField = field(sx() + 14, contentTop() + 30, sw() - 28, PhoneNetwork.MAX_EVENT_TITLE, eventInput, s -> eventInput = s, false);
            default -> {
            }
        }
        if (mainField != null) {
            setInitialFocus(mainField);
        }
    }

    private TextFieldWidget field(int x, int y, int w, int max, String value, Consumer<String> listener, boolean digits) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, w, 10, Text.empty());
        field.setDrawsBackground(false);
        field.setMaxLength(max);
        field.setEditableColor(TEXT);
        if (digits) {
            field.setTextPredicate(s -> s.chars().allMatch(c -> c >= '0' && c <= '9'));
        }
        field.setText(value);
        field.setChangedListener(listener);
        addDrawableChild(field);
        return field;
    }

    private static int parseNumber(String text) {
        if (text.isEmpty() || text.length() > 3) {
            return -1;
        }
        int number = Integer.parseInt(text);
        return PhoneNetwork.validNumber(number) ? number : -1;
    }

    private void typeDigit(int digit) {
        boolean number = view == View.NEW_MESSAGE || view == View.ADD_CONTACT;
        String current = number ? numberInput : codeInput;
        if (current.length() >= (number ? 3 : PhoneNetwork.CODE_LENGTH)) {
            return;
        }
        current += digit;
        if (number) {
            numberInput = current;
            return;
        }
        codeInput = current;
        if (current.length() == PhoneNetwork.CODE_LENGTH) {
            String code = current;
            codeInput = "";
            send(view == View.LOCK ? PhoneNetwork.OP_UNLOCK : PhoneNetwork.OP_SET_CODE, buf -> buf.writeString(code));
        }
    }

    private void eraseDigit() {
        boolean number = view == View.NEW_MESSAGE || view == View.ADD_CONTACT;
        if (number && !numberInput.isEmpty()) {
            numberInput = numberInput.substring(0, numberInput.length() - 1);
        } else if (!number && !codeInput.isEmpty()) {
            codeInput = codeInput.substring(0, codeInput.length() - 1);
        }
    }

    private boolean usesKeypad() {
        return view == View.LOCK || view == View.SET_CODE || view == View.NEW_MESSAGE || view == View.ADD_CONTACT;
    }

    private void addContact() {
        int number = parseNumber(numberInput);
        if (number < 0) {
            return;
        }
        send(PhoneNetwork.OP_ADD_CONTACT, buf -> buf.writeVarInt(number));
        switchTo(View.CONTACTS);
    }

    private void sendMessage() {
        String text = draft.trim();
        if (text.isEmpty() || other < 0) {
            return;
        }
        int target = other;
        send(PhoneNetwork.OP_SEND, buf -> {
            buf.writeVarInt(target);
            buf.writeString(text);
        });
        draft = "";
        if (mainField != null) {
            mainField.setText("");
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        x /= ui();
        y /= ui();
        if (super.mouseClicked(x, y, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        for (Hit hit : List.copyOf(hits)) {
            if (x >= hit.x() && x < hit.x() + hit.w() && y >= hit.y() && y < hit.y() + hit.h()) {
                hit.action().run();
                return true;
            }
        }
        if ((view == View.MAP || view == View.CROP) && x >= sx() && x < sx() + sw() && y >= contentTop() && y < contentBottom()) {
            mapDragging = true;
            return true;
        }
        if (view == View.GALLERY) {
            for (Cell cell : cells) {
                if (x >= cell.x() && x < cell.x() + cell.w() && y >= cell.y() && y < cell.y() + cell.h()) {
                    pressed = cell.id();
                    pressX = x;
                    pressY = y;
                    dragging = false;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        x /= ui();
        y /= ui();
        dx /= ui();
        dy /= ui();
        if (mapDragging && button == 0 && view == View.CROP) {
            cropU -= (float) (dx / cropScale / cropWidth);
            cropV -= (float) (dy / cropScale / cropHeight);
            return true;
        }
        if (mapDragging && button == 0) {
            float scale = MAP_SCALES[mapZoom];
            double angle = Math.toRadians(-mapAngle);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            mapPanX -= (dx * cos - dy * sin) / scale;
            mapPanZ -= (dx * sin + dy * cos) / scale;
            return true;
        }
        if (pressed != null && button == 0) {
            if (!dragging && Math.abs(x - pressX) + Math.abs(y - pressY) > 4) {
                dragging = true;
            }
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        x /= ui();
        y /= ui();
        if (mapDragging && button == 0) {
            mapDragging = false;
            return true;
        }
        if (pressed != null && button == 0) {
            UUID id = pressed;
            boolean wasDragging = dragging;
            pressed = null;
            dragging = false;
            if (!wasDragging) {
                openPhoto(id, true);
                return true;
            }
            for (Drop drop : drops) {
                if (x >= drop.x() && x < drop.x() + drop.w() && y >= drop.y() && y < drop.y() + drop.h()) {
                    int target = drop.folder();
                    if (target != folder) {
                        send(PhoneNetwork.OP_MOVE_PHOTO, buf -> {
                            buf.writeUuid(id);
                            buf.writeVarInt(target);
                        });
                    }
                    break;
                }
            }
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && view != View.HOME && view != View.LOCK) {
            goBack();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            switch (view) {
                case CONVERSATION -> sendMessage();
                case EMPLOYEE_CHAT -> sendEmployeeChat(draft);
                case ADD_CONTACT -> addContact();
                case FOLDER_NAME -> submitFolder();
                case AGENDA_EDIT -> submitEvent();
                case NEW_MESSAGE -> {
                    int number = parseNumber(numberInput);
                    if (number >= 0) {
                        openConversation(number);
                    }
                }
                default -> {
                }
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && usesKeypad()) {
            eraseDigit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (usesKeypad() && chr >= '0' && chr <= '9') {
            typeDigit(chr - '0');
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double amount) {
        if (view == View.CROP) {
            cropZoom = MathHelper.clamp(cropZoom * (amount > 0 ? 1.1f : 1 / 1.1f), 1f, MAX_CROP_ZOOM);
            return true;
        }
        if (view == View.MAP) {
            mapZoom = Math.max(0, Math.min(MAP_SCALES.length - 1, mapZoom + (amount > 0 ? 1 : -1)));
            return true;
        }
        if (view == View.CONVERSATION || view == View.EMPLOYEE_CHAT) {
            scroll = Math.max(0, scroll + (int) (amount * 12));
        } else {
            scroll = Math.max(0, scroll - (int) (amount * 12));
        }
        return true;
    }

    @Override
    public void render(DrawContext context, int realMouseX, int realMouseY, float delta) {
        int mouseX = Math.round(realMouseX / ui());
        int mouseY = Math.round(realMouseY / ui());
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        hits.clear();
        cells.clear();
        drops.clear();
        renderBackground(context);
        context.getMatrices().push();
        context.getMatrices().scale(ui(), ui(), 1);

        int px = phoneLeft();
        int py = phoneTop();
        int pw = phoneWidth();
        int ph = phoneHeight();
        round(context, px - 1, py - 1, pw + 2, ph + 2, 17, FRAME);
        round(context, px, py, pw, ph, 16, BODY);
        context.fill(px - 2, py + 50, px - 1, py + 66, FRAME);
        context.fill(px + pw + 1, py + 40, px + pw + 2, py + 70, FRAME);

        int sx = sx();
        int sy = sy();
        int sw = sw();
        int sh = sh();
        if (view == View.HOME || view == View.LOCK) {
            drawWallpaper(context, sx, sy, sw, sh);
        } else if (view == View.CROP) {
            drawCropPreview(context, sx, sy, sw, sh);
        } else {
            round(context, sx, sy, sw, sh, 11, BG);
        }

        switch (view) {
            case LOCK -> renderLock(context);
            case HOME -> renderHome(context);
            case CONTACTS -> renderContacts(context);
            case ADD_CONTACT -> renderAddContact(context);
            case MESSAGES -> renderMessages(context);
            case NEW_MESSAGE -> renderNewMessage(context);
            case CONVERSATION -> renderConversation(context);
            case SETTINGS -> renderSettings(context);
            case HOME_APPS -> renderHomeApps(context);
            case SET_CODE -> renderSetCode(context);
            case GALLERY -> renderGallery(context);
            case PICK_CONTACT -> renderPickContact(context);
            case FOLDER_NAME -> renderFolderName(context);
            case MAP -> renderMap(context);
            case WALLPAPER -> renderWallpaper(context);
            case CROP -> renderCrop(context);
            case AGENDA -> renderAgenda(context);
            case AGENDA_EDIT -> renderAgendaEdit(context);
            case EMPLOYEES -> renderEmployees(context);
            case EMPLOYEE -> renderEmployee(context);
            case EMPLOYEE_CHAT -> renderEmployeeChat(context);
            case CAMERA, TREE -> {
            }
        }

        renderStatusBar(context);
        int barWidth = sw / 3;
        int barX = sx + (sw - barWidth) / 2;
        int barY = sy + sh - 6;
        boolean barHover = view != View.LOCK && hovered(barX - 6, barY - 4, barWidth + 12, 9);
        context.fill(barX, barY, barX + barWidth, barY + 2, barHover ? 0xFFFFFFFF : 0xB0FFFFFF);
        if (view != View.LOCK && view != View.HOME) {
            hits.add(new Hit(barX - 6, barY - 4, barWidth + 12, 9, () -> switchTo(View.HOME)));
        }

        super.render(context, mouseX, mouseY, delta);

        if (dragging && pressed != null) {
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 200);
            context.fill(mouseX - 17, mouseY - 10, mouseX + 17, mouseY + 10, 0xFFFFFFFF);
            drawPhoto(context, pressed, mouseX - 16, mouseY - 9, 32, 18);
            context.getMatrices().pop();
        }

        if (toast != null && ticks < toastUntil) {
            List<OrderedText> lines = textRenderer.wrapLines(toast, sw - 28);
            int th = lines.size() * 10 + 8;
            int ty = contentBottom() - th - 30;
            round(context, sx + 8, ty, sw - 16, th, 6, 0xF0303540);
            for (int i = 0; i < lines.size(); i++) {
                centered(context, lines.get(i), sx + sw / 2, ty + 5 + i * 10, 0xFFFF8080);
            }
        }
        context.getMatrices().pop();
    }

    private void renderMap(DrawContext context) {
        renderHeader(context, Text.translatable("gui.mca_vie.phone.app.map"), true);
        int sx = sx();
        int sw = sw();
        int top = contentTop();
        int bottom = contentBottom();
        context.fill(sx, top, sx + sw, bottom, 0xFF0A0B0E);
        if (client == null || client.world == null || client.player == null
                || client.world.getDimension().hasCeiling() || !PhoneMap.update(client)) {
            centered(context, fit(Text.translatable("gui.mca_vie.phone.map.no_signal").getString(), sw - 12), sx + sw / 2, (top + bottom) / 2 - 4, MUTED);
            return;
        }
        float scale = MAP_SCALES[mapZoom];
        float tickDelta = client.getTickDelta();
        double px = MathHelper.lerp(tickDelta, client.player.prevX, client.player.getX());
        double pz = MathHelper.lerp(tickDelta, client.player.prevZ, client.player.getZ());
        double viewX = px + mapPanX;
        double viewZ = pz + mapPanZ;
        float cx = sx + sw / 2f;
        float cy = (top + bottom) / 2f;
        int size = PhoneMap.size();
        // Carte orientee : le regard du joueur pointe vers le haut de l'ecran.
        mapAngle = PhoneSettings.mapRotate ? MathHelper.wrapDegrees(MathHelper.lerp(tickDelta, client.player.prevYaw, client.player.getYaw()) + 180f) : 0f;
        scissor(context, sx, top, sx + sw, bottom);
        context.getMatrices().push();
        context.getMatrices().translate(cx, cy, 0);
        context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(mapAngle));
        context.getMatrices().scale(scale, scale, 1);
        context.getMatrices().translate((float) (PhoneMap.originX() - viewX), (float) (PhoneMap.originZ() - viewZ), 0);
        context.drawTexture(PhoneMap.TEXTURE, 0, 0, 0, 0, size, size, size, size);
        context.getMatrices().pop();

        double rad = Math.toRadians(mapAngle);
        double ox = (px - viewX) * scale;
        double oz = (pz - viewZ) * scale;
        float hx = cx + (float) (ox * Math.cos(rad) - oz * Math.sin(rad));
        float hz = cy + (float) (ox * Math.sin(rad) + oz * Math.cos(rad));
        context.getMatrices().push();
        context.getMatrices().translate(hx, hz, 0);
        context.fill(-7, -7, 7, 7, 0xFFFFFFFF);
        PlayerSkinDrawer.draw(context, client.player.getSkinTexture(), -6, -6, 12);
        context.getMatrices().pop();
        context.disableScissor();

        round(context, sx + 4, top + 4, 12, 12, 6, 0xC0000000);
        context.getMatrices().push();
        context.getMatrices().translate(sx + 10, top + 10, 0);
        context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(mapAngle));
        centered(context, "N", 0, -4, mapAngle == 0f ? WHITE : 0xFFFF6B6B);
        context.getMatrices().pop();
        String coords = "X " + MathHelper.floor(px) + "  Z " + MathHelper.floor(pz);
        int cw = textRenderer.getWidth(coords) + 8;
        round(context, sx + 4, bottom - 15, cw, 12, 5, 0xC0000000);
        context.drawText(textRenderer, coords, sx + 8, bottom - 13, WHITE, false);
        if (Math.abs(mapPanX) > 1 || Math.abs(mapPanZ) > 1) {
            int bx = sx + sw - 18;
            int by = bottom - 18;
            boolean hover = hovered(bx, by, 14, 14);
            round(context, bx, by, 14, 14, 7, hover ? ACCENT_HOVER : ACCENT);
            round(context, bx + 4, by + 4, 6, 6, 3, WHITE);
            hits.add(new Hit(bx, by, 14, 14, () -> {
                mapPanX = 0;
                mapPanZ = 0;
            }));
        }
    }

    private void renderStatusBar(DrawContext context) {
        int sx = sx();
        int sy = sy();
        int sw = sw();
        String clock = client != null && client.world != null ? clock(WorldCalendar.flowingNow(client.world)) : "";
        int ink = view == View.HOME || view == View.LOCK || view == View.CROP || view == View.MAP ? WHITE : TEXT;
        context.drawText(textRenderer, clock, sx + 10, sy + 4, ink, false);
        round(context, sx + sw / 2 - 18, sy + 2, 36, 9, 4, 0xFF000000);
        context.fill(sx + sw / 2 + 10, sy + 5, sx + sw / 2 + 13, sy + 8, 0xFF1E2A44);
        int bx = sx + sw - 24;
        context.fill(bx, sy + 4, bx + 14, sy + 11, 0xFFB0B4BC);
        context.fill(bx + 1, sy + 5, bx + 13, sy + 10, BODY);
        context.fill(bx + 2, sy + 6, bx + 11, sy + 9, 0xFF34D399);
        context.fill(bx + 14, sy + 6, bx + 15, sy + 9, 0xFFB0B4BC);
        for (int i = 0; i < 4; i++) {
            context.fill(bx - 14 + i * 3, sy + 10 - (i + 1) * 2, bx - 12 + i * 3, sy + 11, ink);
        }
    }

    private static String clock(CalendarDate date) {
        return String.format("%02d:%02d", date.hour(), date.minute());
    }

    private void renderHeader(DrawContext context, Text title, boolean back) {
        int sx = sx();
        int y = sy() + STATUS;
        int sw = sw();
        context.fill(sx, y + HEADER - 1, sx + sw, y + HEADER, LINE);
        if (back) {
            boolean hover = hovered(sx + 2, y + 2, 22, HEADER - 4);
            drawChevron(context, sx + 10, y + HEADER / 2, hover ? ACCENT_HOVER : ACCENT);
            hits.add(new Hit(sx + 2, y + 2, 22, HEADER - 4, this::goBack));
        }
        String trimmed = fit(title.getString(), sw - 56);
        centered(context, Text.literal(trimmed).formatted(Formatting.BOLD), sx + sw / 2, y + 8, TEXT);
    }

    private void renderLock(DrawContext context) {
        int sx = sx();
        int sw = sw();
        int y = sy() + STATUS + 10;
        String clock = client != null && client.world != null ? clock(WorldCalendar.flowingNow(client.world)) : "";
        context.getMatrices().push();
        context.getMatrices().translate(sx + sw / 2f, y, 0);
        context.getMatrices().scale(2.5f, 2.5f, 1);
        centered(context, clock, 0, 0, WHITE);
        context.getMatrices().pop();
        y += 30;
        drawLockIcon(context, sx + sw / 2 - 4, y);
        y += 14;
        centered(context, fit(Text.translatable("gui.mca_vie.phone.enter_code").getString(), sw - 12), sx + sw / 2, y, WHITE);
        y += 14;
        drawDots(context, y, codeInput.length());
        renderKeypad(context, y + 14, contentBottom() - 4, true);
    }

    private void drawDots(DrawContext context, int y, int filled) {
        int sx = sx();
        int sw = sw();
        int start = sx + sw / 2 - (PhoneNetwork.CODE_LENGTH * 12 - 4) / 2;
        for (int i = 0; i < PhoneNetwork.CODE_LENGTH; i++) {
            int dx = start + i * 12;
            if (i < filled) {
                round(context, dx, y, 8, 8, 4, TEXT);
            } else {
                round(context, dx, y, 8, 8, 4, 0xFFD0D3DA);
                round(context, dx + 1, y + 1, 6, 6, 3, 0xFF3A3F55);
            }
        }
    }

    private void renderKeypad(DrawContext context, int top, int bottom, boolean light) {
        int sx = sx();
        int sw = sw();
        int size = Math.max(14, Math.min(26, Math.min((sw - 40) / 3, (bottom - top - 12) / 4)));
        int gap = Math.max(3, Math.min(10, (sw - size * 3) / 5));
        int left = sx + (sw - size * 3 - gap * 2) / 2;
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "<"};
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            if (key.isEmpty()) {
                continue;
            }
            int kx = left + (i % 3) * (size + gap);
            int ky = top + (i / 3) * (size + 4);
            boolean hover = hovered(kx, ky, size, size);
            if (key.equals("<")) {
                drawBackspace(context, kx + size / 2 - 6, ky + size / 2 - 4, hover ? (light ? WHITE : TEXT) : (light ? 0xFFC8CCD4 : MUTED));
                hits.add(new Hit(kx, ky, size, size, this::eraseDigit));
                continue;
            }
            int color = light ? (hover ? 0x60FFFFFF : 0x35FFFFFF) : (hover ? CARD_HOVER : CARD);
            round(context, kx, ky, size, size, size / 2, color);
            centered(context, key, kx + size / 2, ky + (size - 8) / 2, light ? WHITE : TEXT);
            int digit = Integer.parseInt(key);
            hits.add(new Hit(kx, ky, size, size, () -> typeDigit(digit)));
        }
    }

    private void renderHome(DrawContext context) {
        int sx = sx();
        int sw = sw();
        int top = sy() + STATUS;
        CalendarDate date = client != null && client.world != null ? WorldCalendar.flowingNow(client.world) : null;
        if (date != null) {
            context.getMatrices().push();
            context.getMatrices().translate(sx + sw / 2f, top + 8, 0);
            context.getMatrices().scale(2f, 2f, 1);
            centered(context, clock(date), 0, 0, WHITE);
            context.getMatrices().pop();
            Text day = Text.translatable("calendar.mca_vie.day." + date.dayOfWeek().getValue());
            centered(context, fit(day.getString() + " " + date.day() + "/" + date.month(), sw - 12), sx + sw / 2, top + 28, 0xFFDDE2F0);
        }
        int columns = 3;
        int icon = Math.min(30, (sw - 24) / columns - 10);
        int cell = (sw - 16) / columns;
        int gridTop = top + 48;
        List<App> shown = homeApps().stream().filter(a -> a.key().equals("settings") || !PhoneSettings.hidden.contains(a.key())).toList();
        for (int i = 0; i < shown.size(); i++) {
            App app = shown.get(i);
            int cx = sx + 8 + (i % columns) * cell + (cell - icon) / 2;
            int cy = gridTop + (i / columns) * (icon + 22);
            drawApp(context, app, cx, cy, icon);
            String label = fit(Text.translatable("gui.mca_vie.phone.app." + app.key()).getString(), (int) ((cell - 2) / 0.75f));
            context.getMatrices().push();
            context.getMatrices().translate(cx + icon / 2f, cy + icon + 3, 0);
            context.getMatrices().scale(0.75f, 0.75f, 1);
            centered(context, label, 0, 0, WHITE);
            context.getMatrices().pop();
            hits.add(new Hit(cx - 2, cy - 2, icon + 4, icon + 14, () -> {
                PhoneSettings.playAppClick();
                if (app.view() == View.CAMERA) {
                    CameraMode.start(state);
                } else if (app.view() == View.TREE) {
                    if (client != null && client.player != null) {
                        TreeClient.open(client.player.getUuid(), this);
                    }
                } else {
                    switchTo(app.view());
                }
            }));
        }
    }

    private void drawApp(DrawContext context, App app, int x, int y, int size) {
        boolean hover = hovered(x, y, size, size);
        if (hover) {
            round(context, x - 2, y - 2, size + 4, size + 4, size / 4 + 2, 0x50FFFFFF);
        }
        RenderSystem.enableBlend();
        context.drawTexture(app.texture(), x, y, size, size, 0, 0, 128, 128, 128, 128);
        RenderSystem.disableBlend();
        int badge = app.badge().getAsInt();
        if (badge > 0) {
            String text = badge > 99 ? "99+" : String.valueOf(badge);
            int bw = Math.max(11, textRenderer.getWidth(text) + 5);
            round(context, x + size - bw + 4, y - 4, bw, 11, 5, DANGER);
            centered(context, text, x + size - bw / 2 + 4, y - 2, WHITE);
        }
    }

    private void renderContacts(DrawContext context) {
        renderHeader(context, Text.translatable("gui.mca_vie.phone.app.contacts"), true);
        List<Integer> contacts = new ArrayList<>(state.contacts());
        contacts.sort(Comparator.comparing(state::nameOf, String.CASE_INSENSITIVE_ORDER));
        int top = contentTop();
        int bottom = contentBottom();
        if (contacts.isEmpty()) {
            centered(context, Text.translatable("gui.mca_vie.phone.no_contacts"), sx() + sw() / 2, top + 30, MUTED);
        }
        int maxScroll = Math.max(0, contacts.size() * ROW - (bottom - top));
        scroll = Math.min(scroll, maxScroll);
        int sx = sx();
        int sw = sw();
        scissor(context, sx, top, sx + sw, bottom);
        for (int i = 0; i < contacts.size(); i++) {
            int number = contacts.get(i);
            int ry = top + i * ROW - scroll;
            if (ry + ROW < top || ry > bottom) {
                continue;
            }
            boolean rowHover = visible(ry, ROW, top, bottom) && hovered(sx, ry, sw - 24, ROW);
            if (rowHover) {
                context.fill(sx, ry, sx + sw, ry + ROW, CARD_HOVER);
            }
            drawAvatar(context, sx + 8, ry + 5, 20, number);
            context.drawText(textRenderer, fit(state.nameOf(number), sw - 64), sx + 34, ry + 6, TEXT, false);
            context.drawText(textRenderer, String.format("%03d", number), sx + 34, ry + 17, MUTED, false);
            context.fill(sx + 34, ry + ROW - 1, sx + sw, ry + ROW, LINE);
            boolean trashHover = hovered(sx + sw - 22, ry + 7, 16, 16);
            drawCross(context, sx + sw - 18, ry + 11, trashHover ? DANGER : MUTED);
            if (visible(ry, ROW, top, bottom)) {
                hits.add(new Hit(sx + sw - 22, ry + 7, 16, 16, () -> send(PhoneNetwork.OP_REMOVE_CONTACT, buf -> buf.writeVarInt(number))));
                hits.add(new Hit(sx, ry, sw - 24, ROW, () -> openConversation(number)));
            }
        }
        context.disableScissor();
        drawFab(context, () -> {
            numberInput = "";
            switchTo(View.ADD_CONTACT);
        });
    }

    private void renderAddContact(DrawContext context) {
        renderHeader(context, Text.translatable("gui.mca_vie.phone.add"), true);
        int number = parseNumber(numberInput);
        renderNumberPad(context, Text.translatable("gui.mca_vie.phone.add_hint"), Text.translatable("gui.mca_vie.phone.add_button"), number >= 0, this::addContact);
    }

    private void renderMessages(DrawContext context) {
        renderHeader(context, Text.translatable("gui.mca_vie.phone.app.messages"), true);
        List<Summary> conversations = state.conversations();
        int top = contentTop();
        int bottom = contentBottom();
        int sx = sx();
        int sw = sw();
        if (conversations.isEmpty()) {
            centered(context, Text.translatable("gui.mca_vie.phone.no_messages"), sx + sw / 2, top + 30, MUTED);
        }
        int maxScroll = Math.max(0, conversations.size() * ROW - (bottom - top));
        scroll = Math.min(scroll, maxScroll);
        scissor(context, sx, top, sx + sw, bottom);
        for (int i = 0; i < conversations.size(); i++) {
            Summary summary = conversations.get(i);
            int ry = top + i * ROW - scroll;
            if (ry + ROW < top || ry > bottom) {
                continue;
            }
            String name = state.nameOf(summary.number());
            boolean unread = summary.unread() > 0;
            if (visible(ry, ROW, top, bottom) && hovered(sx, ry, sw, ROW)) {
                context.fill(sx, ry, sx + sw, ry + ROW, CARD_HOVER);
            }
            drawAvatar(context, sx + 8, ry + 5, 20, summary.number());
            String time = clock(WorldCalendar.flowingFromTicks(summary.time()));
            int timeWidth = textRenderer.getWidth(time);
            Text title = Text.literal(fit(name, sw - 50 - timeWidth));
            context.drawText(textRenderer, unread ? title.copy().formatted(Formatting.BOLD) : title, sx + 34, ry + 6, TEXT, false);
            context.drawText(textRenderer, time, sx + sw - 8 - timeWidth, ry + 6, unread ? ACCENT : MUTED, false);
            String body = summary.photo() ? Text.translatable("gui.mca_vie.phone.photo").getString() : summary.preview();
            String preview = (summary.outgoing() ? Text.translatable("gui.mca_vie.phone.you").getString() + " " : "") + body;
            String count = summary.unread() > 99 ? "99+" : String.valueOf(summary.unread());
            int bw = Math.max(10, textRenderer.getWidth(count) + 4);
            context.drawText(textRenderer, fit(preview, sw - 42 - (unread ? bw + 4 : 0)), sx + 34, ry + 17, unread ? TEXT : MUTED, false);
            if (unread) {
                round(context, sx + sw - 8 - bw, ry + 16, bw, 10, 5, ACCENT);
                centered(context, count, sx + sw - 8 - bw / 2, ry + 17, WHITE);
            }
            context.fill(sx + 34, ry + ROW - 1, sx + sw, ry + ROW, LINE);
            if (visible(ry, ROW, top, bottom)) {
                hits.add(new Hit(sx, ry, sw, ROW, () -> openConversation(summary.number())));
            }
        }
        context.disableScissor();
        drawFab(context, () -> {
            numberInput = "";
            switchTo(View.NEW_MESSAGE);
        });
    }

    private void renderNewMessage(DrawContext context) {
        renderHeader(context, Text.translatable("gui.mca_vie.phone.new_message"), true);
        int number = parseNumber(numberInput);
        renderNumberPad(context, Text.translatable("gui.mca_vie.phone.contact_number"), Text.translatable("gui.mca_vie.phone.open"), number >= 0, () -> openConversation(number));
    }

    private void renderNumberPad(DrawContext context, Text hint, Text action, boolean valid, Runnable onValid) {
        int sx = sx();
        int sw = sw();
        int y = contentTop() + 8;
        centered(context, fit(hint.getString(), sw - 12), sx + sw / 2, y, MUTED);
        String shown = numberInput.isEmpty() ? "_ _ _" : String.join(" ", numberInput.split(""));
        context.getMatrices().push();
        context.getMatrices().translate(sx + sw / 2f, y + 14, 0);
        context.getMatrices().scale(2f, 2f, 1);
        centered(context, shown, 0, 0, numberInput.isEmpty() ? MUTED : TEXT);
        context.getMatrices().pop();
        int number = parseNumber(numberInput);
        PhoneClient.Person person = number >= 0 ? state.person(number) : null;
        if (person != null && !person.name().isEmpty()) {
            centered(context, fit(person.name(), sw - 12), sx + sw / 2, y + 34, ACCENT);
        }
        int pillY = contentBottom() - 24;
        renderKeypad(context, y + 46, pillY - 4, false);
        pill(context, sx + 12, pillY, sw - 24, action, valid, onValid);
    }

    private void renderConversation(DrawContext context) {
        renderHeader(context, Text.literal(state.nameOf(other)), true);
        int x = sx() + 8;
        int w = sw() - 16;
        int areaTop = contentTop() + 2;
        int areaBottom = contentBottom() - 28;
        int bubbleWidth = w * 3 / 4;
        List<List<OrderedText>> wrapped = new ArrayList<>(messages.size());
        int total = 0;
        int thumbW = Math.min(bubbleWidth - 8, 112);
        int thumbH = thumbW * PhoneNetwork.THUMB_HEIGHT / PhoneNetwork.THUMB_WIDTH;
        for (Message message : messages) {
            List<OrderedText> lines = message.photo() != null ? List.of() : textRenderer.wrapLines(Text.literal(message.text()), bubbleWidth - 10);
            wrapped.add(lines);
            total += (message.photo() != null ? thumbH + 8 : lines.size() * 10 + 6) + 4;
        }
        scroll = Math.min(scroll, Math.max(0, total - (areaBottom - areaTop)));
        scissor(context, sx(), areaTop, sx() + sw(), areaBottom);
        int y = areaBottom + scroll;
        for (int i = messages.size() - 1; i >= 0 && y > areaTop; i--) {
            Message message = messages.get(i);
            List<OrderedText> lines = wrapped.get(i);
            int textWidth = 0;
            for (OrderedText line : lines) {
                textWidth = Math.max(textWidth, textRenderer.getWidth(line));
            }
            if (message.photo() != null) {
                int height = thumbH + 8;
                y -= height + 4;
                int bx = message.outgoing() ? x + w - thumbW - 8 : x;
                round(context, bx, y, thumbW + 8, height, 6, message.outgoing() ? ACCENT : CARD);
                UUID id = message.photo();
                drawPhoto(context, id, bx + 4, y + 4, thumbW, thumbH);
                if (visible(y, height, areaTop, areaBottom)) {
                    hits.add(new Hit(bx, y, thumbW + 8, height, () -> openPhoto(id, false)));
                }
                continue;
            }
            int height = lines.size() * 10 + 6;
            y -= height + 4;
            int bw = textWidth + 10;
            int bx = message.outgoing() ? x + w - bw : x;
            round(context, bx, y, bw, height, 6, message.outgoing() ? ACCENT : CARD);
            for (int l = 0; l < lines.size(); l++) {
                context.drawText(textRenderer, lines.get(l), bx + 5, y + 4 + l * 10, message.outgoing() ? WHITE : TEXT, false);
            }
        }
        context.disableScissor();
        if (messages.isEmpty()) {
            centered(context, Text.translatable("gui.mca_vie.phone.no_messages"), sx() + sw() / 2, areaTop + 30, MUTED);
        }
        int barY = contentBottom() - 26;
        context.fill(sx(), barY - 2, sx() + sw(), barY - 1, LINE);
        round(context, sx() + 8, barY + 2, sw() - 38, 18, 9, CARD);
        if (draft.isEmpty() && (mainField == null || !mainField.isFocused())) {
            context.drawText(textRenderer, Text.translatable("gui.mca_vie.phone.write"), sx() + 14, barY + 7, MUTED, false);
        }
        int bx = sx() + sw() - 26;
        boolean hover = hovered(bx, barY + 1, 20, 20);
        boolean ready = !draft.trim().isEmpty();
        round(context, bx, barY + 1, 20, 20, 10, ready ? (hover ? ACCENT_HOVER : ACCENT) : CARD);
        drawSendArrow(context, bx + 6, barY + 5, ready ? WHITE : MUTED);
        hits.add(new Hit(bx, barY + 1, 20, 20, this::sendMessage));
    }

    private void openPhoto(UUID id, boolean fromGallery) {
        if (client != null) {
            boolean saved = state.gallery().stream().anyMatch(p -> p.id().equals(id));
            client.setScreen(new PhotoViewerScreen(this, state.number(), id, fromGallery && !state.contacts().isEmpty(), fromGallery, !saved));
        }
    }

    public void pickContact(UUID id) {
        photo = id;
        view = View.PICK_CONTACT;
        scroll = 0;
    }

    public void deletePhoto(UUID id) {
        send(PhoneNetwork.OP_DELETE_PHOTO, buf -> buf.writeUuid(id));
        PhonePhotos.forget(id);
        view = View.GALLERY;
    }

    public void refresh() {
        send(PhoneNetwork.OP_REFRESH, buf -> {
        });
    }

    private void drawPhoto(DrawContext context, UUID id, int x, int y, int w, int h) {
        context.fill(x, y, x + w, y + h, 0xFF000000);
        PhonePhotos.Picture picture = PhonePhotos.get(state.number(), id, false);
        if (picture == null) {
            context.fill(x, y, x + w, y + h, 0xFF2A2F3A);
            centered(context, "…", x + w / 2, y + h / 2 - 4, MUTED);
            return;
        }
        drawFitted(context, picture, x, y, w, h);
    }

    static void drawFitted(DrawContext context, PhonePhotos.Picture picture, int x, int y, int w, int h) {
        int dw = w;
        int dh = w * picture.height() / picture.width();
        if (dh > h) {
            dh = h;
            dw = h * picture.width() / picture.height();
        }
        int dx = x + (w - dw) / 2;
        int dy = y + (h - dh) / 2;
        context.drawTexture(picture.texture(), dx, dy, dw, dh, 0, 0, picture.width(), picture.height(), picture.width(), picture.height());
    }

    private void renderGallery(DrawContext context) {
        String folderName = folder == 0 ? null : state.folders().get(folder);
        int sx = sx();
        int sw = sw();
        int hy = sy() + STATUS;
        renderHeader(context, folderName == null ? Text.translatable("gui.mca_vie.phone.app.gallery") : Text.literal(fit(folderName, sw - 92)), true);
        if (folder == 0) {
            boolean hover = hovered(sx + sw - 22, hy + 4, 16, 16);
            int color = hover ? ACCENT_HOVER : ACCENT;
            context.fill(sx + sw - 18, hy + HEADER / 2 - 1, sx + sw - 8, hy + HEADER / 2 + 1, color);
            context.fill(sx + sw - 14, hy + HEADER / 2 - 5, sx + sw - 12, hy + HEADER / 2 + 5, color);
            hits.add(new Hit(sx + sw - 22, hy + 4, 16, 16, () -> editFolder(0)));
        } else {
            boolean renameHover = hovered(sx + sw - 40, hy + 4, 16, 16);
            context.drawText(textRenderer, "✎", sx + sw - 36, hy + 8, renameHover ? ACCENT_HOVER : ACCENT, false);
            hits.add(new Hit(sx + sw - 40, hy + 4, 16, 16, () -> editFolder(folder)));
            boolean deleteHover = hovered(sx + sw - 22, hy + 4, 16, 16);
            drawCross(context, sx + sw - 18, hy + 8, deleteHover ? DANGER : MUTED);
            hits.add(new Hit(sx + sw - 22, hy + 4, 16, 16, this::deleteFolder));
        }
        List<PhoneClient.Photo> gallery = state.gallery().stream().filter(p -> p.folder() == folder).toList();
        List<Map.Entry<Integer, String>> folders = new ArrayList<>();
        if (folder == 0) {
            folders.addAll(state.folders().entrySet());
            folders.sort(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER));
        }
        int top = contentTop() + 2;
        int bottom = contentBottom();
        if (gallery.isEmpty() && folders.isEmpty() && folder == 0) {
            for (OrderedText line : textRenderer.wrapLines(Text.translatable("gui.mca_vie.phone.no_photos"), sw - 20)) {
                centered(context, line, sx + sw / 2, top + 28, MUTED);
                top += 10;
            }
            return;
        }
        int rowH = 18;
        int listRows = folder == 0 ? folders.size() : 1;
        int gap = 2;
        int cellW = (sw - 4 - gap * 2) / 3;
        int cellH = cellW * PhoneNetwork.THUMB_HEIGHT / PhoneNetwork.THUMB_WIDTH;
        int rows = (gallery.size() + 2) / 3;
        int listH = listRows * rowH + (listRows > 0 ? gap : 0);
        int emptyH = gallery.isEmpty() ? 30 : 0;
        scroll = Math.min(scroll, Math.max(0, listH + rows * (cellH + gap) + emptyH - (bottom - top)));
        scissor(context, sx, top, sx + sw, bottom);
        int y = top - scroll;
        if (folder == 0) {
            for (Map.Entry<Integer, String> entry : folders) {
                int id = entry.getKey();
                long count = state.gallery().stream().filter(p -> p.folder() == id).count();
                folderRow(context, sx, y, sw, rowH, top, bottom, id, entry.getValue(), String.valueOf(count), () -> {
                    folder = id;
                    scroll = 0;
                });
                y += rowH;
            }
        } else {
            folderRow(context, sx, y, sw, rowH, top, bottom, 0, "↑ " + Text.translatable("gui.mca_vie.phone.app.gallery").getString(), "", this::goBack);
            y += rowH;
        }
        if (listRows > 0) {
            y += gap;
        }
        if (gallery.isEmpty()) {
            for (OrderedText line : textRenderer.wrapLines(Text.translatable("gui.mca_vie.phone.folder_empty"), sw - 20)) {
                centered(context, line, sx + sw / 2, y + 8, MUTED);
                y += 10;
            }
        }
        for (int i = 0; i < gallery.size(); i++) {
            int px = sx + 2 + (i % 3) * (cellW + gap);
            int py = y + (i / 3) * (cellH + gap);
            if (py + cellH < top || py > bottom) {
                continue;
            }
            UUID id = gallery.get(i).id();
            drawPhoto(context, id, px, py, cellW, cellH);
            if (dragging && id.equals(pressed)) {
                context.fill(px, py, px + cellW, py + cellH, 0xA0000000);
            } else if (!dragging && hovered(px, py, cellW, cellH)) {
                context.fill(px, py, px + cellW, py + cellH, 0x30FFFFFF);
            }
            if (py >= top && py + cellH <= bottom) {
                cells.add(new Cell(px, py, cellW, cellH, id));
            }
        }
        context.disableScissor();
    }

    private void folderRow(DrawContext context, int sx, int y, int sw, int h, int top, int bottom, int id, String name, String count, Runnable open) {
        if (y + h < top || y > bottom) {
            return;
        }
        boolean hover = hovered(sx, y, sw, h);
        if (dragging && hover) {
            context.fill(sx, y, sx + sw, y + h, 0x803B82F6);
        } else if (hover) {
            context.fill(sx, y, sx + sw, y + h, CARD_HOVER);
        }
        int ix = sx + 8;
        int iy = y + (h - 9) / 2;
        context.fill(ix, iy, ix + 5, iy + 2, 0xFFD69A2D);
        context.fill(ix, iy + 1, ix + 12, iy + 9, 0xFFF0B840);
        context.drawText(textRenderer, fit(name, sw - 50), sx + 26, y + (h - 8) / 2, TEXT, false);
        if (!count.isEmpty()) {
            context.drawText(textRenderer, count, sx + sw - 8 - textRenderer.getWidth(count), y + (h - 8) / 2, MUTED, false);
        }
        context.fill(sx + 26, y + h - 1, sx + sw, y + h, LINE);
        if (visible(y, h, top, bottom)) {
            hits.add(new Hit(sx, y, sw, h, open));
            drops.add(new Drop(sx, y, sw, h, id));
        }
    }

    private void renderFolderName(DrawContext context) {
        renderHeader(context, Text.translatable(editingFolder == 0 ? "gui.mca_vie.phone.new_folder" : "gui.mca_vie.phone.rename_folder"), true);
        int sx = sx();
        int sw = sw();
        int top = contentTop();
        context.drawText(textRenderer, Text.translatable("gui.mca_vie.phone.folder_name"), sx + 10, top + 14, MUTED, false);
        round(context, sx + 8, top + 26, sw - 16, 16, 6, CARD);
        pill(context, sx + 20, top + 52, sw - 40, Text.translatable("gui.mca_vie.phone.folder_ok"), !folderInput.trim().isEmpty(), this::submitFolder);
    }

    private void editFolder(int id) {
        editingFolder = id;
        folderInput = id == 0 ? "" : state.folders().getOrDefault(id, "");
        switchTo(View.FOLDER_NAME);
    }

    private void submitFolder() {
        String name = folderInput.trim();
        if (name.isEmpty()) {
            return;
        }
        int id = editingFolder;
        if (id == 0) {
            send(PhoneNetwork.OP_FOLDER_CREATE, buf -> buf.writeString(name));
        } else {
            send(PhoneNetwork.OP_FOLDER_RENAME, buf -> {
                buf.writeVarInt(id);
                buf.writeString(name);
            });
        }
        switchTo(View.GALLERY);
    }

    private void deleteFolder() {
        int id = folder;
        send(PhoneNetwork.OP_FOLDER_DELETE, buf -> buf.writeVarInt(id));
        folder = 0;
        scroll = 0;
    }

    private void goBack() {
        if ((view == View.GALLERY || view == View.WALLPAPER) && folder != 0) {
            folder = 0;
            scroll = 0;
            return;
        }
        switchTo(parent());
    }

    private void renderPickContact(DrawContext context) {
        renderHeader(context, Text.translatable("gui.mca_vie.phone.send_to"), true);
        List<Integer> contacts = new ArrayList<>(state.contacts());
        contacts.sort(Comparator.comparing(state::nameOf, String.CASE_INSENSITIVE_ORDER));
        int sx = sx();
        int sw = sw();
        int top = contentTop();
        int bottom = contentBottom();
        scroll = Math.min(scroll, Math.max(0, contacts.size() * ROW - (bottom - top)));
        scissor(context, sx, top, sx + sw, bottom);
        for (int i = 0; i < contacts.size(); i++) {
            int number = contacts.get(i);
            int ry = top + i * ROW - scroll;
            if (ry + ROW < top || ry > bottom) {
                continue;
            }
            if (visible(ry, ROW, top, bottom) && hovered(sx, ry, sw, ROW)) {
                context.fill(sx, ry, sx + sw, ry + ROW, CARD_HOVER);
            }
            drawAvatar(context, sx + 8, ry + 5, 20, number);
            context.drawText(textRenderer, fit(state.nameOf(number), sw - 44), sx + 34, ry + 6, TEXT, false);
            context.drawText(textRenderer, String.format("%03d", number), sx + 34, ry + 17, MUTED, false);
            context.fill(sx + 34, ry + ROW - 1, sx + sw, ry + ROW, LINE);
            UUID id = photo;
            if (visible(ry, ROW, top, bottom)) {
                hits.add(new Hit(sx, ry, sw, ROW, () -> {
                    send(PhoneNetwork.OP_SEND_PHOTO, buf -> {
                        buf.writeVarInt(number);
                        buf.writeUuid(id);
                    });
                    openConversation(number);
                }));
            }
        }
        context.disableScissor();
    }

    private List<App> homeApps() {
        List<App> list = new ArrayList<>();
        for (String key : PhoneSettings.order) {
            apps.stream().filter(a -> a.key().equals(key) && !list.contains(a)).findFirst().ifPresent(list::add);
        }
        for (App app : apps) {
            if (!list.contains(app)) {
                list.add(app);
            }
        }
        return list;
    }

    private void moveApp(int index, int delta) {
        List<App> list = homeApps();
        int target = index + delta;
        if (target < 0 || target >= list.size()) {
            return;
        }
        list.add(target, list.remove(index));
        PhoneSettings.order = new ArrayList<>(list.stream().map(App::key).toList());
        PhoneSettings.save();
    }

    private int section(DrawContext context, int x, int y, String key) {
        context.drawText(textRenderer, Text.translatable(key).formatted(Formatting.BOLD), x + 2, y + 2, MUTED, false);
        return y + 13;
    }

    private int option(DrawContext context, int x, int y, int w, Text label, Text value, Runnable action) {
        boolean hover = hovered(x, y, w, 20);
        round(context, x, y, w, 20, 6, hover ? CARD_HOVER : CARD);
        String shown = value.getString();
        int vw = textRenderer.getWidth(shown);
        context.drawText(textRenderer, fit(label.getString(), w - vw - 22), x + 7, y + 6, TEXT, false);
        context.drawText(textRenderer, shown, x + w - vw - 7, y + 6, ACCENT, false);
        hits.add(new Hit(x, y, w, 20, action));
        return y + 22;
    }

    private static Text setting(String key) {
        return Text.translatable("gui.mca_vie.phone.settings." + key);
    }

    private void clampScroll(int contentEnd, int bottom) {
        int max = Math.max(0, contentEnd + scroll - bottom + 6);
        scroll = Math.min(scroll, max);
    }

    private void keepVisibleHits(int first, int top, int bottom) {
        hits.subList(first, hits.size()).removeIf(h -> h.y() + h.h() <= top || h.y() >= bottom);
    }

    private void renderSettings(DrawContext context) {
        renderHeader(context, Text.translatable("gui.mca_vie.phone.app.settings"), true);
        int first = hits.size();
        int sx = sx();
        int sw = sw();
        int x = sx + 8;
        int w = sw - 16;
        int top = contentTop();
        int bottom = contentBottom();
        int y = top + 8 - scroll;
        scissor(context, sx, top, sx + sw, bottom);
        round(context, x, y, w, 56, 8, CARD);
        context.drawText(textRenderer, fit(Text.translatable("gui.mca_vie.phone.my_number").getString(), w - 16), x + 8, y + 5, MUTED, false);
        context.drawText(textRenderer, String.format("%03d", state.number()), x + 8, y + 16, TEXT, false);
        context.fill(x + 8, y + 27, x + w - 8, y + 28, LINE);
        context.drawText(textRenderer, fit(Text.translatable("gui.mca_vie.phone.lock_code").getString(), w - 16), x + 8, y + 32, MUTED, false);
        Text status = Text.translatable(state.hasCode() ? "gui.mca_vie.phone.on" : "gui.mca_vie.phone.off");
        context.drawText(textRenderer, status, x + 8, y + 43, state.hasCode() ? 0xFF22C55E : TEXT, false);
        y += 64;
        if (state.owner()) {
            y = option(context, x, y, w, Text.translatable("gui.mca_vie.phone.wallpaper"), Text.literal(">"), () -> {
                folder = 0;
                switchTo(View.WALLPAPER);
            });
        }

        y = section(context, x, y + 4, "gui.mca_vie.phone.settings.section.sound");
        y = option(context, x, y, w, setting("mode"), setting("mode." + PhoneSettings.mode), () -> {
            PhoneSettings.mode = (PhoneSettings.mode + 1) % 3;
            PhoneSettings.save();
            if (PhoneSettings.mode == PhoneSettings.MODE_RING) {
                PhoneSettings.preview(PhoneSettings.messageSound, PhoneSettings.KIND_MESSAGE);
            } else if (PhoneSettings.mode == PhoneSettings.MODE_VIBRATE) {
                PhoneSettings.previewVibrate();
            }
        });
        y = option(context, x, y, w, setting("message_sound"), setting("sound." + PhoneSettings.SOUNDS[PhoneSettings.messageSound]), () -> {
            PhoneSettings.messageSound = (PhoneSettings.messageSound + 1) % PhoneSettings.SOUNDS.length;
            PhoneSettings.save();
            PhoneSettings.preview(PhoneSettings.messageSound, PhoneSettings.KIND_MESSAGE);
        });
        y = option(context, x, y, w, setting("reminder_sound"), setting("sound." + PhoneSettings.SOUNDS[PhoneSettings.reminderSound]), () -> {
            PhoneSettings.reminderSound = (PhoneSettings.reminderSound + 1) % PhoneSettings.SOUNDS.length;
            PhoneSettings.save();
            PhoneSettings.preview(PhoneSettings.reminderSound, PhoneSettings.KIND_REMINDER);
        });

        y = section(context, x, y + 4, "gui.mca_vie.phone.settings.section.display");
        y = option(context, x, y, w, setting("theme"), setting(PhoneSettings.light ? "theme.light" : "theme.dark"), () -> {
            PhoneSettings.light = !PhoneSettings.light;
            PhoneSettings.save();
            applyTheme();
        });
        y = option(context, x, y, w, setting("text_size"), setting("text_size." + PhoneSettings.textSize), () -> {
            PhoneSettings.textSize = (PhoneSettings.textSize + 1) % PhoneSettings.TEXT_SCALES.length;
            PhoneSettings.save();
            clearAndInit();
        });

        y = section(context, x, y + 4, "gui.mca_vie.phone.settings.section.map");
        y = option(context, x, y, w, setting("map_orientation"), setting(PhoneSettings.mapRotate ? "map.rotate" : "map.north"), () -> {
            PhoneSettings.mapRotate = !PhoneSettings.mapRotate;
            PhoneSettings.save();
        });
        String zoom = "x" + (MAP_SCALES[PhoneSettings.mapZoom] == 0.5f ? "0.5" : String.valueOf((int) MAP_SCALES[PhoneSettings.mapZoom]));
        y = option(context, x, y, w, setting("map_zoom"), Text.literal(zoom), () -> {
            PhoneSettings.mapZoom = (PhoneSettings.mapZoom + 1) % MAP_SCALES.length;
            PhoneSettings.save();
        });

        y = section(context, x, y + 4, "gui.mca_vie.phone.settings.section.home");
        y = option(context, x, y, w, setting("apps"), Text.literal(">"), () -> switchTo(View.HOME_APPS));
        y += 6;

        if (!state.owner()) {
            for (OrderedText line : textRenderer.wrapLines(Text.translatable("gui.mca_vie.phone.owner_only"), w)) {
                context.drawText(textRenderer, line, x, y, MUTED, false);
                y += 10;
            }
        } else {
            pill(context, x, y, w, Text.translatable(state.hasCode() ? "gui.mca_vie.phone.change_code" : "gui.mca_vie.phone.set_code"), true, () -> {
                codeInput = "";
                switchTo(View.SET_CODE);
            });
            if (state.hasCode()) {
                y += 26;
                boolean hover = hovered(x, y, w, 20);
                round(context, x, y, w, 20, 10, hover ? (PhoneSettings.light ? 0xFFFBE4E4 : 0xFF3A1D20) : CARD);
                centered(context, fit(Text.translatable("gui.mca_vie.phone.remove_code").getString(), w - 12), x + w / 2, y + 6, DANGER);
                hits.add(new Hit(x, y, w, 20, () -> send(PhoneNetwork.OP_SET_CODE, buf -> buf.writeString(""))));
            }
            y += 30;
            for (OrderedText line : textRenderer.wrapLines(Text.translatable(state.hasCode() ? "gui.mca_vie.phone.code_on" : "gui.mca_vie.phone.code_off"), w)) {
                context.drawText(textRenderer, line, x, y, MUTED, false);
                y += 10;
            }
        }
        context.disableScissor();
        keepVisibleHits(first, top, bottom);
        clampScroll(y, bottom);
    }

    private void renderHomeApps(DrawContext context) {
        renderHeader(context, setting("apps"), true);
        int first = hits.size();
        int sx = sx();
        int sw = sw();
        int x = sx + 8;
        int w = sw - 16;
        int top = contentTop();
        int bottom = contentBottom();
        int y = top + 6 - scroll;
        scissor(context, sx, top, sx + sw, bottom);
        for (OrderedText line : textRenderer.wrapLines(setting("apps_hint"), w)) {
            context.drawText(textRenderer, line, x, y, MUTED, false);
            y += 10;
        }
        y += 4;
        List<App> list = homeApps();
        for (int i = 0; i < list.size(); i++) {
            App app = list.get(i);
            int index = i;
            boolean locked = app.key().equals("settings");
            boolean visible = locked || !PhoneSettings.hidden.contains(app.key());
            round(context, x, y, w, 22, 6, CARD);
            RenderSystem.enableBlend();
            context.drawTexture(app.texture(), x + 4, y + 3, 16, 16, 0, 0, 128, 128, 128, 128);
            RenderSystem.disableBlend();
            int controls = 54;
            context.drawText(textRenderer, fit(Text.translatable("gui.mca_vie.phone.app." + app.key()).getString(), w - 26 - controls), x + 24, y + 7, visible ? TEXT : MUTED, false);
            int bx = x + w - controls;
            arrowButton(context, bx, y + 3, true, i > 0, () -> moveApp(index, -1));
            arrowButton(context, bx + 16, y + 3, false, i < list.size() - 1, () -> moveApp(index, 1));
            int tx = bx + 34;
            int ty = y + 6;
            round(context, tx, ty, 16, 10, 5, locked ? LINE : visible ? ACCENT : (PhoneSettings.light ? 0xFFC4C8D0 : 0xFF3A3F4A));
            round(context, visible ? tx + 7 : tx + 1, ty + 1, 8, 8, 4, WHITE);
            if (!locked) {
                hits.add(new Hit(tx - 2, y, 20, 22, () -> {
                    if (!PhoneSettings.hidden.remove(app.key())) {
                        PhoneSettings.hidden.add(app.key());
                    }
                    PhoneSettings.save();
                }));
            }
            y += 24;
        }
        context.disableScissor();
        keepVisibleHits(first, top, bottom);
        clampScroll(y, bottom);
    }

    private void arrowButton(DrawContext context, int x, int y, boolean up, boolean enabled, Runnable action) {
        boolean hover = enabled && hovered(x, y, 14, 16);
        if (hover) {
            round(context, x, y, 14, 16, 4, CARD_HOVER);
        }
        int color = enabled ? (hover ? ACCENT_HOVER : ACCENT) : LINE;
        int cx = x + 7;
        int cy = y + 6;
        for (int i = 0; i < 4; i++) {
            int row = up ? cy + i : cy + 3 - i;
            context.fill(cx - i, row, cx + i + 1, row + 1, color);
        }
        if (enabled) {
            hits.add(new Hit(x, y, 14, 16, action));
        }
    }

    public void setWallpaper(String key) {
        send(PhoneNetwork.OP_SET_WALLPAPER, buf -> buf.writeString(key));
    }

    private static int presetIndex(String key) {
        if (key.startsWith("preset:")) {
            try {
                return MathHelper.clamp(Integer.parseInt(key.substring(7)), 0, WALLPAPERS.length - 1);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private static Crop parseCrop(String key) {
        if (key.isEmpty() || key.startsWith("preset:")) {
            return null;
        }
        int at = key.indexOf('@');
        try {
            UUID id = UUID.fromString(at < 0 ? key : key.substring(0, at));
            if (at < 0) {
                return new Crop(id, 1f, 0.5f, 0.5f);
            }
            String[] parts = key.substring(at + 1).split(",");
            return new Crop(id, MathHelper.clamp(Float.parseFloat(parts[0]), 1f, MAX_CROP_ZOOM),
                    MathHelper.clamp(Float.parseFloat(parts[1]), 0f, 1f), MathHelper.clamp(Float.parseFloat(parts[2]), 0f, 1f));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public void startCrop(UUID id) {
        Crop current = parseCrop(state.wallpaper());
        boolean same = current != null && current.id().equals(id);
        cropId = id;
        cropZoom = same ? current.zoom() : 1f;
        cropU = same ? current.u() : 0.5f;
        cropV = same ? current.v() : 0.5f;
        view = View.CROP;
        scroll = 0;
        mapDragging = false;
        clearAndInit();
    }

    private void applyCrop() {
        if (cropId == null) {
            return;
        }
        setWallpaper(String.format(Locale.ROOT, "%s@%.3f,%.4f,%.4f", cropId, cropZoom, cropU, cropV));
        switchTo(View.HOME);
    }

    private static float clampCenter(float center, float visible) {
        if (visible >= 1f) {
            return 0.5f;
        }
        return MathHelper.clamp(center, visible / 2f, 1f - visible / 2f);
    }

    private void drawCropPreview(DrawContext context, int x, int y, int w, int h) {
        PhonePhotos.Picture picture = cropId == null ? null : PhonePhotos.get(state.number(), cropId, true);
        if (picture == null) {
            round(context, x, y, w, h, 11, BG);
            return;
        }
        cropWidth = picture.width();
        cropHeight = picture.height();
        cropScale = Math.max(w / (float) cropWidth, h / (float) cropHeight) * cropZoom;
        cropU = clampCenter(cropU, w / cropScale / cropWidth);
        cropV = clampCenter(cropV, h / cropScale / cropHeight);
        drawPhotoWallpaper(context, picture, cropZoom, cropU, cropV, x, y, w, h);
    }

    private void renderCrop(DrawContext context) {
        renderHeader(context, Text.translatable("gui.mca_vie.phone.crop"), true);
        int sx = sx();
        int sw = sw();
        int by = contentBottom() - 26;
        int half = (sw - 24) / 2;
        List<OrderedText> hint = textRenderer.wrapLines(Text.translatable("gui.mca_vie.phone.crop_hint"), sw - 16);
        for (int i = 0; i < hint.size(); i++) {
            centered(context, hint.get(i), sx + sw / 2, by - 4 - (hint.size() - i) * 10, WHITE);
        }
        pill(context, sx + 8, by, half, Text.translatable("gui.mca_vie.phone.crop_cancel"), true, this::goBack);
        pill(context, sx + 16 + half, by, half, Text.translatable("gui.mca_vie.phone.crop_apply"), cropId != null, this::applyCrop);
    }

    private void drawWallpaper(DrawContext context, int x, int y, int w, int h) {
        String key = state.wallpaper();
        Crop crop = parseCrop(key);
        PhonePhotos.Picture picture = crop == null ? null : PhonePhotos.get(state.number(), crop.id(), true);
        if (picture == null) {
            int[] colors = WALLPAPERS[presetIndex(key)];
            roundGradient(context, x, y, w, h, 11, colors[0], colors[1]);
            return;
        }
        drawPhotoWallpaper(context, picture, crop.zoom(), crop.u(), crop.v(), x, y, w, h);
    }

    private static void drawPhotoWallpaper(DrawContext context, PhonePhotos.Picture picture, float zoom, float u, float v, int x, int y, int w, int h) {
        int pw = picture.width();
        int ph = picture.height();
        float scale = Math.max(w / (float) pw, h / (float) ph) * zoom;
        int rw = MathHelper.clamp(Math.round(w / scale), 1, pw);
        int rh = MathHelper.clamp(Math.round(h / scale), 1, ph);
        float u0 = MathHelper.clamp(u * pw - rw / 2f, 0, pw - rw);
        float v0 = MathHelper.clamp(v * ph - rh / 2f, 0, ph - rh);
        context.drawTexture(picture.texture(), x, y, w, h, u0, v0, rw, rh, pw, ph);
        context.fill(x, y, x + w, y + h, 0x48000000);
        int r = Math.min(11, Math.min(w, h) / 2);
        for (int i = 0; i < r; i++) {
            int inset = inset(r, i);
            context.fill(x, y + i, x + inset, y + i + 1, BODY);
            context.fill(x + w - inset, y + i, x + w, y + i + 1, BODY);
            context.fill(x, y + h - i - 1, x + inset, y + h - i, BODY);
            context.fill(x + w - inset, y + h - i - 1, x + w, y + h - i, BODY);
        }
    }

    private static void outline(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x - 1, y - 1, x + w + 1, y, color);
        context.fill(x - 1, y + h, x + w + 1, y + h + 1, color);
        context.fill(x - 1, y, x, y + h, color);
        context.fill(x + w, y, x + w + 1, y + h, color);
    }

    private void renderWallpaper(DrawContext context) {
        String folderName = folder == 0 ? null : state.folders().get(folder);
        int sx = sx();
        int sw = sw();
        renderHeader(context, folderName == null ? Text.translatable("gui.mca_vie.phone.wallpaper") : Text.literal(fit(folderName, sw - 56)), true);
        String current = state.wallpaper();
        Crop currentCrop = parseCrop(current);
        UUID currentId = currentCrop == null ? null : currentCrop.id();
        int top = contentTop() + 2;
        int bottom = contentBottom();
        int gap = 2;
        int cellW = (sw - 4 - gap * 2) / 3;
        int cellH = cellW * PhoneNetwork.THUMB_HEIGHT / PhoneNetwork.THUMB_WIDTH;
        int presetW = (sw - 8 - 4 * (WALLPAPERS.length - 1)) / WALLPAPERS.length;
        int presetH = presetW * 16 / 9;
        List<PhoneClient.Photo> gallery = state.gallery().stream().filter(p -> p.folder() == folder).toList();
        List<Map.Entry<Integer, String>> folders = new ArrayList<>();
        if (folder == 0) {
            folders.addAll(state.folders().entrySet());
            folders.sort(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER));
        }
        int rowH = 18;
        int listRows = folder == 0 ? folders.size() : 1;
        int total = 14 + presetH + 8 + 14 + listRows * rowH + gap + (gallery.size() + 2) / 3 * (cellH + gap) + (gallery.isEmpty() ? 30 : 0);
        scroll = Math.min(scroll, Math.max(0, total - (bottom - top)));
        scissor(context, sx, top, sx + sw, bottom);
        int y = top - scroll;
        context.drawText(textRenderer, Text.translatable("gui.mca_vie.phone.wallpaper_presets"), sx + 6, y + 3, MUTED, false);
        y += 14;
        for (int i = 0; i < WALLPAPERS.length; i++) {
            int px = sx + 4 + i * (presetW + 4);
            String key = i == 0 ? "" : "preset:" + i;
            boolean selected = key.equals(current) || (i == 0 && current.equals("preset:0"));
            roundGradient(context, px, y, presetW, presetH, 3, WALLPAPERS[i][0], WALLPAPERS[i][1]);
            if (selected) {
                outline(context, px, y, presetW, presetH, TEXT);
            } else if (visible(y, presetH, top, bottom) && hovered(px, y, presetW, presetH)) {
                context.fill(px, y, px + presetW, y + presetH, 0x30FFFFFF);
            }
            if (visible(y, presetH, top, bottom)) {
                hits.add(new Hit(px, y, presetW, presetH, () -> setWallpaper(key)));
            }
        }
        y += presetH + 8;
        context.drawText(textRenderer, Text.translatable("gui.mca_vie.phone.app.gallery"), sx + 6, y + 3, MUTED, false);
        y += 14;
        if (folder == 0) {
            for (Map.Entry<Integer, String> entry : folders) {
                int id = entry.getKey();
                long count = state.gallery().stream().filter(p -> p.folder() == id).count();
                folderRow(context, sx, y, sw, rowH, top, bottom, id, entry.getValue(), String.valueOf(count), () -> {
                    folder = id;
                    scroll = 0;
                });
                y += rowH;
            }
        } else {
            folderRow(context, sx, y, sw, rowH, top, bottom, 0, "↑ " + Text.translatable("gui.mca_vie.phone.app.gallery").getString(), "", this::goBack);
            y += rowH;
        }
        if (listRows > 0) {
            y += gap;
        }
        if (gallery.isEmpty()) {
            for (OrderedText line : textRenderer.wrapLines(Text.translatable(folder == 0 ? "gui.mca_vie.phone.no_photos" : "gui.mca_vie.phone.folder_empty"), sw - 20)) {
                centered(context, line, sx + sw / 2, y + 8, MUTED);
                y += 10;
            }
        }
        for (int i = 0; i < gallery.size(); i++) {
            int px = sx + 2 + (i % 3) * (cellW + gap);
            int py = y + (i / 3) * (cellH + gap);
            if (py + cellH < top || py > bottom) {
                continue;
            }
            UUID id = gallery.get(i).id();
            drawPhoto(context, id, px, py, cellW, cellH);
            if (id.equals(currentId)) {
                outline(context, px, py, cellW, cellH, TEXT);
            } else if (visible(py, cellH, top, bottom) && hovered(px, py, cellW, cellH)) {
                context.fill(px, py, px + cellW, py + cellH, 0x30FFFFFF);
            }
            if (visible(py, cellH, top, bottom)) {
                hits.add(new Hit(px, py, cellW, cellH, () -> startCrop(id)));
            }
        }
        context.disableScissor();
    }

    private long agendaToday() {
        return client != null && client.world != null ? WorldCalendar.exactCalendarDay(client.world) : 0;
    }

    private int eventsToday() {
        long today = agendaToday();
        return (int) state.agenda().stream().filter(e -> PhoneAgenda.occursOn(e.day(), e.yearly(), today)).count();
    }

    private void renderAgenda(DrawContext context) {
        renderHeader(context, Text.translatable("gui.mca_vie.phone.app.agenda"), true);
        int sx = sx();
        int sw = sw();
        int top = contentTop();
        int bottom = contentBottom();
        long today = agendaToday();
        if (agendaSelected == Long.MIN_VALUE) {
            agendaSelected = today;
        }
        if (agendaMonth == null) {
            agendaMonth = PhoneAgenda.date(agendaSelected).withDayOfMonth(1);
        }
        int y = top + 4;
        int year = agendaMonth.getYear() <= 0 ? agendaMonth.getYear() - 1 : agendaMonth.getYear();
        String title = Text.translatable("calendar.mca_vie.month." + agendaMonth.getMonthValue()).getString() + " " + year;
        centered(context, fit(title, sw - 40), sx + sw / 2, y + 3, TEXT);
        boolean prevHover = hovered(sx + 4, y, 16, 14);
        boolean nextHover = hovered(sx + sw - 20, y, 16, 14);
        centered(context, "<", sx + 12, y + 3, prevHover ? ACCENT_HOVER : ACCENT);
        centered(context, ">", sx + sw - 12, y + 3, nextHover ? ACCENT_HOVER : ACCENT);
        hits.add(new Hit(sx + 4, y, 16, 14, () -> agendaMonth = agendaMonth.minusMonths(1)));
        hits.add(new Hit(sx + sw - 20, y, 16, 14, () -> agendaMonth = agendaMonth.plusMonths(1)));
        y += 16;
        int cell = (sw - 8) / 7;
        int gx = sx + (sw - cell * 7) / 2;
        for (int d = 1; d <= 7; d++) {
            String letter = Text.translatable("calendar.mca_vie.day." + d).getString().substring(0, 1);
            centered(context, letter, gx + (d - 1) * cell + cell / 2, y, MUTED);
        }
        y += 11;
        int rowH = 13;
        long first = PhoneAgenda.calendarDay(agendaMonth);
        int offset = agendaMonth.getDayOfWeek().getValue() - 1;
        int length = agendaMonth.lengthOfMonth();
        for (int i = 0; i < length; i++) {
            long day = first + i;
            int col = (offset + i) % 7;
            int row = (offset + i) / 7;
            int cx = gx + col * cell;
            int cy = y + row * rowH;
            boolean selected = day == agendaSelected;
            boolean hover = hovered(cx, cy, cell, rowH);
            if (selected) {
                round(context, cx + 1, cy, cell - 2, rowH - 1, 4, ACCENT);
            } else if (day == today) {
                round(context, cx + 1, cy, cell - 2, rowH - 1, 4, 0xFF2B3A55);
            } else if (hover) {
                round(context, cx + 1, cy, cell - 2, rowH - 1, 4, CARD_HOVER);
            }
            centered(context, String.valueOf(i + 1), cx + cell / 2, cy + 2, selected ? WHITE : day == today ? ACCENT_HOVER : TEXT);
            if (state.agenda().stream().anyMatch(e -> PhoneAgenda.occursOn(e.day(), e.yearly(), day))) {
                context.fill(cx + cell / 2 - 1, cy + rowH - 3, cx + cell / 2 + 1, cy + rowH - 1, selected ? WHITE : 0xFFF59E0B);
            }
            hits.add(new Hit(cx, cy, cell, rowH, () -> {
                agendaSelected = day;
                scroll = 0;
            }));
        }
        y += 6 * rowH + 4;
        context.fill(sx, y, sx + sw, y + 1, LINE);
        LocalDate selectedDate = PhoneAgenda.date(agendaSelected);
        Text dayLabel = agendaSelected == today ? Text.translatable("gui.mca_vie.phone.agenda_today")
                : Text.literal(Text.translatable("calendar.mca_vie.day." + selectedDate.getDayOfWeek().getValue()).getString() + " " + selectedDate.getDayOfMonth() + "/" + selectedDate.getMonthValue());
        context.drawText(textRenderer, fit(dayLabel.getString(), sw - 16), sx + 8, y + 4, MUTED, false);
        int listTop = y + 15;
        List<PhoneClient.Event> events = new ArrayList<>();
        long selectedDay = agendaSelected;
        for (PhoneClient.Event event : state.agenda()) {
            if (PhoneAgenda.occursOn(event.day(), event.yearly(), selectedDay)) {
                events.add(event);
            }
        }
        int row = 20;
        if (events.isEmpty()) {
            centered(context, Text.translatable("gui.mca_vie.phone.agenda_empty"), sx + sw / 2, listTop + 8, MUTED);
        }
        scroll = Math.min(scroll, Math.max(0, events.size() * row - (bottom - listTop)));
        scissor(context, sx, listTop, sx + sw, bottom);
        for (int i = 0; i < events.size(); i++) {
            PhoneClient.Event event = events.get(i);
            int ry = listTop + i * row - scroll;
            if (ry + row < listTop || ry > bottom) {
                continue;
            }
            context.fill(sx + 8, ry + 6, sx + 11, ry + 9, event.yearly() ? 0xFFF59E0B : ACCENT);
            context.drawText(textRenderer, fit(event.title(), sw - 44), sx + 16, ry + 4, TEXT, false);
            if (event.yearly() && event.day() != selectedDay) {
                int years = selectedDate.getYear() - PhoneAgenda.date(event.day()).getYear();
                context.drawText(textRenderer, Text.translatable("gui.mca_vie.phone.agenda_years", years), sx + 16, ry + 13, MUTED, false);
            }
            context.fill(sx + 16, ry + row - 1, sx + sw, ry + row, LINE);
            boolean crossHover = hovered(sx + sw - 22, ry + 2, 16, 16);
            drawCross(context, sx + sw - 18, ry + 6, crossHover ? DANGER : MUTED);
            if (visible(ry, row, listTop, bottom)) {
                int id = event.id();
                hits.add(new Hit(sx + sw - 22, ry + 2, 16, 16, () -> send(PhoneNetwork.OP_AGENDA_REMOVE, buf -> buf.writeVarInt(id))));
            }
        }
        context.disableScissor();
        drawFab(context, () -> {
            eventInput = "";
            eventYearly = false;
            switchTo(View.AGENDA_EDIT);
        });
    }

    private void renderAgendaEdit(DrawContext context) {
        renderHeader(context, Text.translatable("gui.mca_vie.phone.agenda_new"), true);
        int sx = sx();
        int sw = sw();
        int top = contentTop();
        LocalDate date = PhoneAgenda.date(agendaSelected);
        String when = date.getDayOfMonth() + " " + Text.translatable("calendar.mca_vie.month." + date.getMonthValue()).getString();
        context.drawText(textRenderer, fit(Text.translatable("gui.mca_vie.phone.agenda_title", when).getString(), sw - 20), sx + 10, top + 14, MUTED, false);
        round(context, sx + 8, top + 26, sw - 16, 16, 6, CARD);
        int y = top + 50;
        boolean hover = hovered(sx + 8, y, sw - 16, 16);
        int box = sx + 12;
        context.fill(box, y + 3, box + 10, y + 13, hover ? ACCENT_HOVER : MUTED);
        context.fill(box + 1, y + 4, box + 9, y + 12, eventYearly ? ACCENT : BG);
        if (eventYearly) {
            context.fill(box + 3, y + 6, box + 7, y + 10, WHITE);
        }
        context.drawText(textRenderer, fit(Text.translatable("gui.mca_vie.phone.agenda_yearly").getString(), sw - 40), box + 15, y + 4, TEXT, false);
        hits.add(new Hit(sx + 8, y, sw - 16, 16, () -> eventYearly = !eventYearly));
        pill(context, sx + 20, y + 26, sw - 40, Text.translatable("gui.mca_vie.phone.folder_ok"), !eventInput.trim().isEmpty(), this::submitEvent);
    }

    private void submitEvent() {
        String title = eventInput.trim();
        if (title.isEmpty()) {
            return;
        }
        long day = agendaSelected;
        boolean yearly = eventYearly;
        send(PhoneNetwork.OP_AGENDA_ADD, buf -> {
            buf.writeVarLong(day);
            buf.writeBoolean(yearly);
            buf.writeString(title);
        });
        switchTo(View.AGENDA);
    }

    private void requestEmployees() {
        ClientPlayNetworking.send(PhoneEmployees.REQUEST,
                PacketByteBufs.create());
    }

    public void applyEmployees(List<PhoneEmployees.Employee> list) {
        List<PhoneEmployees.Employee> sorted = new ArrayList<>(list);
        sorted.sort(Comparator.comparing(PhoneEmployees.Employee::name, String.CASE_INSENSITIVE_ORDER));
        employees = List.copyOf(sorted);
        employeesLoaded = true;
        if ((view == View.EMPLOYEE || view == View.EMPLOYEE_CHAT) && findEmployee() == null) {
            switchTo(View.EMPLOYEES);
        }
    }

    private PhoneEmployees.Employee findEmployee() {
        return employees.stream().filter(e -> e.uuid().equals(employee)).findFirst().orElse(null);
    }

    private static Text employeeStatus(PhoneEmployees.Employee e) {
        return e.status() < 0 ? Text.translatable("gui.mca_vie.phone.employees.resting")
                : WorkStatus.byId(e.status()).getText();
    }

    private static boolean employeeAlert(PhoneEmployees.Employee e) {
        return e.alerts() != 0 || e.health() < e.maxHealth() * 0.5f
                || e.status() == WorkStatus.INVENTORY_FULL.ordinal()
                || e.status() == WorkStatus.NO_WORKSTATION.ordinal()
                || e.status() == WorkStatus.NO_TOOL.ordinal();
    }

    private Text employeeWhere(PhoneEmployees.Employee e) {
        String pos = e.pos().getX() + ", " + e.pos().getY() + ", " + e.pos().getZ();
        var player = MinecraftClient.getInstance().player;
        if (player != null && player.getWorld().getRegistryKey().getValue().toString().equals(e.dimension())) {
            int distance = (int) Math.sqrt(player.getBlockPos().getSquaredDistance(e.pos()));
            return Text.translatable("gui.mca_vie.phone.employees.where", pos, distance);
        }
        return Text.literal(pos);
    }

    private static Text employeeSeen(PhoneEmployees.Employee e) {
        if (e.loaded()) {
            return Text.translatable("gui.mca_vie.phone.employees.live");
        }
        if (e.seenAgo() < 0) {
            return Text.translatable("gui.mca_vie.phone.employees.unknown");
        }
        long minutes = e.seenAgo() / 1200;
        return minutes < 60 ? Text.translatable("gui.mca_vie.phone.employees.seen_minutes", Math.max(1, minutes))
                : Text.translatable("gui.mca_vie.phone.employees.seen_hours", minutes / 60);
    }

    private void renderEmployees(DrawContext context) {
        renderHeader(context, Text.translatable("gui.mca_vie.phone.app.employees"), true);
        int top = contentTop();
        int bottom = contentBottom();
        int sx = sx();
        int sw = sw();
        if (employees.isEmpty()) {
            Text empty = Text.translatable(employeesLoaded ? "gui.mca_vie.phone.employees.none" : "gui.mca_vie.phone.employees.loading");
            List<OrderedText> lines = textRenderer.wrapLines(empty, sw - 24);
            for (int i = 0; i < lines.size(); i++) {
                centered(context, lines.get(i), sx + sw / 2, top + 30 + i * 10, MUTED);
            }
            return;
        }
        int maxScroll = Math.max(0, employees.size() * ROW - (bottom - top));
        scroll = Math.min(scroll, maxScroll);
        scissor(context, sx, top, sx + sw, bottom);
        for (int i = 0; i < employees.size(); i++) {
            var e = employees.get(i);
            int ry = top + i * ROW - scroll;
            if (ry + ROW < top || ry > bottom) {
                continue;
            }
            boolean rowHover = visible(ry, ROW, top, bottom) && hovered(sx, ry, sw, ROW);
            if (rowHover) {
                context.fill(sx, ry, sx + sw, ry + ROW, CARD_HOVER);
            }
            drawEmployeeHead(context, e, sx + 6, ry + 5, 20);
            context.drawText(textRenderer, fit(e.name(), sw - 50), sx + 30, ry + 6, e.loaded() ? TEXT : MUTED, false);
            String line = Job.byId(e.job()).getName().getString() + " · "
                    + (e.loaded() ? employeeStatus(e).getString() : Text.translatable("gui.mca_vie.phone.employees.away").getString());
            context.drawText(textRenderer, fit(line, sw - 50), sx + 30, ry + 17, MUTED, false);
            if (employeeAlert(e)) {
                round(context, sx + sw - 14, ry + ROW / 2 - 3, 6, 6, 3, DANGER);
            }
            context.fill(sx + 30, ry + ROW - 1, sx + sw, ry + ROW, LINE);
            if (visible(ry, ROW, top, bottom)) {
                UUID id = e.uuid();
                hits.add(new Hit(sx, ry, sw, ROW, () -> {
                    employee = id;
                    switchTo(View.EMPLOYEE);
                }));
            }
        }
        context.disableScissor();
    }

    private void renderEmployee(DrawContext context) {
        var e = findEmployee();
        if (e == null) {
            renderHeader(context, Text.translatable("gui.mca_vie.phone.app.employees"), true);
            return;
        }
        renderHeader(context, Text.literal(e.name()), true);
        int first = hits.size();
        int sx = sx();
        int sw = sw();
        int x = sx + 12;
        int w = sw - 24;
        int top = contentTop();
        int bottom = contentBottom();
        int y = top + 8 - scroll;
        scissor(context, sx, top, sx + sw, bottom);
        int frameH = 84;
        round(context, x, y, w, frameH, 8, CARD);
        LivingEntity look = EmployeeLooks.get(e);
        if (look != null) {
            scissor(context, x, Math.max(y, top), x + w, Math.min(y + frameH, bottom));
            EmployeeLooks.drawBody(context, look, x, y, w, frameH, mouseX, mouseY);
            context.disableScissor();
        } else {
            drawEmployeeInitial(context, e, x + w / 2 - 16, y + frameH / 2 - 16, 32);
        }
        y += frameH + 8;
        var job = Job.byId(e.job());
        context.drawItem(job.icon(), x, y);
        context.drawText(textRenderer, fit(job.getName().getString(), w - 22), x + 22, y + 4, TEXT, false);
        y += 24;
        context.drawText(textRenderer, Text.translatable("gui.mca_vie.phone.employees.state"), x, y, MUTED, false);
        y += 10;
        Text state = e.loaded() ? employeeStatus(e) : Text.translatable("gui.mca_vie.phone.employees.away");
        for (OrderedText l : textRenderer.wrapLines(state, w)) {
            context.drawText(textRenderer, l, x, y, TEXT, false);
            y += 10;
        }
        y += 4;
        context.drawText(textRenderer, Text.translatable("gui.mca_vie.phone.employees.health"), x, y, MUTED, false);
        y += 11;
        float ratio = e.maxHealth() > 0 ? MathHelper.clamp(e.health() / e.maxHealth(), 0, 1) : 0;
        round(context, x, y, w, 6, 3, CARD);
        if (ratio > 0) {
            round(context, x, y, Math.max(6, (int) (w * ratio)), 6, 3, ratio < 0.5f ? DANGER : 0xFF30A46C);
        }
        y += 12;
        context.drawText(textRenderer, Text.translatable("gui.mca_vie.phone.employees.position"), x, y, MUTED, false);
        y += 10;
        for (OrderedText l : textRenderer.wrapLines(employeeWhere(e), w)) {
            context.drawText(textRenderer, l, x, y, TEXT, false);
            y += 10;
        }
        context.drawText(textRenderer, fit(employeeSeen(e).getString(), w), x, y, MUTED, false);
        y += 18;
        if (!e.loaded()) {
            for (OrderedText l : textRenderer.wrapLines(Text.translatable("gui.mca_vie.phone.employees.pending_hint"), w)) {
                context.drawText(textRenderer, l, x, y, MUTED, false);
                y += 10;
            }
            y += 4;
        }
        y = renderEmployeeExtras(context, e, x, y, w);
        int by = Math.max(y, bottom - 30 - scroll);
        Text label = Text.translatable(confirmFire ? "gui.mca_vie.phone.employees.fire_confirm" : "gui.mca_vie.phone.employees.fire");
        boolean hover = hovered(x, by, w, 20);
        round(context, x, by, w, 20, 10, hover ? 0xFFFF6369 : DANGER);
        centered(context, fit(label.getString(), w - 12), x + w / 2, by + 6, WHITE);
        UUID id = e.uuid();
        hits.add(new Hit(x, by, w, 20, () -> {
            if (!confirmFire) {
                confirmFire = true;
                return;
            }
            confirmFire = false;
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeUuid(id);
            ClientPlayNetworking.send(PhoneEmployees.FIRE, buf);
            switchTo(View.EMPLOYEES);
        }));
        context.disableScissor();
        keepVisibleHits(first, top, bottom);
        clampScroll(by + 24, bottom);
    }

    // tête du PNJ (entité du monde ou reconstruite depuis son apparence), sinon pastille avec l'initiale
    private void drawEmployeeHead(DrawContext context, PhoneEmployees.Employee e, int x, int y, int box) {
        LivingEntity look = EmployeeLooks.get(e);
        if (look == null) {
            drawEmployeeInitial(context, e, x, y, box);
            return;
        }
        round(context, x, y, box, box, 4, CARD);
        scissor(context, x, y, x + box, y + box);
        EmployeeLooks.drawHead(context, look, x, y, box);
        context.disableScissor();
    }

    private void drawEmployeeInitial(DrawContext context, PhoneEmployees.Employee e, int x, int y, int box) {
        int color = 0xFF000000 | (Color.HSBtoRGB((e.uuid().hashCode() & 0xFFFF) / 65535f, 0.45f, 0.75f) & 0xFFFFFF);
        round(context, x, y, box, box, box / 2, color);
        String initial = e.name().isEmpty() ? "?" : e.name().substring(0, e.name().offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
        centered(context, initial, x + box / 2, y + (box - 8) / 2, WHITE);
    }

    private static final String[] EMPLOYEE_ORDERS = {"follow", "stay", "home", "work"};

    // fiche employé : ordre actif/en attente, boutons d'ordres, messagerie, rapport du jour
    private int renderEmployeeExtras(DrawContext context, PhoneEmployees.Employee e, int x, int y, int w) {
        UUID id = e.uuid();
        if (!e.order().isEmpty()) {
            context.drawText(textRenderer, fit(Text.translatable("gui.mca_vie.phone.employees.order_active",
                    Text.translatable("gui.mca_vie.phone.employees.order." + e.order())).getString(), w), x, y, MUTED, false);
            y += 10;
        }
        if (!e.pending().isEmpty()) {
            context.drawText(textRenderer, fit(Text.translatable("gui.mca_vie.phone.employees.order_waiting",
                    Text.translatable("gui.mca_vie.phone.employees.order." + e.pending())).getString(), w), x, y, ACCENT, false);
            y += 10;
        }
        y += 2;
        int bw = (w - 4) / 2;
        for (int i = 0; i < EMPLOYEE_ORDERS.length; i++) {
            String order = EMPLOYEE_ORDERS[i];
            int bx = x + (i % 2) * (bw + 4);
            int by = y + (i / 2) * 22;
            boolean active = order.equals(e.order());
            boolean hover = hovered(bx, by, bw, 18);
            round(context, bx, by, bw, 18, 9, active ? ACCENT : hover ? CARD_HOVER : CARD);
            centered(context, fit(Text.translatable("gui.mca_vie.phone.employees.order." + order).getString(), bw - 8),
                    bx + bw / 2, by + 5, active ? WHITE : TEXT);
            hits.add(new Hit(bx, by, bw, 18, () -> sendEmployeeOrder(id, order)));
        }
        y += 46;
        boolean hover = hovered(x, y, w, 20);
        round(context, x, y, w, 20, 10, hover ? ACCENT_HOVER : ACCENT);
        centered(context, fit(Text.translatable("gui.mca_vie.phone.employees.message").getString(), w - 12), x + w / 2, y + 6, WHITE);
        hits.add(new Hit(x, y, w, 20, () -> openEmployeeChat()));
        y += 28;
        context.drawText(textRenderer, Text.translatable("gui.mca_vie.phone.employees.report"), x, y, MUTED, false);
        NbtCompound report = e.report();
        int total = 0;
        for (String key : report.getKeys()) {
            total += report.getInt(key);
        }
        y += 10;
        if (report.isEmpty()) {
            context.drawText(textRenderer, fit(Text.translatable("gui.mca_vie.phone.employees.report_empty").getString(), w), x, y, MUTED, false);
            return y + 14;
        }
        context.drawText(textRenderer, fit(Text.translatable("gui.mca_vie.phone.employees.report_total", total).getString(), w), x, y, TEXT, false);
        y += 12;
        List<String> keys = new ArrayList<>(report.getKeys());
        keys.sort(Comparator.comparingInt(report::getInt).reversed());
        int cell = 34;
        int perRow = Math.max(1, w / cell);
        int shown = Math.min(keys.size(), perRow * 2);
        for (int i = 0; i < shown; i++) {
            int cx = x + (i % perRow) * cell;
            int cy = y + (i / perRow) * 20;
            var item = Registries.ITEM.get(Identifier.tryParse(keys.get(i)));
            context.drawItem(new ItemStack(item), cx, cy);
            context.drawText(textRenderer, String.valueOf(report.getInt(keys.get(i))), cx + 17, cy + 5, TEXT, false);
        }
        return y + ((shown + perRow - 1) / perRow) * 20 + 4;
    }

    private void sendEmployeeOrder(UUID id, String order) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(id);
        buf.writeString(order);
        ClientPlayNetworking.send(PhoneEmployees.ORDER, buf);
    }

    private void openEmployeeChat() {
        employeeChat = List.of();
        pendingChat = null;
        awaitingReply = false;
        employeeChatLoaded = false;
        draft = "";
        switchTo(View.EMPLOYEE_CHAT);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(employee);
        ClientPlayNetworking.send(EmployeeChat.REQUEST, buf);
    }

    public void applyEmployeeChat(UUID id, List<EmployeeChat.Line> lines) {
        if (view == View.EMPLOYEE_CHAT && id.equals(employee)) {
            employeeChatLoaded = true;
            int lastOutgoing = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).outgoing()) {
                    lastOutgoing = i;
                }
            }
            if (awaitingReply && lastOutgoing >= 0 && lastOutgoing < lines.size() - 1) {
                int length = 0;
                for (int i = lastOutgoing + 1; i < lines.size(); i++) {
                    length += lines.get(i).text().length();
                }
                employeeChat = lines.subList(0, lastOutgoing + 1);
                pendingChat = lines;
                revealTick = ticks + 30 + ThreadLocalRandom.current().nextInt(21) + Math.min(20, length / 6);
            } else {
                employeeChat = lines;
                pendingChat = null;
            }
            awaitingReply = false;
            scroll = 0;
        }
    }

    private void sendEmployeeChat(String text) {
        if (employee == null) {
            return;
        }
        String message = text.trim();
        if (pendingChat != null) {
            employeeChat = pendingChat;
            pendingChat = null;
        }
        awaitingReply = true;
        List<EmployeeChat.Line> lines = new ArrayList<>(employeeChat);
        lines.add(new EmployeeChat.Line(true, message));
        employeeChat = lines;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(employee);
        buf.writeString(message.length() > EmployeeChat.MAX_TEXT
                ? message.substring(0, EmployeeChat.MAX_TEXT) : message);
        buf.writeString(MinecraftClient.getInstance().getLanguageManager().getLanguage());
        ClientPlayNetworking.send(EmployeeChat.SEND, buf);
        draft = "";
        scroll = 0;
        if (mainField != null) {
            mainField.setText("");
        }
    }

    private void renderEmployeeChat(DrawContext context) {
        var e = findEmployee();
        renderHeader(context, Text.literal(e != null ? e.name() : "?"), true);
        int x = sx() + 8;
        int w = sw() - 16;
        int areaTop = contentTop() + 2;
        int chipsTop = contentBottom() - 28 - 34;
        int areaBottom = chipsTop - 2;
        int bubbleWidth = w * 3 / 4;
        List<List<OrderedText>> wrapped = new ArrayList<>(employeeChat.size());
        int total = 0;
        for (var line : employeeChat) {
            List<OrderedText> lines = textRenderer.wrapLines(Text.literal(line.text().isEmpty() ? " " : line.text()), bubbleWidth - 10);
            wrapped.add(lines);
            total += lines.size() * 10 + 6 + 4;
        }
        boolean typing = pendingChat != null || awaitingReply;
        if (typing) {
            total += 20;
        }
        scroll = Math.min(scroll, Math.max(0, total - (areaBottom - areaTop)));
        scissor(context, sx(), areaTop, sx() + sw(), areaBottom);
        int y = areaBottom + scroll;
        if (typing) {
            y -= 20;
            round(context, x, y, 30, 16, 6, CARD);
            for (int d = 0; d < 3; d++) {
                float phase = (ticks + client.getTickDelta() - d * 4) / 6f;
                int hop = Math.round(Math.max(0, MathHelper.sin(phase)) * 3);
                round(context, x + 7 + d * 6, y + 7 - hop, 4, 4, 2, MUTED);
            }
        }
        for (int i = employeeChat.size() - 1; i >= 0 && y > areaTop; i--) {
            var line = employeeChat.get(i);
            List<OrderedText> lines = wrapped.get(i);
            int textWidth = 0;
            for (OrderedText l : lines) {
                textWidth = Math.max(textWidth, textRenderer.getWidth(l));
            }
            int height = lines.size() * 10 + 6;
            y -= height + 4;
            int bw = textWidth + 10;
            int bx = line.outgoing() ? x + w - bw : x;
            round(context, bx, y, bw, height, 6, line.outgoing() ? ACCENT : CARD);
            for (int l = 0; l < lines.size(); l++) {
                context.drawText(textRenderer, lines.get(l), bx + 5, y + 4 + l * 10, line.outgoing() ? WHITE : TEXT, false);
            }
        }
        context.disableScissor();
        if (employeeChat.isEmpty()) {
            Text empty = Text.translatable(employeeChatLoaded ? "gui.mca_vie.phone.employees.chat_empty" : "gui.mca_vie.phone.employees.loading");
            List<OrderedText> lines = textRenderer.wrapLines(empty, w - 8);
            for (int i = 0; i < lines.size(); i++) {
                centered(context, lines.get(i), sx() + sw() / 2, areaTop + 30 + i * 10, MUTED);
            }
        }
        int cw = (w - 4) / 2;
        for (int i = 0; i < 4; i++) {
            String suggestion = Text.translatable("gui.mca_vie.phone.employees.suggest." + (i + 1)).getString();
            int cx = x + (i % 2) * (cw + 4);
            int cy = chipsTop + (i / 2) * 16;
            boolean hover = hovered(cx, cy, cw, 14);
            round(context, cx, cy, cw, 14, 7, hover ? CARD_HOVER : CARD);
            centered(context, fit(suggestion, cw - 8), cx + cw / 2, cy + 3, ACCENT);
            hits.add(new Hit(cx, cy, cw, 14, () -> sendEmployeeChat(suggestion)));
        }
        int barY = contentBottom() - 26;
        context.fill(sx(), barY - 2, sx() + sw(), barY - 1, LINE);
        round(context, sx() + 8, barY + 2, sw() - 38, 18, 9, CARD);
        if (draft.isEmpty() && (mainField == null || !mainField.isFocused())) {
            context.drawText(textRenderer, Text.translatable("gui.mca_vie.phone.write"), sx() + 14, barY + 7, MUTED, false);
        }
        int bx = sx() + sw() - 26;
        boolean hover = hovered(bx, barY + 1, 20, 20);
        round(context, bx, barY + 1, 20, 20, 10, hover ? ACCENT_HOVER : ACCENT);
        drawSendArrow(context, bx + 6, barY + 5, WHITE);
        hits.add(new Hit(bx, barY + 1, 20, 20, () -> sendEmployeeChat(draft)));
    }

    private void renderSetCode(DrawContext context) {
        renderHeader(context, Text.translatable("gui.mca_vie.phone.lock_code"), true);
        int sx = sx();
        int sw = sw();
        int y = contentTop() + 10;
        centered(context, fit(Text.translatable("gui.mca_vie.phone.new_code").getString(), sw - 12), sx + sw / 2, y, MUTED);
        drawDots(context, y + 16, codeInput.length());
        renderKeypad(context, y + 36, contentBottom() - 4, false);
    }

    private void drawFab(DrawContext context, Runnable action) {
        int size = 22;
        int fx = sx() + sw() - size - 10;
        int fy = contentBottom() - size - 8;
        boolean hover = hovered(fx, fy, size, size);
        round(context, fx, fy, size, size, size / 2, hover ? ACCENT_HOVER : ACCENT);
        context.fill(fx + size / 2 - 1, fy + 6, fx + size / 2 + 1, fy + size - 6, WHITE);
        context.fill(fx + 6, fy + size / 2 - 1, fx + size - 6, fy + size / 2 + 1, WHITE);
        hits.add(0, new Hit(fx, fy, size, size, action));
    }

    private void pill(DrawContext context, int x, int y, int w, Text label, boolean enabled, Runnable action) {
        boolean hover = enabled && hovered(x, y, w, 20);
        round(context, x, y, w, 20, 10, enabled ? (hover ? ACCENT_HOVER : ACCENT) : CARD);
        centered(context, fit(label.getString(), w - 12), x + w / 2, y + 6, enabled ? WHITE : MUTED);
        if (enabled) {
            hits.add(new Hit(x, y, w, 20, action));
        }
    }

    private void drawAvatar(DrawContext context, int x, int y, int size, int number) {
        Identifier head = PhoneSkins.head(state.person(number));
        if (head != null) {
            round(context, x - 1, y - 1, size + 2, size + 2, 3, 0xFF000000);
            PlayerSkinDrawer.draw(context, head, x, y, size);
            return;
        }
        round(context, x, y, size, size, size / 2, AVATARS[number % AVATARS.length]);
        centered(context, "#", x + size / 2, y + (size - 8) / 2, WHITE);
    }

    private String fit(String text, int width) {
        if (textRenderer.getWidth(text) <= width) {
            return text;
        }
        return textRenderer.trimToWidth(text, Math.max(0, width - textRenderer.getWidth("…"))) + "…";
    }

    private boolean hovered(int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static boolean visible(int y, int h, int top, int bottom) {
        return y >= top && y + h <= bottom;
    }

    private static void round(DrawContext context, int x, int y, int w, int h, int radius, int color) {
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (h - 2 * r > 0) {
            context.fill(x, y + r, x + w, y + h - r, color);
        }
        for (int i = 0; i < r; i++) {
            int inset = inset(r, i);
            context.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            context.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
        }
    }

    private static void roundGradient(DrawContext context, int x, int y, int w, int h, int radius, int top, int bottom) {
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        for (int i = 0; i < h; i++) {
            int inset = i < r ? inset(r, i) : i >= h - r ? inset(r, h - 1 - i) : 0;
            context.fill(x + inset, y + i, x + w - inset, y + i + 1, mix(top, bottom, h <= 1 ? 0 : i / (float) (h - 1)));
        }
    }

    private static int inset(int r, int row) {
        double dy = r - row - 0.5;
        return r - (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
    }

    private static int mix(int a, int b, float t) {
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int ca = (a >>> shift) & 0xFF;
            int cb = (b >>> shift) & 0xFF;
            result |= Math.round(ca + (cb - ca) * t) << shift;
        }
        return result;
    }

    private static void drawChevron(DrawContext context, int x, int y, int color) {
        for (int i = 0; i < 5; i++) {
            context.fill(x + i, y - i - 1, x + i + 2, y - i, color);
            context.fill(x + i, y + i - 1, x + i + 2, y + i, color);
        }
    }

    private static void drawCross(DrawContext context, int x, int y, int color) {
        for (int i = 0; i < 8; i++) {
            context.fill(x + i, y + i, x + i + 1, y + i + 1, color);
            context.fill(x + 7 - i, y + i, x + 8 - i, y + i + 1, color);
        }
    }

    private static void drawSendArrow(DrawContext context, int x, int y, int color) {
        for (int i = 0; i < 6; i++) {
            context.fill(x + i, y + i, x + i + 2, y + 12 - i, color);
        }
        context.fill(x, y + 5, x + 4, y + 7, color);
    }

    private static void drawBackspace(DrawContext context, int x, int y, int color) {
        for (int i = 0; i < 4; i++) {
            context.fill(x + 3 - i, y + i, x + 4 - i, y + i + 1, color);
            context.fill(x + 3 - i, y + 8 - i, x + 4 - i, y + 9 - i, color);
        }
        context.fill(x, y + 4, x + 1, y + 5, color);
        context.fill(x + 4, y, x + 12, y + 1, color);
        context.fill(x + 4, y + 8, x + 12, y + 9, color);
        context.fill(x + 11, y, x + 12, y + 9, color);
        for (int i = 0; i < 4; i++) {
            context.fill(x + 6 + i, y + 3 + i, x + 7 + i, y + 4 + i, color);
            context.fill(x + 9 - i, y + 3 + i, x + 10 - i, y + 4 + i, color);
        }
    }

    private static void drawLockIcon(DrawContext context, int x, int y) {
        context.fill(x + 2, y, x + 6, y + 1, WHITE);
        context.fill(x + 1, y + 1, x + 2, y + 4, WHITE);
        context.fill(x + 6, y + 1, x + 7, y + 4, WHITE);
        context.fill(x, y + 4, x + 8, y + 10, WHITE);
    }
}
