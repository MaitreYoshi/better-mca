package com.maitreyoshi.mcavie.phone;

import com.maitreyoshi.mcavie.MCAVie;
import net.minecraft.util.Identifier;

public final class PhoneNetwork {
    public static final Identifier ACTION = MCAVie.id("phone_action");
    public static final Identifier STATE = MCAVie.id("phone_state");
    public static final Identifier CONVERSATION = MCAVie.id("phone_conversation");
    public static final Identifier ERROR = MCAVie.id("phone_error");
    public static final Identifier PHOTO_UPLOAD = MCAVie.id("phone_photo_upload");
    public static final Identifier PHOTO = MCAVie.id("phone_photo");
    public static final Identifier PRINT_REQUEST = MCAVie.id("photo_print_request");
    public static final Identifier PRINT_VIEW = MCAVie.id("photo_print_view");
    public static final Identifier PRINTED = MCAVie.id("photo_printed");
    public static final Identifier PORTRAIT_REQUEST = MCAVie.id("portrait_request");
    public static final Identifier PHOTO_PLACE = MCAVie.id("photo_place");
    public static final Identifier PHOTO_RESIZE = MCAVie.id("photo_resize");
    public static final Identifier PHOTO_RESIZE_OPEN = MCAVie.id("photo_resize_open");
    public static final Identifier NOTIFY = MCAVie.id("phone_notify");
    public static final int NOTIFY_MESSAGE = 0;
    public static final int NOTIFY_REMINDER = 1;

    public static final int OP_REFRESH = 0;
    public static final int OP_UNLOCK = 1;
    public static final int OP_CONVERSATION = 2;
    public static final int OP_SEND = 3;
    public static final int OP_ADD_CONTACT = 4;
    public static final int OP_REMOVE_CONTACT = 5;
    public static final int OP_SET_CODE = 6;
    public static final int OP_PHOTO = 7;
    public static final int OP_DELETE_PHOTO = 8;
    public static final int OP_SEND_PHOTO = 9;
    public static final int OP_SAVE_PHOTO = 10;
    public static final int OP_PRINT_PHOTO = 11;
    public static final int OP_SET_PORTRAIT = 12;
    public static final int OP_FOLDER_CREATE = 13;
    public static final int OP_FOLDER_RENAME = 14;
    public static final int OP_FOLDER_DELETE = 15;
    public static final int OP_MOVE_PHOTO = 16;
    public static final int OP_SET_WALLPAPER = 17;
    public static final int OP_AGENDA_ADD = 18;
    public static final int OP_AGENDA_REMOVE = 19;
    public static final int MAX_EVENTS = 100;
    public static final int MAX_EVENT_TITLE = 48;
    public static final int WALLPAPERS = 6;
    public static final int MAX_WALLPAPER = 64;

    public static final int MAX_TEXT = 200;
    public static final int MAX_CONTACTS = 100;
    public static final int MAX_MESSAGES = 100;
    public static final int CODE_LENGTH = 4;
    public static final int PREVIEW = 40;
    public static final int FULL_WIDTH = 1280;
    public static final int FULL_HEIGHT = 720;
    public static final int THUMB_WIDTH = 160;
    public static final int THUMB_HEIGHT = 90;
    public static final int MAX_FULL_BYTES = 600000;
    public static final int MAX_THUMB_BYTES = 40000;
    public static final int PHOTO_CHUNK = 30000;
    // Garde-fou disque (~650 Ko par photo) : assez haut pour ne plus gener en jeu.
    public static final int MAX_PHOTOS = 1000;
    public static final int MAX_FOLDERS = 64;
    public static final int MAX_FOLDER_NAME = 24;
    public static final int PORTRAIT_SIZE = 128;

    private PhoneNetwork() {
    }

    public static boolean validCode(String code) {
        return code.length() == CODE_LENGTH && code.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    public static boolean validNumber(int number) {
        return number >= 0 && number < PhoneRegistry.NUMBERS;
    }
}
