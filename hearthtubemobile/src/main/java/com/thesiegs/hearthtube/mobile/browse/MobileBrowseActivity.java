package com.thesiegs.hearthtube.mobile.browse;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.HearthSections;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.HearthChannelMenu;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.utils.ParentGate;
import com.thesiegs.hearthtube.mobile.R;
import com.thesiegs.hearthtube.mobile.parent.MobileParentMenu;
import com.thesiegs.hearthtube.mobile.ui.HearthActivity;
import com.thesiegs.hearthtube.mobile.ui.HearthLook;
import com.thesiegs.hearthtube.mobile.ui.KidGate;
import com.thesiegs.hearthtube.mobile.ui.SubPillStrip;
import com.thesiegs.hearthtube.mobile.ui.VideoGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * HearthTube Mobile's home screen: the sections as chips across the top and the current section's videos below. The
 * shared {@link BrowsePresenter} drives it exactly as it drives the TV's browse screen.
 * <p>
 * A kid who isn't signed in sees no videos at all, not only no playback: what YouTube lists signed out isn't supervised.
 */
public class MobileBrowseActivity extends HearthActivity implements BrowseView {
    /** The TV's own tabs: a play queue, notifications, blocked channels and its settings tab (a parent has the Parent button) */
    private static final int[] HIDDEN_SECTIONS = {
            MediaGroup.TYPE_PLAYBACK_QUEUE, MediaGroup.TYPE_NOTIFICATIONS, MediaGroup.TYPE_BLOCKED_CHANNELS, MediaGroup.TYPE_SETTINGS
    };

    private final List<BrowseSection> mSections = new ArrayList<>();
    private final List<TextView> mChips = new ArrayList<>();

    private BrowsePresenter mPresenter;
    private HorizontalScrollView mChipScroll;
    private TextView mParentButton;
    private LinearLayout mChipBar;
    /** The section pills and, under them, the sub-pills; it floats over the list and slides away on a phone as it scrolls */
    private LinearLayout mHeader;
    private boolean mHeaderHidden;
    private VideoGrid mGrid;
    private boolean mGated;
    /** A selection asked for before the chips were there (the presenter picks the start section first) */
    private int mPendingSelect = -1;
    private int mShownSectionId = -1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(createContent());

