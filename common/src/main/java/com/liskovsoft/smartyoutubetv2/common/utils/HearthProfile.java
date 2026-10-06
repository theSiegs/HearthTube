package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.mylogger.Log;

/**
 * The Google TV profile that's active right now, as tracked by the Hearth launcher.
 * Google TV has no API for it; Hearth watches its profile chooser and shares the result
 * through a content provider. Null when Hearth isn't installed, is too old, or couldn't tell.
 */
public class HearthProfile {
    private static final String TAG = HearthProfile.class.getSimpleName();
    private static final Uri ACTIVE_URI = Uri.parse("content://com.leanbitlab.ltvL.profile/active");

    public final String name;
    /** Hearth's accent color, or null when it's the default or unknown. */
    @Nullable
    public final Integer accentColor;

    private HearthProfile(String name, @Nullable Integer accentColor) {
        this.name = name;
        this.accentColor = accentColor;
    }

    @Nullable
    public static HearthProfile query(Context context) {
        if (context == null) {
            return null;
        }

        try (Cursor cursor = context.getContentResolver().query(ACTIVE_URI, null, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return null;
            }

            String name = getString(cursor, "name");

            if (name == null || name.trim().isEmpty()) {
                return null;
            }

            return new HearthProfile(name.trim(), parseColor(getString(cursor, "accent_color")));
        } catch (Exception e) {
            // Hearth missing or without the provider (SecurityException, IllegalArgumentException...)
            Log.d(TAG, "Hearth profile unavailable: %s", e.getMessage());
            return null;
        }
    }

    private static String getString(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index != -1 ? cursor.getString(index) : null;
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
