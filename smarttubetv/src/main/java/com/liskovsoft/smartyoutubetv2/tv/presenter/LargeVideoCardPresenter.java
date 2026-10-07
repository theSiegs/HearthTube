package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;

/**
 * The big cards of HearthTube's one-row browse screens: about three and a half across, 16:9, picture only (the
 * focused video's title and details show above the row).
 */
public class LargeVideoCardPresenter extends VideoCardPresenter {
    /** Of the screen width (the approved concept: 27%, with a 1.8% gap) */
    public static final float WIDTH_FRACTION = 0.27f;

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        ViewHolder holder = super.onCreateViewHolder(parent);

        // No text block (and its gradient) over the picture
        View info = holder.view.findViewById(androidx.leanback.R.id.info_field);
        if (info != null) {
            info.setVisibility(View.GONE);
        }

        return holder;
    }

    @Override
    protected boolean isTitleEnabled() {
        return false;
    }

    @Override
    protected boolean isContentEnabled() {
        return false;
    }

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
