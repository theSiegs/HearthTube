package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.R;

/**
 * HearthTube: the parent's own say that a device is a kid's, for phones and tablets where no Hearth and no profile
 * system tells. Changing it needs the parent PIN (see {@link ParentGate#confirmParentPin}). Also tells the phone and
 * tablet build apart from the TV one.
 */
public final class KidsMode {
    private static final String PREFS = "hearthtube_kids_mode";
    private static final String KEY = "explicit";
    private static final String ON = "on";
    private static final String OFF = "off";

    private KidsMode() {
    }

    /** This is the phone and tablet build ({@code hearthtube_mobile_build} in its resources) */
    public static boolean isMobileBuild(Context context) {
        return context != null && context.getResources().getBoolean(R.bool.hearthtube_mobile_build);
    }

    /** True: a parent turned kids mode on; false: off; null: never set */
    @Nullable
    public static Boolean getExplicit(Context context) {
        String value = prefs(context).getString(KEY, null);
        return ON.equals(value) ? Boolean.TRUE : OFF.equals(value) ? Boolean.FALSE : null;
    }

    public static void setExplicit(Context context, @Nullable Boolean kids) {
        SharedPreferences.Editor editor = prefs(context).edit();

        if (kids == null) {
            editor.remove(KEY);
        } else {
            editor.putString(KEY, kids ? ON : OFF);
        }

        editor.apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
