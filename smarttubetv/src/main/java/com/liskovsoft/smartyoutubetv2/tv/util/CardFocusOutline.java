package com.liskovsoft.smartyoutubetv2.tv.util;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build.VERSION;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Hearth-style card focus: the focused card keeps its colors and gets an accent outline,
 * instead of leanback's white info area. Only color schemes that set {@code cardFocusOutline}
 * (the Hearth scheme) use it; everything else keeps the classic look.
 */
public final class CardFocusOutline {
    private CardFocusOutline() {
    }

    /**
     * @return the outline drawable resource of the current theme, or 0 for the classic look
     */
    public static int get(Context context) {
        // Foreground drawables on plain views need API 23
        if (context == null || VERSION.SDK_INT < 23) {
            return 0;
        }

        TypedArray attrs = context.obtainStyledAttributes(new int[] {R.attr.cardFocusOutline});
        int resId = attrs.getResourceId(0, 0);
        attrs.recycle();

        return resId;
    }

    /**
     * Shows or hides the outline. A new drawable per card: drawables keep their bounds.
     */
    public static void apply(View card, int outlineResId, boolean focused) {
        if (outlineResId == 0 || card == null || VERSION.SDK_INT < 23) {
            return;
        }

        card.setForeground(focused ? ContextCompat.getDrawable(card.getContext(), outlineResId) : null);
    }
}
