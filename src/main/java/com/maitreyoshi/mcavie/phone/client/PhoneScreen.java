package com.maitreyoshi.mcavie.phone.client;

import com.maitreyoshi.mcavie.calendar.WorldCalendar;
import com.maitreyoshi.mcavie.calendar.WorldCalendar.CalendarDate;
import com.maitreyoshi.mcavie.phone.PhoneNetwork;
import com.maitreyoshi.mcavie.phone.client.PhoneClient.Message;
import com.maitreyoshi.mcavie.phone.client.PhoneClient.State;
import com.maitreyoshi.mcavie.phone.client.PhoneClient.Summary;
import com.maitreyoshi.mcavie.tree.client.TreeClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    private static final int BG = 0xFF14161C;
    private static final int CARD = 0xFF1F232B;
    private static final int CARD_HOVER = 0xFF2A2F3A;
    private static final int LINE = 0xFF272B34;
    private static final int ACCENT = 0xFF3B82F6;
    private static final int ACCENT_HOVER = 0xFF5B9BFF;
    private static final int DANGER = 0xFFE5484D;
    private static final int TEXT = 0xFFF2F3F5;
    private static final int MUTED = 0xFF8B909A;
    private static final int[] AVATARS = {0xFFE5484D, 0xFFF76B15, 0xFFFFB224, 0xFF30A46C, 0xFF12A594, 0xFF0090FF, 0xFF6E56CF, 0xFFD6409F};

    private enum View { LOCK, HOME, CONTACTS, ADD_CONTACT, MESSAGES, NEW_MESSAGE, CONVERSATION, SETTINGS, SET_CODE, CAMERA, TREE, GALLERY, PICK_CONTACT }

    private interface Icon {
        void draw(DrawContext context, int x, int y, int size);
    }

    private record App(String key, int top, int bottom, View view, IntSupplier badge, Icon icon) {
    }

    private record Hit(int x, int y, int w, int h, Runnable action) {
    }

    private final List<App> apps = List.of(
            new App("messages", 0xFF4ADE80, 0xFF16A34A, View.MESSAGES, () -> this.state.unread(), PhoneScreen::drawBubbleIcon),
            new App("contacts", 0xFF60A5FA, 0xFF2563EB, View.CONTACTS, () -> 0, PhoneScreen::drawPersonIcon),
            new App("camera", 0xFF52525B, 0xFF18181B, View.CAMERA, () -> 0, PhoneScreen::drawCameraIcon),
            new App("gallery", 0xFFFBBF24, 0xFFEA580C, View.GALLERY, () -> 0, PhoneScreen::drawPictureIcon),
            new App("tree", 0xFF86EFAC, 0xFF15803D, View.TREE, () -> 0, PhoneScreen::drawTreeIcon),
            new App("settings", 0xFFA1A1AA, 0xFF52525B, View.SETTINGS, () -> 0, PhoneScreen::drawGearIcon)
    );

    private final List<Hit> hits = new ArrayList<>();
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

    public PhoneScreen(State state) {
        super(Text.translatable("item.mca_vie.phone"));
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
        if (mainField != null) {
            mainField.tick();
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private int phoneHeight() {
        return Math.min(300, height - 16);
    }

    private int phoneWidth() {
        return Math.min(width - 8, Math.max(150, Math.round(phoneHeight() * 0.56f)));
    }

    private int phoneLeft() {
        return (width - phoneWidth()) / 2;
    }

    private int phoneTop() {
        return (height - phoneHeight()) / 2;
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
        clearAndInit();
    }

    private View parent() {
        return switch (view) {
            case ADD_CONTACT -> View.CONTACTS;
            case NEW_MESSAGE, CONVERSATION -> View.MESSAGES;
            case SET_CODE -> View.SETTINGS;
            case PICK_CONTACT -> View.GALLERY;
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
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && view != View.HOME && view != View.LOCK) {
            switchTo(parent());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            switch (view) {
                case CONVERSATION -> sendMessage();
                case ADD_CONTACT -> addContact();
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
        if (view == View.CONVERSATION) {
            scroll = Math.max(0, scroll + (int) (amount * 12));
        } else {
            scroll = Math.max(0, scroll - (int) (amount * 12));
        }
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        hits.clear();
        renderBackground(context);

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
            roundGradient(context, sx, sy, sw, sh, 11, 0xFF1E3A8A, 0xFF6D28D9);
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
            case SET_CODE -> renderSetCode(context);
            case GALLERY -> renderGallery(context);
            case PICK_CONTACT -> renderPickContact(context);
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

        if (toast != null && ticks < toastUntil) {
            List<OrderedText> lines = textRenderer.wrapLines(toast, sw - 28);
            int th = lines.size() * 10 + 8;
            int ty = contentBottom() - th - 30;
            round(context, sx + 8, ty, sw - 16, th, 6, 0xF0303540);
            for (int i = 0; i < lines.size(); i++) {
                context.drawCenteredTextWithShadow(textRenderer, lines.get(i), sx + sw / 2, ty + 5 + i * 10, 0xFFFF8080);
            }
        }
    }

    private void renderStatusBar(DrawContext context) {
        int sx = sx();
        int sy = sy();
        int sw = sw();
        String clock = client != null && client.world != null ? clock(WorldCalendar.now(client.world)) : "";
        context.drawText(textRenderer, clock, sx + 10, sy + 4, TEXT, false);
        round(context, sx + sw / 2 - 18, sy + 2, 36, 9, 4, 0xFF000000);
        context.fill(sx + sw / 2 + 10, sy + 5, sx + sw / 2 + 13, sy + 8, 0xFF1E2A44);
        int bx = sx + sw - 24;
        context.fill(bx, sy + 4, bx + 14, sy + 11, 0xFFB0B4BC);
        context.fill(bx + 1, sy + 5, bx + 13, sy + 10, BODY);
        context.fill(bx + 2, sy + 6, bx + 11, sy + 9, 0xFF34D399);
        context.fill(bx + 14, sy + 6, bx + 15, sy + 9, 0xFFB0B4BC);
        for (int i = 0; i < 4; i++) {
            context.fill(bx - 14 + i * 3, sy + 10 - (i + 1) * 2, bx - 12 + i * 3, sy + 11, TEXT);
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
            hits.add(new Hit(sx + 2, y + 2, 22, HEADER - 4, () -> switchTo(parent())));
        }
        String trimmed = fit(title.getString(), sw - 56);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(trimmed).formatted(Formatting.BOLD), sx + sw / 2, y + 8, TEXT);
    }

    private void renderLock(DrawContext context) {
        int sx = sx();
        int sw = sw();
        int y = sy() + STATUS + 10;
        String clock = client != null && client.world != null ? clock(WorldCalendar.now(client.world)) : "";
        context.getMatrices().push();
        context.getMatrices().translate(sx + sw / 2f, y, 0);
        context.getMatrices().scale(2.5f, 2.5f, 1);
        context.drawCenteredTextWithShadow(textRenderer, clock, 0, 0, TEXT);
        context.getMatrices().pop();
        y += 30;
        drawLockIcon(context, sx + sw / 2 - 4, y);
        y += 14;
        context.drawCenteredTextWithShadow(textRenderer, fit(Text.translatable("gui.mca_vie.phone.enter_code").getString(), sw - 12), sx + sw / 2, y, TEXT);
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
                drawBackspace(context, kx + size / 2 - 6, ky + size / 2 - 4, hover ? TEXT : (light ? 0xFFC8CCD4 : MUTED));
                hits.add(new Hit(kx, ky, size, size, this::eraseDigit));
                continue;
            }
            int color = light ? (hover ? 0x60FFFFFF : 0x35FFFFFF) : (hover ? CARD_HOVER : CARD);
            round(context, kx, ky, size, size, size / 2, color);
            context.drawCenteredTextWithShadow(textRenderer, key, kx + size / 2, ky + (size - 8) / 2, TEXT);
            int digit = Integer.parseInt(key);
            hits.add(new Hit(kx, ky, size, size, () -> typeDigit(digit)));
        }
    }

    private void renderHome(DrawContext context) {
        int sx = sx();
        int sw = sw();
        int top = sy() + STATUS;
        CalendarDate date = client != null && client.world != null ? WorldCalendar.now(client.world) : null;
        if (date != null) {
            context.getMatrices().push();
            context.getMatrices().translate(sx + sw / 2f, top + 8, 0);
            context.getMatrices().scale(2f, 2f, 1);
            context.drawCenteredTextWithShadow(textRenderer, clock(date), 0, 0, TEXT);
            context.getMatrices().pop();
            Text day = Text.translatable("calendar.mca_vie.day." + date.dayOfWeek().getValue());
            context.drawCenteredTextWithShadow(textRenderer, fit(day.getString() + " " + date.day() + "/" + date.month(), sw - 12), sx + sw / 2, top + 28, 0xFFDDE2F0);
        }
        int columns = 3;
        int icon = Math.min(30, (sw - 24) / columns - 10);
        int cell = (sw - 16) / columns;
        int gridTop = top + 48;
        for (int i = 0; i < apps.size(); i++) {
            App app = apps.get(i);
            int cx = sx + 8 + (i % columns) * cell + (cell - icon) / 2;
            int cy = gridTop + (i / columns) * (icon + 22);
            drawApp(context, app, cx, cy, icon);
            String label = fit(Text.translatable("gui.mca_vie.phone.app." + app.key()).getString(), (int) ((cell - 2) / 0.75f));
            context.getMatrices().push();
            context.getMatrices().translate(cx + icon / 2f, cy + icon + 3, 0);
            context.getMatrices().scale(0.75f, 0.75f, 1);
            context.drawCenteredTextWithShadow(textRenderer, label, 0, 0, TEXT);
            context.getMatrices().pop();
            hits.add(new Hit(cx - 2, cy - 2, icon + 4, icon + 14, () -> {
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
        int dockH = 36;
        int dockY = contentBottom() - dockH - 2;
        round(context, sx + 6, dockY, sw - 12, dockH, 10, 0x40FFFFFF);
        String label = fit(Text.translatable("gui.mca_vie.phone.number", String.format("%03d", state.number())).getString(), sw - 24);
        context.drawCenteredTextWithShadow(textRenderer, label, sx + sw / 2, dockY + (dockH - 8) / 2, TEXT);
    }

    private void drawApp(DrawContext context, App app, int x, int y, int size) {
        boolean hover = hovered(x, y, size, size);
        if (hover) {
            round(context, x - 2, y - 2, size + 4, size + 4, size / 4 + 2, 0x50FFFFFF);
        }
        roundGradient(context, x, y, size, size, size / 4, app.top(), app.bottom());
        app.icon().draw(context, x, y, size);
        int badge = app.badge().getAsInt();
        if (badge > 0) {
            String text = badge > 99 ? "99+" : String.valueOf(badge);
            int bw = Math.max(11, textRenderer.getWidth(text) + 5);
            round(context, x + size - bw + 4, y - 4, bw, 11, 5, DANGER);
            context.drawCenteredTextWithShadow(textRenderer, text, x + size - bw / 2 + 4, y - 2, TEXT);
        }
    }

    private void renderContacts(DrawContext context) {
        renderHeader(context, Text.translatable("gui.mca_vie.phone.app.contacts"), true);
        List<Integer> contacts = new ArrayList<>(state.contacts());
        contacts.sort(Comparator.comparing(state::nameOf, String.CASE_INSENSITIVE_ORDER));
        int top = contentTop();
        int bottom = contentBottom();
        if (contacts.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.mca_vie.phone.no_contacts"), sx() + sw() / 2, top + 30, MUTED);
        }
        int maxScroll = Math.max(0, contacts.size() * ROW - (bottom - top));
        scroll = Math.min(scroll, maxScroll);
        int sx = sx();
        int sw = sw();
        context.enableScissor(sx, top, sx + sw, bottom);
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
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.mca_vie.phone.no_messages"), sx + sw / 2, top + 30, MUTED);
        }
        int maxScroll = Math.max(0, conversations.size() * ROW - (bottom - top));
        scroll = Math.min(scroll, maxScroll);
        context.enableScissor(sx, top, sx + sw, bottom);
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
            String time = clock(WorldCalendar.fromTicks(summary.time()));
            int timeWidth = textRenderer.getWidth(time);
            Text title = Text.literal(fit(name, sw - 50 - timeWidth));
            context.drawText(textRenderer, unread ? title.copy().formatted(Formatting.BOLD) : title, sx + 34, ry + 6, TEXT, false);
            context.drawText(textRenderer, time, sx + sw - 8 - timeWidth, ry + 6, unread ? ACCENT : MUTED, false);
            String body = summary.photo() ? Text.translatable("gui.mca_vie.phone.photo").getString() : summary.preview();
            String preview = (summary.outgoing() ? Text.translatable("gui.mca_vie.phone.you").getString() + " " : "") + body;
            String count = summary.unread() > 99 ? "99+" : String.valueOf(summary.unread());
            int bw = Math.max(10, textRenderer.getWidth(count) + 4);
            context.drawText(textRenderer, fit(preview, sw - 42 - (unread ? bw + 4 : 0)), sx + 34, ry + 17, unread ? 0xFFD0D4DC : MUTED, false);
            if (unread) {
                round(context, sx + sw - 8 - bw, ry + 16, bw, 10, 5, ACCENT);
                context.drawCenteredTextWithShadow(textRenderer, count, sx + sw - 8 - bw / 2, ry + 17, TEXT);
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
        context.drawCenteredTextWithShadow(textRenderer, fit(hint.getString(), sw - 12), sx + sw / 2, y, MUTED);
        String shown = numberInput.isEmpty() ? "_ _ _" : String.join(" ", numberInput.split(""));
        context.getMatrices().push();
        context.getMatrices().translate(sx + sw / 2f, y + 14, 0);
        context.getMatrices().scale(2f, 2f, 1);
        context.drawCenteredTextWithShadow(textRenderer, shown, 0, 0, numberInput.isEmpty() ? MUTED : TEXT);
        context.getMatrices().pop();
        int number = parseNumber(numberInput);
        PhoneClient.Person person = number >= 0 ? state.person(number) : null;
        if (person != null && !person.name().isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, fit(person.name(), sw - 12), sx + sw / 2, y + 34, ACCENT);
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
        context.enableScissor(sx(), areaTop, sx() + sw(), areaBottom);
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
                context.drawText(textRenderer, lines.get(l), bx + 5, y + 4 + l * 10, TEXT, false);
            }
        }
        context.disableScissor();
        if (messages.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.mca_vie.phone.no_messages"), sx() + sw() / 2, areaTop + 30, MUTED);
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
        drawSendArrow(context, bx + 6, barY + 5, ready ? TEXT : MUTED);
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
            context.drawCenteredTextWithShadow(textRenderer, "…", x + w / 2, y + h / 2 - 4, MUTED);
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
        renderHeader(context, Text.translatable("gui.mca_vie.phone.app.gallery"), true);
        List<PhoneClient.Photo> gallery = state.gallery();
        int sx = sx();
        int sw = sw();
        int top = contentTop() + 2;
        int bottom = contentBottom();
        if (gallery.isEmpty()) {
            for (OrderedText line : textRenderer.wrapLines(Text.translatable("gui.mca_vie.phone.no_photos"), sw - 20)) {
                context.drawCenteredTextWithShadow(textRenderer, line, sx + sw / 2, top + 28, MUTED);
                top += 10;
            }
            return;
        }
        int gap = 2;
        int cellW = (sw - 4 - gap * 2) / 3;
        int cellH = cellW * PhoneNetwork.THUMB_HEIGHT / PhoneNetwork.THUMB_WIDTH;
        int rows = (gallery.size() + 2) / 3;
        scroll = Math.min(scroll, Math.max(0, rows * (cellH + gap) - (bottom - top)));
        context.enableScissor(sx, top, sx + sw, bottom);
        for (int i = 0; i < gallery.size(); i++) {
            int px = sx + 2 + (i % 3) * (cellW + gap);
            int py = top + (i / 3) * (cellH + gap) - scroll;
            if (py + cellH < top || py > bottom) {
                continue;
            }
            UUID id = gallery.get(i).id();
            drawPhoto(context, id, px, py, cellW, cellH);
            if (hovered(px, py, cellW, cellH)) {
                context.fill(px, py, px + cellW, py + cellH, 0x30FFFFFF);
            }
            if (py >= top && py + cellH <= bottom) {
                hits.add(new Hit(px, py, cellW, cellH, () -> openPhoto(id, true)));
            }
        }
        context.disableScissor();
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
        context.enableScissor(sx, top, sx + sw, bottom);
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

    private void renderSettings(DrawContext context) {
        renderHeader(context, Text.translatable("gui.mca_vie.phone.app.settings"), true);
        int sx = sx();
        int sw = sw();
        int x = sx + 8;
        int w = sw - 16;
        int y = contentTop() + 8;
        round(context, x, y, w, 56, 8, CARD);
        context.drawText(textRenderer, fit(Text.translatable("gui.mca_vie.phone.my_number").getString(), w - 16), x + 8, y + 5, MUTED, false);
        context.drawText(textRenderer, String.format("%03d", state.number()), x + 8, y + 16, TEXT, false);
        context.fill(x + 8, y + 27, x + w - 8, y + 28, LINE);
        context.drawText(textRenderer, fit(Text.translatable("gui.mca_vie.phone.lock_code").getString(), w - 16), x + 8, y + 32, MUTED, false);
        Text status = Text.translatable(state.hasCode() ? "gui.mca_vie.phone.on" : "gui.mca_vie.phone.off");
        context.drawText(textRenderer, status, x + 8, y + 43, state.hasCode() ? 0xFF4ADE80 : TEXT, false);
        y += 64;
        if (!state.owner()) {
            for (OrderedText line : textRenderer.wrapLines(Text.translatable("gui.mca_vie.phone.owner_only"), w)) {
                context.drawText(textRenderer, line, x, y, MUTED, false);
                y += 10;
            }
            return;
        }
        pill(context, x, y, w, Text.translatable(state.hasCode() ? "gui.mca_vie.phone.change_code" : "gui.mca_vie.phone.set_code"), true, () -> {
            codeInput = "";
            switchTo(View.SET_CODE);
        });
        if (state.hasCode()) {
            y += 26;
            boolean hover = hovered(x, y, w, 20);
            round(context, x, y, w, 20, 10, hover ? 0xFF3A1D20 : CARD);
            context.drawCenteredTextWithShadow(textRenderer, fit(Text.translatable("gui.mca_vie.phone.remove_code").getString(), w - 12), x + w / 2, y + 6, DANGER);
            hits.add(new Hit(x, y, w, 20, () -> send(PhoneNetwork.OP_SET_CODE, buf -> buf.writeString(""))));
        }
        y += 30;
        for (OrderedText line : textRenderer.wrapLines(Text.translatable(state.hasCode() ? "gui.mca_vie.phone.code_on" : "gui.mca_vie.phone.code_off"), w)) {
            context.drawText(textRenderer, line, x, y, MUTED, false);
            y += 10;
        }
    }

    private void renderSetCode(DrawContext context) {
        renderHeader(context, Text.translatable("gui.mca_vie.phone.lock_code"), true);
        int sx = sx();
        int sw = sw();
        int y = contentTop() + 10;
        context.drawCenteredTextWithShadow(textRenderer, fit(Text.translatable("gui.mca_vie.phone.new_code").getString(), sw - 12), sx + sw / 2, y, MUTED);
        drawDots(context, y + 16, codeInput.length());
        renderKeypad(context, y + 36, contentBottom() - 4, false);
    }

    private void drawFab(DrawContext context, Runnable action) {
        int size = 22;
        int fx = sx() + sw() - size - 10;
        int fy = contentBottom() - size - 8;
        boolean hover = hovered(fx, fy, size, size);
        round(context, fx, fy, size, size, size / 2, hover ? ACCENT_HOVER : ACCENT);
        context.fill(fx + size / 2 - 1, fy + 6, fx + size / 2 + 1, fy + size - 6, TEXT);
        context.fill(fx + 6, fy + size / 2 - 1, fx + size - 6, fy + size / 2 + 1, TEXT);
        hits.add(0, new Hit(fx, fy, size, size, action));
    }

    private void pill(DrawContext context, int x, int y, int w, Text label, boolean enabled, Runnable action) {
        boolean hover = enabled && hovered(x, y, w, 20);
        round(context, x, y, w, 20, 10, enabled ? (hover ? ACCENT_HOVER : ACCENT) : CARD);
        context.drawCenteredTextWithShadow(textRenderer, fit(label.getString(), w - 12), x + w / 2, y + 6, enabled ? TEXT : MUTED);
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
        context.drawCenteredTextWithShadow(textRenderer, "#", x + size / 2, y + (size - 8) / 2, TEXT);
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
        context.fill(x + 2, y, x + 6, y + 1, TEXT);
        context.fill(x + 1, y + 1, x + 2, y + 4, TEXT);
        context.fill(x + 6, y + 1, x + 7, y + 4, TEXT);
        context.fill(x, y + 4, x + 8, y + 10, TEXT);
    }

    private static void drawBubbleIcon(DrawContext context, int x, int y, int size) {
        int pad = size / 5;
        round(context, x + pad, y + pad, size - 2 * pad, size - 2 * pad - size / 8, size / 6, 0xFFFFFFFF);
        int tail = size / 8;
        for (int i = 0; i < tail; i++) {
            context.fill(x + pad + 2, y + size - pad - tail + i, x + pad + 2 + tail - i, y + size - pad - tail + i + 1, 0xFFFFFFFF);
        }
    }

    private static void drawPersonIcon(DrawContext context, int x, int y, int size) {
        int head = size / 3;
        round(context, x + (size - head) / 2, y + size / 5, head, head, head / 2, 0xFFFFFFFF);
        int body = size / 2 + 2;
        round(context, x + (size - body) / 2, y + size / 5 + head + 1, body, size / 4, size / 8, 0xFFFFFFFF);
    }

    private static void drawCameraIcon(DrawContext context, int x, int y, int size) {
        int bw = size * 2 / 3;
        int bh = size / 2;
        int bx = x + (size - bw) / 2;
        int by = y + (size - bh) / 2 + 1;
        context.fill(bx + bw / 4, by - 2, bx + bw / 2, by, 0xFFFFFFFF);
        round(context, bx, by, bw, bh, 2, 0xFFFFFFFF);
        int lens = bh * 2 / 3;
        round(context, x + (size - lens) / 2, by + (bh - lens) / 2, lens, lens, lens / 2, 0xFF27272A);
        int inner = Math.max(2, lens / 2);
        round(context, x + (size - inner) / 2, by + (bh - inner) / 2, inner, inner, inner / 2, 0xFF60A5FA);
    }

    private static void drawPictureIcon(DrawContext context, int x, int y, int size) {
        int pad = size / 5;
        int w = size - 2 * pad;
        int h = w * 3 / 4;
        int px = x + pad;
        int py = y + (size - h) / 2;
        round(context, px, py, w, h, 2, 0xFFFFFFFF);
        context.fill(px + 2, py + 2, px + w - 2, py + h - 2, 0xFF7DD3FC);
        round(context, px + w - 2 - w / 4, py + 3, w / 6, w / 6, w / 12, 0xFFFDE047);
        for (int i = 0; i < h / 2; i++) {
            context.fill(px + w / 3 - i, py + h - 2 - h / 2 + i, px + w / 3 + i + 1, py + h - 2 - h / 2 + i + 1, 0xFF16A34A);
        }
    }

    private static void drawTreeIcon(DrawContext context, int x, int y, int size) {
        int node = Math.max(4, size / 5);
        int cx = x + size / 2;
        int top = y + size / 5;
        int bottom = y + size - size / 5 - node;
        int left = x + size / 5;
        int right = x + size - size / 5 - node;
        context.fill(cx, top + node, cx + 1, bottom - node / 2, 0xFFFFFFFF);
        context.fill(left + node / 2, bottom - node / 2, right + node / 2 + 1, bottom - node / 2 + 1, 0xFFFFFFFF);
        context.fill(left + node / 2, bottom - node / 2, left + node / 2 + 1, bottom, 0xFFFFFFFF);
        context.fill(right + node / 2, bottom - node / 2, right + node / 2 + 1, bottom, 0xFFFFFFFF);
        round(context, cx - node / 2, top, node, node, 1, 0xFFFFFFFF);
        round(context, left, bottom, node, node, 1, 0xFFFFFFFF);
        round(context, right, bottom, node, node, 1, 0xFFFFFFFF);
    }

    private static void drawGearIcon(DrawContext context, int x, int y, int size) {
        int c = size / 2;
        int outer = size / 3;
        int tooth = Math.max(2, size / 10);
        context.fill(x + c - tooth, y + c - outer - tooth, x + c + tooth, y + c + outer + tooth, 0xFFFFFFFF);
        context.fill(x + c - outer - tooth, y + c - tooth, x + c + outer + tooth, y + c + tooth, 0xFFFFFFFF);
        round(context, x + c - outer, y + c - outer, outer * 2, outer * 2, outer, 0xFFFFFFFF);
        int inner = Math.max(2, outer / 2);
        round(context, x + c - inner, y + c - inner, inner * 2, inner * 2, inner, 0xFF71717A);
    }
}
