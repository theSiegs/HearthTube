package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.mylogger.Log;

/**
 * What the Hearth launcher shares through its content provider: the Google TV profile that's active right now
 * (Google TV has no API for it; Hearth watches its profile chooser), and Hearth's look and settings, so HearthTube
 * can match them. Null when Hearth isn't installed or is too old.
 */
public class HearthProfile {
    private static final String TAG = HearthProfile.class.getSimpleName();
    private static final String AUTHORITY = "content://com.leanbitlab.ltvL.profile";
    private static final Uri ACTIVE_URI = Uri.parse(AUTHORITY + "/active");
    /** Hearth's current wallpaper picture (no picture = a gradient, see {@link #gradientUuid}) */
    public static final Uri WALLPAPER_URI = Uri.parse(AUTHORITY + "/wallpaper");

    /** The Google TV profile, or null when Hearth couldn't tell. */
    @Nullable
    public final String name;
    /** Hearth's accent color, or null when it's the default or unknown. */
    @Nullable
    public final Integer accentColor;
    /** Hearth's clock format ("HH:mm", "h:mm a"...), or null for the default. */
    @Nullable
    public final String timeFormat;
    /** Hearth's language ("de"...), or "" for the system's. Null when Hearth is too old to tell. */
    @Nullable
    public final String appLanguage;
    /** Hearth has a parent PIN that {@link #verifyParentPin} can check */
    public final boolean hasParentPin;
    @Nullable
    public final String gradientUuid;
    /** Changes when Hearth's wallpaper picture does; 0 when it shows a gradient instead. */
    public final long wallpaperStamp;

    private HearthProfile(Cursor cursor) {
        String name = getString(cursor, "name");
        this.name = name != null && !name.trim().isEmpty() ? name.trim() : null;
        this.accentColor = parseColor(getString(cursor, "accent_color"));
        this.timeFormat = getString(cursor, "time_format");
        this.appLanguage = getString(cursor, "app_language");
        this.hasParentPin = getLong(cursor, "has_parent_pin") == 1;
        this.gradientUuid = getString(cursor, "gradient_uuid");
        this.wallpaperStamp = getLong(cursor, "wallpaper_stamp");
    }

    /**
     * The active Google TV profile. Null when Hearth is missing or couldn't tell who's watching.
     */
    @Nullable
    public static HearthProfile query(Context context) {
        HearthProfile hearth = queryHearth(context);
        return hearth != null && hearth.name != null ? hearth : null;
    }

    /**
     * Hearth's settings and look, even when it can't tell the profile. Null when Hearth is missing.
     */
    @Nullable
    public static HearthProfile queryHearth(Context context) {
        if (context == null) {
            return null;
        }

        try (Cursor cursor = context.getContentResolver().query(ACTIVE_URI, null, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return null;
            }

            return new HearthProfile(cursor);
        } catch (Exception e) {
            // Hearth missing or without the provider (SecurityException, IllegalArgumentException...)
            Log.d(TAG, "Hearth unavailable: %s", e.getMessage());
            return null;
        }
    }

    /** Result of {@link #verifyParentPin}: {@link #PIN_OK}, {@link #PIN_WRONG}, {@link #PIN_UNAVAILABLE} or seconds to wait. */
    public static final int PIN_OK = 0;
    public static final int PIN_WRONG = -1;
    /** Hearth is missing or too old to check PINs */
    public static final int PIN_UNAVAILABLE = -2;

    /**
     * Checks a PIN against Hearth's parent PIN. Hearth never hands out the PIN, and locks checks for a minute
     * after 5 wrong tries; then this returns the seconds left (a positive number).
     */
    public static int verifyParentPin(Context context, String pin) {
        try {
            Bundle result = context.getContentResolver().call(ACTIVE_URI, "verify_parent_pin", pin, null);

            if (result == null) {
                return PIN_UNAVAILABLE;
            }

            if (result.getBoolean("ok")) {
                return PIN_OK;
            }

            int wait = result.getInt("wait_seconds", 0);
            return wait > 0 ? wait : PIN_WRONG;
        } catch (Exception e) {
            Log.d(TAG, "Hearth PIN check unavailable: %s", e.getMessage());
            return PIN_UNAVAILABLE;
        }
    }

    /** Hearth is installed (and new enough to talk to HearthTube) */
    public static boolean isInstalled(Context context) {
        return context != null && context.getPackageManager().resolveContentProvider("com.leanbitlab.ltvL.profile", 0) != null;
    }

    /**
     * Goes to the launcher, like the Home button, when it's Hearth. False when Hearth isn't installed.
     */
    public static boolean goHome(Context context) {
        if (!isInstalled(context)) {
            return false;
        }

        try {
            context.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String getString(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index != -1 ? cursor.getString(index) : null;
    }

    private static long getLong(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index != -1 && !cursor.isNull(index) ? cursor.getLong(index) : 0;
    }

    /** "7C4DFF" (Hearth's format) to an opaque color. */
    @Nullable
    static Integer parseColor(String hex) {
        if (hex == null || !hex.matches("[0-9A-Fa-f]{6}")) {
            return null;
        }

        return Color.parseColor("#" + hex);
    }
}
