package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.browse;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
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
 * and tabs changes them), then "Queued (n)" while videos wait in the queue. The open one is white.
 * <p>
 * Like the strip under them (HearthRowsFragment's chips and Subscriptions' circles), a tab opens as focus lands on it,
 * and focus stays on it: only Down goes to the videos. It opens once focus rests there for a moment, so sweeping
 * across the tabs loads nothing on the way. OK opens it at once.
 */
public class HearthTabBar extends LinearLayout {
    /** The queue can change behind our back (phone casting, the player): look again now and then */
    private static final long QUEUE_CHECK_MS = 3_000;
    /** How long focus rests on a tab before it opens */
    private static final long FOCUS_OPEN_MS = 350;
    private static final int NO_SECTION = Integer.MIN_VALUE;
    /** The tab focus is resting on, waiting to open; null when none is */
    private View mPendingTab;
    private int mPendingId = NO_SECTION;
    private final Runnable mOpenPending = this::openPending;
    /** The tab focus opened and still rests on: OK there keeps it as it is rather than loading it again */
    private int mFocusOpenedId = NO_SECTION;
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
    /** Last count shown; -1 before the first */
    private int mLastCount = -1;

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

        if (!(context instanceof BrowseActivity)) {
            return false;
        }

        // Pages inside the browse screen (the sign-in prompt, empty pages) have title bars of their own, with the same
        // id: the browse screen's own has no error page around it
        boolean browseTitle = false;
        for (View v = this; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            if (v.getId() == androidx.leanback.R.id.error_frame) {
                return false;
            }
            if (v.getId() == androidx.leanback.R.id.browse_title_group) {
                browseTitle = true;
            }
        }

        return browseTitle;
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
        Utils.removeCallbacks(mOpenPending);
        mPendingTab = null;
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

        boolean refocus = false;

        if (!tabIds.equals(mTabIds) || mQueued == null) {
            refocus = rebuild(tabIds, titles);
        }

        // A tab waiting to open already shows as open
        int shownId = mPendingTab != null ? mPendingId : getCurrentId();

        for (int i = 0; i < mTabIds.size(); i++) {
            getChildAt(i).setSelected(mTabIds.get(i) == shownId);
        }

        mQueued.setSelected(shownId == MediaGroup.TYPE_PLAYBACK_QUEUE);
        updateQueued();

        if (refocus) {
            // Back on the open tab (the first one would open itself)
            View tab = getSelectedTab();
            if (tab != null) {
                tab.requestFocus();
            }
        }
    }

    private int getCurrentId() {
        BrowseSection current = BrowsePresenter.instance(getContext()).getCurrentSection();
        return current != null ? current.getId() : NO_SECTION;
    }

    /** @return the tabs had focus */
    private boolean rebuild(List<Integer> tabIds, List<String> titles) {
        View focused = findFocus();
        Utils.removeCallbacks(mOpenPending);
        mPendingTab = null;
        removeAllViews();
        mTabIds.clear();
        mTabIds.addAll(tabIds);

        for (int i = 0; i < tabIds.size(); i++) {
            addView(createTab(titles.get(i), tabIds.get(i)));
        }

        mQueued = createTab("", MediaGroup.TYPE_PLAYBACK_QUEUE);
        mQueued.setVisibility(GONE);
        addView(mQueued);

        return focused != null && getChildCount() > 0;
    }

    /** Focus landed on a tab, or left it */
    private void onTabFocus(View tab, int sectionId, boolean hasFocus) {
        if (!hasFocus) {
            if (tab == mPendingTab) {
                // Moved on before it opened (to the next tab, the search button...)
                cancelPending();
            }
            if (sectionId == mFocusOpenedId) {
                mFocusOpenedId = NO_SECTION;
            }
            return;
        }

        Utils.removeCallbacks(mOpenPending);
        mPendingTab = null;

        if (sectionId != getCurrentId()) {
            mPendingTab = tab;
            mPendingId = sectionId;
            Utils.postDelayed(mOpenPending, FOCUS_OPEN_MS);
        }

        update();
    }

    /** Focus rested on the tab: open it, and keep the focus on it */
    private void openPending() {
        View tab = mPendingTab;
        int sectionId = mPendingId;
        mPendingTab = null;

        // Gone, or already open (something else opened it meanwhile): nothing to load
        if (tab == null || !tab.isFocused() || !hasWindowFocus() || sectionId == getCurrentId()) {
            update();
            return;
        }

        mFocusOpenedId = sectionId;
        BrowsePresenter.instance(getContext()).selectSection(sectionId, false);
    }

    private void cancelPending() {
        if (mPendingTab == null) {
            return;
        }

        Utils.removeCallbacks(mOpenPending);
        mPendingTab = null;
        update();
    }

    /** OK opens the tab at once, or loads the open one again (as before), and keeps the focus on it */
    private void onTabClicked(int sectionId) {
        boolean pending = mPendingTab != null && mPendingId == sectionId;
        Utils.removeCallbacks(mOpenPending);
        mPendingTab = null;

        if (!pending && sectionId == mFocusOpenedId && sectionId == getCurrentId()) {
            // Focus opened it just now: OK only confirms it
            return;
        }

        mFocusOpenedId = sectionId;
        BrowsePresenter.instance(getContext()).selectSection(sectionId, false);
    }

    private boolean onTabKey(int keyCode, KeyEvent event) {
        if (mPendingTab == null || event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            // Down before the tab opened: open it now and go down to its videos once they're in
            int sectionId = mPendingId;
            Utils.removeCallbacks(mOpenPending);
            mPendingTab = null;
            BrowsePresenter.instance(getContext()).selectSection(sectionId, true);
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Back is about the open tab (BrowseActivity), not the one focus is passing over
            cancelPending();
        }

        return false;
    }

    /**
     * "Queued (4)": only while something waits to play, with the live count.
     */
    private void updateQueued() {
        if (mQueued == null) {
            return;
        }

        int count = HearthSections.getQueued().size();
        boolean show = count > 0;

        if (count != mLastCount && mLastCount != -1 && mQueued.isSelected()) {
            if (count == 0) {
                // Nothing left to play: the tab goes, and with it the empty list
                BrowsePresenter.instance(getContext()).selectSection(MediaGroup.TYPE_HOME);
            } else {
                // The list on screen follows the count (a queued video played, or one was added)
                BrowsePresenter.instance(getContext()).refresh(false);
            }
        }
        mLastCount = count;

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

    private TextView createTab(String title, int sectionId) {
        TextView tab = new TextView(getContext());
        tab.setText(title);
        tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15); // same as the chips under it
        tab.setTextColor(ContextCompat.getColorStateList(getContext(), R.color.hearth_tab_text));
        tab.setBackgroundResource(R.drawable.hearth_tab_background);
        tab.setSingleLine(true);
        tab.setGravity(Gravity.CENTER);
        tab.setPadding(dp(11), dp(6), dp(11), dp(6));
        tab.setFocusable(true);
        tab.setClickable(true);
        tab.setOnClickListener(v -> onTabClicked(sectionId));
        tab.setOnFocusChangeListener((v, hasFocus) -> onTabFocus(v, sectionId, hasFocus));
        tab.setOnKeyListener((v, keyCode, event) -> onTabKey(keyCode, event));

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
