package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.browse;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.HearthSections;
import com.liskovsoft.smartyoutubetv2.common.prefs.HearthTabsData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.BrowseActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * The tabs at the top of the browse screen, in one pill: Home, Subscriptions, Music, Ambiance, Library (Edit menu
 * and tabs changes them), then "Queued (n)" while videos wait in the queue. OK opens a tab; the open one is white.
 */
public class HearthTabBar extends LinearLayout {
    /** The queue can change behind our back (phone casting, the player): look again now and then */
    private static final long QUEUE_CHECK_MS = 3_000;
    private final Runnable mOnSectionChange = this::update;
    private final Runnable mQueueCheck = new Runnable() {
        @Override
        public void run() {
            updateQueued();
            Utils.postDelayed(this, QUEUE_CHECK_MS);
        }
    };
    private final List<Integer> mTabIds = new ArrayList<>();
    private TextView mQueued;

    public HearthTabBar(Context context) {
        super(context);
        init();
    }

    public HearthTabBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundResource(R.drawable.hearth_tab_bar_background);
        int pad = dp(4);
        setPadding(pad, pad, pad, pad);
        setFocusable(false);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
    }

    /** The title bar layout is shared by every screen; the tabs belong to the browse screen only */
    private boolean isBrowse() {
        Context context = getContext();

        while (context instanceof ContextWrapper && !(context instanceof Activity)) {
            context = ((ContextWrapper) context).getBaseContext();
        }

        return context instanceof BrowseActivity;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!isBrowse()) {
            setVisibility(GONE);
            return;
        }

        BrowsePresenter.instance(getContext()).addOnSectionChange(mOnSectionChange);
        update();
        Utils.postDelayed(mQueueCheck, QUEUE_CHECK_MS);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        BrowsePresenter.instance(getContext()).removeOnSectionChange(mOnSectionChange);
        Utils.removeCallbacks(mQueueCheck);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);

        if (getVisibility() != VISIBLE) {
            return;
        }

        // Back from a menu (Add to queue, Edit menu and tabs) or the player
        if (hasWindowFocus) {
            update();
        }
    }

    /**
     * The tab of the open section, to focus when moving up from the videos.
     */
    public View getSelectedTab() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.isSelected() && child.getVisibility() == VISIBLE) {
                return child;
            }
        }

        return getChildCount() > 0 ? getChildAt(0) : null;
    }

    private void update() {
        BrowsePresenter presenter = BrowsePresenter.instance(getContext());
        List<Integer> tabIds = new ArrayList<>();
        List<String> titles = new ArrayList<>();

        // Tabs in the order the user chose; sections switched off don't show
        for (int id : HearthTabsData.instance(getContext()).getTabs()) {
            for (BrowseSection section : presenter.getSections()) {
                if (section.getId() == id) {
                    tabIds.add(id);
                    titles.add(section.getTitle());
                    break;
                }
            }
        }

        if (!tabIds.equals(mTabIds) || mQueued == null) {
            rebuild(tabIds, titles);
        }

        BrowseSection current = presenter.getCurrentSection();
        int currentId = current != null ? current.getId() : -1;

        for (int i = 0; i < mTabIds.size(); i++) {
            getChildAt(i).setSelected(mTabIds.get(i) == currentId);
        }

        mQueued.setSelected(currentId == MediaGroup.TYPE_PLAYBACK_QUEUE);
        updateQueued();
    }

    private void rebuild(List<Integer> tabIds, List<String> titles) {
        View focused = findFocus();
        removeAllViews();
        mTabIds.clear();
        mTabIds.addAll(tabIds);

        for (int i = 0; i < tabIds.size(); i++) {
            int id = tabIds.get(i);
            addView(createTab(titles.get(i), v -> BrowsePresenter.instance(getContext()).selectSection(id)));
        }

        mQueued = createTab("", v -> BrowsePresenter.instance(getContext()).selectSection(MediaGroup.TYPE_PLAYBACK_QUEUE));
        mQueued.setVisibility(GONE);
        addView(mQueued);

        if (focused != null && getChildCount() > 0) {
            getChildAt(0).requestFocus();
        }
    }

    /**
     * "Queued (4)": only while something waits to play, with the live count.
     */
    private void updateQueued() {
        if (mQueued == null) {
            return;
        }

        int count = HearthSections.getQueued().size();
        boolean show = count > 0 || mQueued.isSelected();

        if (show) {
            mQueued.setText(getContext().getString(R.string.header_queued, count));
        }

        if (show != (mQueued.getVisibility() == VISIBLE)) {
            // Moving focus off a tab that's about to go
            if (!show && mQueued.hasFocus() && getChildCount() > 1) {
                getChildAt(0).requestFocus();
            }
            mQueued.setVisibility(show ? VISIBLE : GONE);
        }
    }

    private TextView createTab(String title, OnClickListener onClick) {
        TextView tab = new TextView(getContext());
        tab.setText(title);
        tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tab.setTextColor(ContextCompat.getColorStateList(getContext(), R.color.hearth_tab_text));
        tab.setBackgroundResource(R.drawable.hearth_tab_background);
        tab.setSingleLine(true);
        tab.setGravity(Gravity.CENTER);
        tab.setPadding(dp(11), dp(6), dp(11), dp(6));
        tab.setFocusable(true);
        tab.setClickable(true);
        tab.setOnClickListener(onClick);

        LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(1);
        params.rightMargin = dp(1);
        tab.setLayoutParams(params);

        return tab;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