        mPresenter = BrowsePresenter.instance(this);
        mPresenter.setView(this);
        mPresenter.onViewInitialized();
    }

    /** A phone's and tablet's own density: the TV's fixed-width scaling would make everything too big or small */
    @Override
    protected void initDpi() {
    }

    /** The color scheme's themes are the TV's (Leanback); this app has its own */
    @Override
    protected void initTheme() {
    }

    @Override
    protected void onResume() {
        super.onResume();
        mPresenter.onViewResumed();

        // A kid's device has a Parent button; anyone else has Settings
        mParentButton.setText(ParentGate.isKidsProfile(this) ? R.string.parent_button : R.string.settings_button);

        // A parent signed in, or turned kids mode off, in the meantime: show what the presenter has
        if (mGated && !isGated()) {
            mGated = false;
            mChipScroll.setVisibility(View.VISIBLE);
            mGrid.showMessage(null);
            mPresenter.refresh(false);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPresenter.onViewPaused();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPresenter.onViewDestroyed();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mGrid.onConfigurationChanged();
    }

    private View createContent() {
        FrameLayout root = new FrameLayout(this);

        mChipScroll = new HorizontalScrollView(this);
        mChipScroll.setHorizontalScrollBarEnabled(false);
        mChipBar = new LinearLayout(this);
        mChipBar.setOrientation(LinearLayout.HORIZONTAL);
        mChipBar.setPadding(dp(8), dp(8), dp(8), dp(8));
        mChipScroll.addView(mChipBar);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(mChipScroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(barButton(getString(R.string.search_button), v -> com.thesiegs.hearthtube.mobile.search.MobileSearchActivity.start(this)), barButtonParams());
        mParentButton = barButton(getString(R.string.parent_button), v -> MobileParentMenu.show(this));
        bar.addView(mParentButton, barButtonParams());

        mGrid = new VideoGrid(this, new VideoGrid.Listener() {
            @Override
            public void onClick(Video video) {
                mPresenter.onVideoItemClicked(video);
            }

            @Override
            public void onLongClick(Video video) {
                KidGate.parentOnly(MobileBrowseActivity.this, () -> mPresenter.onVideoItemLongClicked(video));
            }

            @Override
            public void onScrollEnd(Video last) {
                mPresenter.onScrollEnd(last);
            }
        });

        // A section with several rows (Ambiance: Fireplace, Rain...) has a strip of sub-pills under the section pills; in
        // Subscriptions a long press on a channel's circle opens its menu (a parent's, on a kid's device)
        SubPillStrip strip = new SubPillStrip(this, mGrid, pill -> {
            if (!(pill.tag instanceof HearthSections.TitledGroup)) {
                return false;
            }

            KidGate.parentOnly(this, () -> HearthChannelMenu.show(this, (HearthSections.TitledGroup) pill.tag,
                    channel -> mGrid.removeChannel(pill.tag, video -> HearthSections.isSameChannel(
                            channel.channelId, channel.title, video.channelId, video.getAuthor()))));
            return true;
        });

        mHeader = new LinearLayout(this);
        mHeader.setOrientation(LinearLayout.VERTICAL);
        mHeader.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mHeader.addView(strip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(mGrid, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(mHeader, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // The list starts below the header, whatever its height (with or without the strip)
        mHeader.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> mGrid.setTopInset(bottom - top));
        mGrid.setScrollWatcher(this::onGridScrolled);
        return root;
    }

    // Hide on scroll: on a phone the pills slide away as the list goes down and come back as it goes up, so the videos
    // get the screen; a tablet has room to keep them

    private void onGridScrolled(int dy, boolean atTop) {
        if (getResources().getConfiguration().smallestScreenWidthDp >= 600) {
            return;
        }

        if (atTop || dy < -dp(4)) {
            showHeader();
        } else if (dy > dp(4)) {
            hideHeader();
        }
    }

    private void hideHeader() {
        if (!mHeaderHidden && mHeader.getHeight() > 0) {
            mHeaderHidden = true;
            mHeader.animate().translationY(-mHeader.getHeight()).setDuration(150).start();
        }
    }

    private void showHeader() {
        if (mHeaderHidden) {
            mHeaderHidden = false;
            mHeader.animate().translationY(0).setDuration(150).start();
        }
    }

    private TextView barButton(String text, View.OnClickListener onClick) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextSize(14);
        button.setPadding(dp(14), dp(8), dp(14), dp(8));
        HearthLook.accentButton(button);
        button.setOnClickListener(onClick);
        return button;
    }

    private LinearLayout.LayoutParams barButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(2), 0, dp(4), 0);
        return params;
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** What YouTube lists signed out isn't supervised: a kid who isn't signed in gets no videos, not just no playback */
    private boolean isGated() {
        return KidGate.isClosed(this);
    }

    private void showGate() {
        mGated = true;
        mGrid.clear();
        mGrid.showProgress(false);
        mChipScroll.setVisibility(View.INVISIBLE);
        mGrid.showMessage(getString(R.string.gated_title));
    }

    // BrowseView

    @Override
    public void addSection(int index, BrowseSection section) {
        if (section == null || isHidden(section) || indexOf(section.getId()) >= 0) {
            return;
        }

        int at = index < 0 || index > mSections.size() ? mSections.size() : index;
        mSections.add(at, section);

        TextView chip = new TextView(this);
        chip.setText(section.getTitle());
        chip.setTextSize(14);
        chip.setPadding(dp(14), dp(8), dp(14), dp(8));
        HearthLook.chip(chip, false);
        chip.setOnClickListener(v -> selectSection(mSections.indexOf(section), false));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(4), 0, dp(4), 0);
        mChips.add(at, chip);
        mChipBar.addView(chip, at, params);

        if (mPendingSelect >= 0 && mPendingSelect < mSections.size()) {
            int pending = mPendingSelect;
            mPendingSelect = -1;
            selectSection(pending, false);
        }
        styleChips();
    }

    @Override
    public void removeSection(BrowseSection section) {
        int at = section != null ? indexOf(section.getId()) : -1;

        if (at < 0) {
            return;
        }

        mSections.remove(at);
        mChipBar.removeView(mChips.remove(at));
        styleChips();
    }

    @Override
    public void removeAllSections() {
        mSections.clear();
        mChips.clear();
        mChipBar.removeAllViews();
        mShownSectionId = -1;
    }

    /** Unlike the TV's header list, nothing tells the presenter that a chip was picked: say so here */
    @Override
    public void selectSection(int index, boolean focusOnContent) {
        if (index < 0) {
            return;
        }

        if (index >= mSections.size()) {
            mPendingSelect = index; // applied as the chips arrive
            return;
        }

        int id = mSections.get(index).getId();

        if (id != mShownSectionId) {
            mGrid.clear();
        }

        mShownSectionId = id;
        mPresenter.onSectionFocused(id);
        styleChips();
    }

    @Override
    public void updateSection(VideoGroup group) {
        if (group == null) {
            return;
        }

        if (isGated()) {
            showGate();
            return;
        }

        if (group.getSection() != null) {
            mShownSectionId = group.getSection().getId();
        }
        mGrid.update(group);
        styleChips();
    }

    /** Settings sections (the TV's settings tab) come with the phone's own settings screen */
    @Override
    public void updateSection(SettingsGroup group) {
    }

    @Override
    public void clearSection(BrowseSection section) {
        mGrid.clear();
    }

    @Override
    public void selectSectionItem(int index) {
        mGrid.scrollToPosition(index);
    }

    @Override
    public void selectSectionItem(Video item) {
        mGrid.scrollTo(item);
    }

    @Override
    public void showError(ErrorFragmentData data) {
        if (isGated()) { // the presenter's own "sign in to see this" would invite a kid to try
            showGate();
            return;
        }

        // The presenter says what to do ("Sign in"): a button, for someone who may
        if (data != null) {
            mGrid.showMessage(data.getMessage(), data.getActionText(), data::onAction);
        } else {
            mGrid.showMessage(null);
        }
    }

    @Override
    public void showProgressBar(boolean show) {
        mGrid.showProgress(show);
    }

    @Override
    public boolean isProgressBarShowing() {
        return mGrid.isProgressShowing();
    }

    /** There is no focus to move on a touch screen */
    @Override
    public void focusOnContent() {
    }

    @Override
    public boolean isEmpty() {
        return mGrid.isEmpty();
    }

    /** The TV shows the account avatar in the corner; the phone's top bar comes later */
    @Override
    public void updateBadge() {
    }

    private static boolean isHidden(BrowseSection section) {
        for (int hidden : HIDDEN_SECTIONS) {
            if (section.getId() == hidden) {
                return true;
            }
        }

        return false;
    }

    private int indexOf(int sectionId) {
        for (int i = 0; i < mSections.size(); i++) {
            if (mSections.get(i).getId() == sectionId) {
                return i;
            }
        }

        return -1;
    }

    /** The chip of the section the presenter is showing: it may pick one before the chips exist, or on its own */
    private void styleChips() {
        int shown = indexOf(mShownSectionId);

        for (int i = 0; i < mChips.size(); i++) {
            boolean selected = i == shown;
            HearthLook.chip(mChips.get(i), selected);
        }

        // The start section can be far along the row (Music is the sixth): bring its chip into view
        if (shown >= 0 && shown < mChips.size()) {
            TextView chip = mChips.get(shown);
            mChipScroll.post(() -> mChipScroll.smoothScrollTo(Math.max(0, chip.getLeft() - dp(16)), 0));
        }
    }
}
