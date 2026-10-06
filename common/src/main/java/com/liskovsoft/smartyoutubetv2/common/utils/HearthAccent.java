package com.liskovsoft.smartyoutubetv2.common.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.prefs.HearthLinkData;

/**
 * The accent color (focus rings, the open tab, progress bars): Hearth's own (Match Hearth, the default) or one of
 * Hearth's 15, picked in Look and layout. Applied over each screen's theme as it opens (MotherActivity).
 */
public final class HearthAccent {
    /** Hearth's accents, in its order: name, color, theme overlay */
    public static final int[][] ACCENTS = {
            {R.string.accent_purple, 0xFF7C4DFF, R.style.HearthAccent_Purple},
            {R.string.accent_teal, 0xFF00BFA5, R.style.HearthAccent_Teal},
            {R.string.accent_blue, 0xFF2979FF, R.style.HearthAccent_Blue},
            {R.string.accent_orange, 0xFFFF6D00, R.style.HearthAccent_Orange},
            {R.string.accent_pink, 0xFFF50057, R.style.HearthAccent_Pink},
            {R.string.accent_green, 0xFF00C853, R.style.HearthAccent_Green},
            {R.string.accent_white, 0xFFFFFFFF, R.style.HearthAccent_White},
            {R.string.accent_yellow, 0xFFFFD600, R.style.HearthAccent_Yellow},
            {R.string.accent_red, 0xFFD50000, R.style.HearthAccent_Red},
            {R.string.accent_cyan, 0xFF00E5FF, R.style.HearthAccent_Cyan},
            {R.string.accent_indigo, 0xFF536DFE, R.style.HearthAccent_Indigo},
            {R.string.accent_lime, 0xFFAEEA00, R.style.HearthAccent_Lime},
            {R.string.accent_amber, 0xFFFFAB00, R.style.HearthAccent_Amber},
            {R.string.accent_rose, 0xFFFF4081, R.style.HearthAccent_Rose},
            {R.string.accent_ice_blue, 0xFF80D8FF, R.style.HearthAccent_IceBlue},
    };

    private HearthAccent() {
    }

    public static void applyTo(Activity activity) {
        activity.getTheme().applyStyle(styleFor(resolve(activity)), true);
    }

    /** The accent in use */
    public static int resolve(Context context) {
        Integer chosen = HearthLinkData.instance(context).getAccentColor();

        if (chosen != null) {
            return chosen;
        }

        HearthProfile hearth = HearthProfile.queryHearth(context);
        return hearth != null && hearth.accentColor != null ? hearth.accentColor : ACCENTS[0][1];
    }

    /** Hearth only offers these 15, so its color is one of them; anything else gets the closest */
    private static int styleFor(int color) {
        int best = 0;
        long bestDistance = Long.MAX_VALUE;

        for (int i = 0; i < ACCENTS.length; i++) {
            int other = ACCENTS[i][1];
            long dr = Color.red(color) - Color.red(other);
            long dg = Color.green(color) - Color.green(other);
            long db = Color.blue(color) - Color.blue(other);
            long distance = dr * dr + dg * dg + db * db;

            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }

        return ACCENTS[best][2];
    }
}
