package com.thesiegs.hearthtube.mobile.browse;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.thesiegs.hearthtube.mobile.R;
import com.thesiegs.hearthtube.mobile.ui.HearthLook;

import java.util.ArrayList;
import java.util.List;

/**
 * The bottom bar on a phone, the side rail on a tablet: Home, Subscriptions, Library and Search as floating dark glass
 * with the shown one in white, like the section chips. Every item is at least 48 dp each way, has a name for screen
 * readers, and its label follows the system font size. Which items there are is decided by {@link NavRules}.
 */
final class NavBar extends LinearLayout {
    interface Listener {
        void onDestination(Destination destination);
    }

    private final Listener mListener;
    private final List<Destination> mShown = new ArrayList<>();
    private final List<View> mItems = new ArrayList<>();
    private boolean mRail;

    NavBar(Context context, Listener listener) {
        super(context);
        mListener = listener;
        setGravity(Gravity.CENTER);
        setBaselineAligned(false); // the items' icons have no baseline: aligning on one adds a gap above them
        HearthLook.glassBar(this);
        setPadding(dp(6), dp(6), dp(6), dp(6));
        setClipToPadding(false);
        setRail(false);
    }

    /** A tablet's rail stands on the start edge; a phone's bar lies along the bottom */
    void setRail(boolean rail) {
        mRail = rail;
        setOrientation(rail ? VERTICAL : HORIZONTAL);
        relayoutItems();
    }

    /** The items to show and which one is the shown one (Search is never shown: it opens its own screen) */
    void setDestinations(List<Destination> destinations, Destination current) {
        if (!destinations.equals(mShown)) {
            mShown.clear();
            mShown.addAll(destinations);
            mItems.clear();

            for (Destination destination : destinations) {
                mItems.add(createItem(destination));
            }

            relayoutItems();
        }

        for (int i = 0; i < mItems.size(); i++) {
            style(mItems.get(i), mShown.get(i) == current);
        }
    }

    private void relayoutItems() {
        removeAllViews();

        for (View item : mItems) {
            LayoutParams params = mRail
                    ? new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    : new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            params.setMargins(dp(2), dp(2), dp(2), dp(2));
            addView(item, params);

            // A rail is as wide as its widest label, so nothing needs to shrink; a bar's items share the width
            TextView text = (TextView) ((LinearLayout) item).getChildAt(1);
            if (mRail) {
                text.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE);
                text.setTextSize(12);
                text.setEllipsize(null);
            } else {
                text.setAutoSizeTextTypeUniformWithConfiguration(9, 12, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
                text.setEllipsize(TextUtils.TruncateAt.END);
            }
        }
    }

    private View createItem(Destination destination) {
        String label = getContext().getString(labelOf(destination));

        LinearLayout item = new LinearLayout(getContext());
        item.setOrientation(VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setMinimumHeight(dp(56));
        item.setMinimumWidth(dp(64));
        item.setPadding(dp(8), dp(6), dp(8), dp(6));
        item.setContentDescription(label);
        item.setClickable(true);
        item.setFocusable(true);
        item.setOnClickListener(v -> mListener.onDestination(destination));

        ImageView icon = new ImageView(getContext());
        icon.setImageResource(iconOf(destination));
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        item.addView(icon, new LayoutParams(dp(24), dp(24)));

        TextView text = new TextView(getContext());
        text.setText(label);
        text.setTextSize(12);
        text.setSingleLine(true);
        // A big system font would cut "Subscriptions" short: let the label shrink a little, down to 9 sp, before it does
        text.setAutoSizeTextTypeUniformWithConfiguration(9, 12, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setGravity(Gravity.CENTER);
        text.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        item.addView(text, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return item;
    }

    private static void style(View item, boolean shown) {
        HearthLook.navItem(item, shown);
        int color = shown ? Color.BLACK : Color.WHITE;
        LinearLayout group = (LinearLayout) item;
        ((ImageView) group.getChildAt(0)).setColorFilter(color);
        ((TextView) group.getChildAt(1)).setTextColor(color);
    }

    private static int labelOf(Destination destination) {
        switch (destination) {
            case SUBSCRIPTIONS:
                return R.string.nav_subscriptions;
            case LIBRARY:
                return R.string.nav_library;
            case SEARCH:
                return R.string.nav_search;
            default:
                return R.string.nav_home;
        }
    }

    private static int iconOf(Destination destination) {
        switch (destination) {
            case SUBSCRIPTIONS:
                return R.drawable.ic_nav_subscriptions;
            case LIBRARY:
                return R.drawable.ic_nav_library;
            case SEARCH:
                return R.drawable.ic_nav_search;
            default:
                return R.drawable.ic_nav_home;
        }
    }

    private int dp(int dp) {
        return HearthLook.dp(getContext(), dp);
    }
}
