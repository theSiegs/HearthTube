package com.thesiegs.hearthtube.mobile.playback;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.thesiegs.hearthtube.mobile.ui.HearthLook;

import java.util.HashMap;
import java.util.Map;

/**
 * The row of buttons under the phone player's title (shown with the controls): what the TV has as player buttons. A
 * button is a pill that is lit while its state is on (liked, subscribed). What each one does is the TV's own code,
 * run by the caller.
 */
final class PlayerActions {
    private final HorizontalScrollView mScroll;
    private final LinearLayout mRow;
    private final Map<Integer, TextView> mButtons = new HashMap<>();

    PlayerActions(Context context) {
        mScroll = new HorizontalScrollView(context);
        mScroll.setHorizontalScrollBarEnabled(false);
        mScroll.setBackgroundColor(0x66000000);
        mRow = new LinearLayout(context);
        mRow.setOrientation(LinearLayout.HORIZONTAL);
        int pad = HearthLook.dp(context, 8);
        mRow.setPadding(pad, 0, pad, pad);
        mScroll.addView(mRow, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    View view() {
        return mScroll;
    }

    /** Adds a button; {@code id} is what {@link #setOn} later finds it by (any number, 0 for a button that has no state) */
    void add(int id, String label, Runnable click) {
        Context context = mRow.getContext();
        TextView button = new TextView(context);
        button.setText(label);
        button.setSingleLine(true);
        button.setGravity(android.view.Gravity.CENTER);
        button.setMinHeight(HearthLook.dp(context, 36));
        button.setPadding(HearthLook.dp(context, 14), 0, HearthLook.dp(context, 14), 0);
        button.setTextSize(14);
        HearthLook.chip(button, false);
        button.setOnClickListener(v -> click.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = HearthLook.dp(context, 6);
        mRow.addView(button, params);

        if (id != 0) {
            mButtons.put(id, button);
        }
    }

    /** Lights or dims the button whose state the player changed (a like, a subscribe) */
    void setOn(int id, boolean on) {
        TextView button = mButtons.get(id);

        if (button != null) {
            HearthLook.chip(button, on);
        }
    }

    void setVisible(int id, boolean visible) {
        TextView button = mButtons.get(id);

        if (button != null) {
            button.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    void setLabel(int id, String label) {
        TextView button = mButtons.get(id);

        if (button != null) {
            button.setText(label);
        }
    }
}
