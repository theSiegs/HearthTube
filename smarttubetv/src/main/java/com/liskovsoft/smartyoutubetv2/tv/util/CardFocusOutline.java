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

    /** Hearth's focus border pulses between 40% and full strength, 1.2s each way */
    private static final long PULSE_MS = 1_200;

    /**
     * Shows or hides the outline. A new drawable per card: drawables keep their bounds. Like Hearth, the focused
     * card's border pulses and the other cards are dimmed a little.
     */
    public static void apply(View card, int outlineResId, boolean focused) {
        if (outlineResId == 0 || card == null || VERSION.SDK_INT < 23) {
            return;
        }

        Object running = card.getTag(R.id.hearth_focus_pulse);
        if (running instanceof android.animation.Animator) {
            ((android.animation.Animator) running).cancel();
            card.setTag(R.id.hearth_focus_pulse, null);
        }

        if (!focused) {
            card.setForeground(ContextCompat.getDrawable(card.getContext(), R.drawable.hearth_card_unfocused));
            return;
        }

        android.graphics.drawable.Drawable outline = ContextCompat.getDrawable(card.getContext(), outlineResId);
        card.setForeground(outline);

        if (outline != null) {
            android.animation.ObjectAnimator pulse = android.animation.ObjectAnimator.ofInt(outline, "alpha", 255, 102);
            pulse.setDuration(PULSE_MS);
            pulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            pulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            pulse.addUpdateListener(animation -> {
                if (!card.isAttachedToWindow()) {
                    animation.cancel(); // the card left the screen while focused (opening a video)
                    return;
                }
                card.invalidate();
            });
            pulse.start();
            card.setTag(R.id.hearth_focus_pulse, pulse);
        }
    }
}
