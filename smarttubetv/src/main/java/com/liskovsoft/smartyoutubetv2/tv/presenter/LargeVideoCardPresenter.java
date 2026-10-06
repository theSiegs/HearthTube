package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Pair;

/**
 * The big cards of HearthTube's one-row browse screens: about three and a half across, 16:9.
 */
public class LargeVideoCardPresenter extends VideoCardPresenter {
    /** Of the screen width (the approved concept: 27%, with a 1.8% gap) */
    public static final float WIDTH_FRACTION = 0.27f;

    @Override
    protected Pair<Integer, Integer> getCardDimensPx(Context context) {
        return getLargeCardDimensPx(context);
    }

    public static Pair<Integer, Integer> getLargeCardDimensPx(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int width = Math.round(metrics.widthPixels * WIDTH_FRACTION);
        return new Pair<>(width, Math.round(width * 9 / 16f));
    }
}
