package com.thesiegs.hearthtube.mobile.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * The strip of sub-pills under a screen's top bar, like the TV's strip under its tabs: one pill per titled row of the
 * {@link VideoGrid} it belongs to (Ambiance: Fireplace, Rain; a channel: Videos, Playlists; a search's shelves), a
 * channel's circle in Subscriptions. The picked one is white (a circle has a ring in the accent) and the grid shows its
 * row. It is shown only while the grid has two or more rows. Every screen with a grid uses this one.
 */
public class SubPillStrip extends HorizontalScrollView {
    /** A long press on a pill (a channel's circle opens its menu); true when it was handled */
    public interface LongPress {
        boolean onLongPress(VideoRows.Pill pill);
    }

    private final VideoGrid mGrid;
    @Nullable
    private final LongPress mLongPress;
    private final LinearLayout mBar;
    private final List<View> mViews = new ArrayList<>();
    private String mSignature;

    public SubPillStrip(Context context, VideoGrid grid, @Nullable LongPress longPress) {
        super(context);
        mGrid = grid;
        mLongPress = longPress;

        setHorizontalScrollBarEnabled(false);
        setVisibility(GONE);

        mBar = new LinearLayout(context);
        mBar.setOrientation(LinearLayout.HORIZONTAL);
        mBar.setGravity(Gravity.CENTER_VERTICAL);
        mBar.setPadding(dp(8), 0, dp(8), dp(8));
        addView(mBar);

        grid.setStripListener(this::show);
    }

    private void show(List<VideoRows.Pill> pills, int selected) {
        if (pills.isEmpty()) {
            setVisibility(GONE);
            mBar.removeAllViews();
            mViews.clear();
            mSignature = null;
            return;
        }

        String signature = signatureOf(pills);
        setVisibility(VISIBLE);

        // Rows arrive one after another while a section loads: rebuild only when the pills are different, and keep the place
        if (!signature.equals(mSignature)) {
            mSignature = signature;
            int scrollX = getScrollX();
            mBar.removeAllViews();
            mViews.clear();

            for (int i = 0; i < pills.size(); i++) {
                View view = createPill(pills.get(i), i);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.setMargins(dp(4), 0, dp(pills.get(i).iconUrl != null ? 8 : 4), 0);
                mViews.add(view);
                mBar.addView(view, params);
            }

            post(() -> scrollTo(scrollX, 0));
        }

        style(selected);
    }

    /** What the pills look like: a change in it rebuilds them, the same pills picked differently only restyle them */
    static String signatureOf(List<VideoRows.Pill> pills) {
        StringBuilder signature = new StringBuilder();

        for (VideoRows.Pill pill : pills) {
            signature.append(pill.title).append('|').append(pill.iconUrl != null).append(';');
        }

        return signature.toString();
    }

    private View createPill(VideoRows.Pill pill, int index) {
        if (pill.iconUrl == null) {
            TextView text = new TextView(getContext());
            text.setText(pill.title);
            text.setTextSize(13);
            text.setSingleLine(true);
            text.setPadding(dp(12), dp(6), dp(12), dp(6));
            HearthLook.chip(text, false);
            text.setOnClickListener(v -> mGrid.selectRow(index));
            return text;
        }

        // A channel in Subscriptions: its picture in a circle with its name, like the TV's circles
        LinearLayout circle = new LinearLayout(getContext());
        circle.setOrientation(LinearLayout.VERTICAL);
        circle.setGravity(Gravity.CENTER_HORIZONTAL);

        FrameLayout ring = new FrameLayout(getContext());
        ring.setPadding(dp(3), dp(3), dp(3), dp(3));
        ImageView image = new ImageView(getContext());
        Glide.with(getContext()).load(pill.iconUrl).apply(RequestOptions.circleCropTransform()).into(image);
        ring.addView(image, new FrameLayout.LayoutParams(dp(48), dp(48)));
        circle.addView(ring);

        TextView name = new TextView(getContext());
        name.setText(pill.title);
        name.setTextSize(11);
        name.setTextColor(android.graphics.Color.WHITE);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER_HORIZONTAL);
        name.setMaxWidth(dp(64));
        HearthLook.textShadow(name);
        circle.addView(name);

        circle.setOnClickListener(v -> mGrid.selectRow(index));
        circle.setOnLongClickListener(v -> mLongPress != null && mLongPress.onLongPress(pill));
        return circle;
    }

    /** The picked pill is white (a circle has a ring in the accent); the others are dark glass */
    private void style(int selected) {
        for (int i = 0; i < mViews.size(); i++) {
            View view = mViews.get(i);

            if (view instanceof TextView) {
                HearthLook.chip((TextView) view, i == selected);
            } else if (view instanceof LinearLayout) {
                HearthLook.circleRing(((LinearLayout) view).getChildAt(0), i == selected);
            }
        }
    }

    private int dp(int dp) {
        return HearthLook.dp(getContext(), dp);
    }
}
