package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * HearthTube: the player's controls, quiet by default. Only play/pause, previous and next show, with a More button
 * after them; the rest of the buttons (both rows) appear when you move right onto More (or click it), and fold away when the controls
 * hide. Kept on the transport row view's tag, so the fragment can fold them.
 */
public final class HearthControlsCollapser {
    /** Buttons that always show: play/pause, previous, next */
    private static final int ALWAYS_SHOWN = 3;
    private final ViewGroup mControlsDock;
    private final ViewGroup mSecondaryDock;
    private final ImageView mMore;
    private boolean mExpanded;

    private HearthControlsCollapser(ViewGroup controlsDock, ViewGroup secondaryDock) {
        mControlsDock = controlsDock;
        mSecondaryDock = secondaryDock;
        Context context = controlsDock.getContext();
        int size = Math.round(48 * context.getResources().getDisplayMetrics().density);
        int pad = Math.round(10 * context.getResources().getDisplayMetrics().density);

        mMore = new ImageView(context);
        mMore.setId(View.generateViewId());
        mMore.setImageResource(R.drawable.ic_player_more);
        mMore.setBackgroundResource(R.drawable.hearth_player_more_background);
        mMore.setPadding(pad, pad, pad, pad);
        mMore.setFocusable(true);
        mMore.setContentDescription(context.getString(R.string.more_videos));
        mMore.setOnClickListener(v -> expand());

        // Reached from Next (moving right): open up. Reached from elsewhere (Up from the progress bar finds it as
        // the nearest button): go to play/pause instead, still folded.
        controlsDock.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (newFocus != mMore || mExpanded) {
                return;
            }

            ViewGroup bar = findBar();
            if (oldFocus != null && bar != null && isInside(oldFocus, bar)) {
                expand();
            } else if (bar != null && bar.getChildCount() > 0) {
                mMore.post(() -> bar.getChildAt(0).requestFocus());
            }
        });

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(size, size);
        params.addRule(RelativeLayout.END_OF, controlsDock.getId());
        params.addRule(RelativeLayout.CENTER_VERTICAL);
        ((ViewGroup) controlsDock.getParent()).addView(mMore, params);

        // Buttons get rebuilt as the video changes, and fade out while seeking: keep up, frame by frame
        controlsDock.getViewTreeObserver().addOnPreDrawListener(() -> {
            apply();
            return true;
        });
    }

    /** Sets up the row's controls (once per row view) */
    public static void attach(View transportRow, ViewGroup controlsDock, ViewGroup secondaryDock) {
        if (transportRow.getTag(R.id.transport_row) == null && controlsDock.getParent() instanceof RelativeLayout) {
            transportRow.setTag(R.id.transport_row, new HearthControlsCollapser(controlsDock, secondaryDock));
        }
    }

    /** Folds the controls of the player view's row, if any (the controls hid) */
    public static void collapse(View playerView) {
        View row = playerView != null ? playerView.findViewById(R.id.transport_row) : null;
        Object collapser = row != null ? row.getTag(R.id.transport_row) : null;

        if (collapser instanceof HearthControlsCollapser) {
            ((HearthControlsCollapser) collapser).collapse();
        }
    }

    private void expand() {
        if (mExpanded) {
            return;
        }

        mExpanded = true;
        apply();

        // On to the first button that was hidden
        ViewGroup bar = findBar();
        if (bar != null && bar.getChildCount() > ALWAYS_SHOWN) {
            mControlsDock.post(() -> bar.getChildAt(ALWAYS_SHOWN).requestFocus());
        }
    }

    private void collapse() {
        if (!mExpanded) {
            return;
        }

        mExpanded = false;
        apply();
    }

    private void apply() {
        ViewGroup bar = findBar();
        int buttons = bar != null ? bar.getChildCount() : 0;

        if (bar != null) {
            for (int i = ALWAYS_SHOWN; i < buttons; i++) {
                setVisibility(bar.getChildAt(i), mExpanded ? View.VISIBLE : View.GONE);
            }
        }

        boolean hasHidden = buttons > ALWAYS_SHOWN || (mSecondaryDock != null && hasButtons(mSecondaryDock));
        // Hidden with the buttons too (seeking hides them)
        boolean controlsShown = isShown(mControlsDock) && (bar == null || isShown(bar));
        setVisibility(mMore, !mExpanded && hasHidden && controlsShown ? View.VISIBLE : View.GONE);

        if (mSecondaryDock != null) {
            setVisibility(mSecondaryDock, mExpanded ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private static boolean isInside(View view, ViewGroup parent) {
        for (View v = view; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            if (v == parent) {
                return true;
            }
        }

        return false;
    }

    private static boolean isShown(View view) {
        return view.getVisibility() == View.VISIBLE && view.getAlpha() > 0.5f;
    }

    private static void setVisibility(View view, int visibility) {
        if (view.getVisibility() != visibility) {
            view.setVisibility(visibility);
        }
    }

    /** The primary control bar: the dock's view holder wraps it */
    private ViewGroup findBar() {
        View bar = mControlsDock.findViewById(R.id.control_bar);
        return bar instanceof ViewGroup ? (ViewGroup) bar : null;
    }

    private static boolean hasButtons(ViewGroup dock) {
        View bar = dock.findViewById(R.id.control_bar);
        return bar instanceof ViewGroup && ((ViewGroup) bar).getChildCount() > 0;
    }
}
