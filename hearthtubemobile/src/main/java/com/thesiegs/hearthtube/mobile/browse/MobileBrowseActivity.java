package com.thesiegs.hearthtube.mobile.browse;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
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
import com.liskovsoft.smartyoutubetv2.common.prefs.HearthTabsData;
import com.liskovsoft.smartyoutubetv2.common.utils.ParentGate;
import com.thesiegs.hearthtube.mobile.R;
import com.thesiegs.hearthtube.mobile.parent.MobileParentMenu;
import com.thesiegs.hearthtube.mobile.search.MobileSearchActivity;
import com.thesiegs.hearthtube.mobile.ui.HearthActivity;
import com.thesiegs.hearthtube.mobile.ui.HearthLook;
import com.thesiegs.hearthtube.mobile.ui.KidGate;
import com.thesiegs.hearthtube.mobile.ui.KidMenu;
import com.thesiegs.hearthtube.mobile.ui.SubPillStrip;
import com.thesiegs.hearthtube.mobile.ui.VideoGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * HearthTube Mobile's home screen. Four destinations on a bottom bar (a side rail on a tablet): Home, Subscriptions,
 * Library and Search. Home holds the TV's sections as chips across the top (the person's tabs first), Subscriptions
 * the channels' circles, Library its lists (what was watched, Watch later, the queue and the playlists), and Search
 * opens the search screen. Below the chips, the current section's videos. The shared {@link BrowsePresenter} drives it
 * exactly as it drives the TV's browse screen; {@link NavRules} decides what is on the bar.
 * <p>
 * A kid who isn't signed in sees no videos at all, not only no playback, and no bar: what YouTube lists signed out
 * isn't supervised.
 */
public class MobileBrowseActivity extends HearthActivity implements BrowseView {
    /** The TV's own tabs: notifications, blocked channels and its settings tab (a parent has the Settings button) */
    private static final int[] HIDDEN_SECTIONS = {
            MediaGroup.TYPE_NOTIFICATIONS, MediaGroup.TYPE_BLOCKED_CHANNELS, MediaGroup.TYPE_SETTINGS
    };
    private static final String STATE_NAV = "nav";
    /** From this width the destinations are a rail on the start edge, not a bar along the bottom */
    private static final int RAIL_MIN_WIDTH_DP = 600;
    private static final int BAR_MARGIN_DP = 12;

    private final List<BrowseSection> mSections = new ArrayList<>();
    private final List<TextView> mChips = new ArrayList<>();
    /** The number the presenter gave each section: it counts the sections this screen leaves out too, and picks by it */
    private final List<Integer> mPresenterIndexes = new ArrayList<>();
    /** The chips in the row at the top now (those of the destination on show) */
    private List<TextView> mShownChips = new ArrayList<>();

    private BrowsePresenter mPresenter;
    private FrameLayout mRoot;
    private HorizontalScrollView mChipScroll;
    private TextView mParentButton;
    /** The signed-in account's picture: opens the same menu as the gear, which shows until there is a picture */
    private ImageView mAvatar;
    private LinearLayout mChipBar;
    /** The section chips and, under them, the sub-pills; it floats over the list and slides away on a phone as it scrolls */
    private LinearLayout mHeader;
    private NavBar mNav;
    private ScrollHide mScrollHide = new ScrollHide(false, 0);
    private VideoGrid mGrid;
    private boolean mGated;
    private NavState mState = new NavState();
    /** The section the saved state had on show: picked again once the presenter has listed the sections */
    private int mRestoreSectionId = -1;
    /** A selection asked for before the chips were there (the presenter picks the start section first) */
    private int mPendingSelect = -1;
    private int mShownSectionId = -1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mState = NavState.decode(savedInstanceState.getString(STATE_NAV));
            mRestoreSectionId = mState.section();
        }

        setContentView(createContent());
        applyLayout();

        mPresenter = BrowsePresenter.instance(this);
        mPresenter.setView(this);
        mPresenter.onViewInitialized();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_NAV, mState.encode());
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
        // (a gear, not a word, for anyone else)
        if (ParentGate.isKidsProfile(this)) {
            mParentButton.setText(R.string.parent_button);
            mParentButton.setCompoundDrawablesRelative(null, null, null, null);
        } else {
            mParentButton.setText("");
            mParentButton.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_settings_gear, 0, 0, 0);
        }
        mParentButton.setContentDescription(getString(ParentGate.isKidsProfile(this) ? R.string.parent_button : R.string.settings_button));
        updateAvatar();

        // A parent signed in, or turned kids mode off, in the meantime: show what the presenter has
        if (mGated && !isGated()) {
            mGated = false;
            mGrid.showMessage(null);
            mPresenter.refresh(false);
        }

        updateNav();
        showBars(); // back from a video or a page: the bars are there
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
        applyLayout();
    }

    /** Back from Library or Subscriptions goes to Home; Back at Home leaves the app */
    @Override
    public void onBackPressed() {
        if (mGrid.collapse()) { // a row opened with "See more": back to the shelves
            return;
        }

        if (!isGated() && NavRules.back(currentDestination()) == NavRules.Back.GO_HOME) {
            openDestination(Destination.HOME);
            return;
        }

        super.onBackPressed();
    }

    private View createContent() {
        mRoot = new FrameLayout(this);

        mChipScroll = new HorizontalScrollView(this);
        mChipScroll.setHorizontalScrollBarEnabled(false);
        mChipBar = new LinearLayout(this);
        mChipBar.setOrientation(LinearLayout.HORIZONTAL);
        mChipBar.setGravity(Gravity.CENTER_VERTICAL);
        mChipBar.setPadding(dp(8), dp(4), dp(8), dp(4));
        mChipScroll.addView(mChipBar);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        mParentButton = barButton(getString(R.string.parent_button), v -> MobileParentMenu.show(this));
        bar.addView(mParentButton, barButtonParams());
        mAvatar = new ImageView(this);
        mAvatar.setVisibility(View.GONE);
        mAvatar.setContentDescription(getString(R.string.settings_button));
        mAvatar.setOnClickListener(v -> MobileParentMenu.show(this));
        LinearLayout.LayoutParams avatarParams = barButtonParams();
        avatarParams.width = dp(44);
        avatarParams.height = dp(44);
        bar.addView(mAvatar, avatarParams);
        bar.addView(mChipScroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        mGrid = new VideoGrid(this, new VideoGrid.Listener() {
            @Override
            public void onClick(Video video) {
                mPresenter.onVideoItemClicked(video);
            }

            @Override
            public void onLongClick(Video video) {
                KidMenu.longPress(MobileBrowseActivity.this, () -> mPresenter.onVideoItemLongClicked(video));
            }

            @Override
            public void onScrollEnd(Video last) {
                mPresenter.onScrollEnd(last);
            }
        });

        mGrid.setShelves(true);

        // A section with several rows (Ambiance: Fireplace, Rain...) has a strip of sub-pills under the section chips; in
        // Subscriptions a long press on a channel's circle opens its menu (a kid's own: open, unsubscribe)
        SubPillStrip strip = new SubPillStrip(this, mGrid, pill -> {
            if (!(pill.tag instanceof HearthSections.TitledGroup)) {
                return false;
            }

            KidMenu.longPress(this, () -> HearthChannelMenu.show(this, (HearthSections.TitledGroup) pill.tag,
                    channel -> mGrid.removeChannel(pill.tag, video -> HearthSections.isSameChannel(
                            channel.channelId, channel.title, video.channelId, video.getAuthor()))));
            return true;
        });

        mHeader = new LinearLayout(this);
        mHeader.setOrientation(LinearLayout.VERTICAL);
        mHeader.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mHeader.addView(strip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mNav = new NavBar(this, this::openDestination);

        mRoot.addView(mGrid, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mRoot.addView(mHeader, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mRoot.addView(mNav, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // The list starts below the header and ends above the bar (or starts after the rail), whatever their size
        mHeader.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> mGrid.setTopInset(bottom));
        mNav.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            // The chips start after the rail, not over it
            FrameLayout.LayoutParams header = (FrameLayout.LayoutParams) mHeader.getLayoutParams();
            int headerStart = !isCompact() && mNav.getVisibility() == View.VISIBLE ? right : 0;

            if (header.getMarginStart() != headerStart) {
                header.setMarginStart(headerStart);
                mHeader.setLayoutParams(header);
            }

            if (isCompact()) {
                mGrid.setStartInset(0);
                mGrid.setBottomInset(mNav.getVisibility() == View.VISIBLE ? mRoot.getHeight() - top : 0);
            } else {
                mGrid.setBottomInset(0);
                mGrid.setStartInset(mNav.getVisibility() == View.VISIBLE ? right + dp(BAR_MARGIN_DP) / 2 : 0);
            }
        });
        mGrid.setScrollWatcher(this::onGridScrolled);
        return mRoot;
    }

    private boolean isCompact() {
        return getResources().getConfiguration().screenWidthDp < RAIL_MIN_WIDTH_DP;
    }

    /** A bar on a phone, a rail on a tablet (or a phone turned sideways), and the room each needs around it */
    private void applyLayout() {
        boolean compact = isCompact();
        int margin = dp(BAR_MARGIN_DP);

        mNav.setRail(!compact);
        FrameLayout.LayoutParams nav = (FrameLayout.LayoutParams) mNav.getLayoutParams();

        if (compact) {
            nav.width = ViewGroup.LayoutParams.MATCH_PARENT;
            nav.gravity = Gravity.BOTTOM;
            nav.setMargins(margin, 0, margin, margin);
        } else {
            nav.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            nav.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
            nav.setMargins(margin, 0, 0, 0);
        }
        mNav.setLayoutParams(nav);


        // The bars hide while a phone scrolls; a rail never does (nothing else would be in its way)
        mScrollHide = new ScrollHide(compact, dp(4));
        mHeader.setTranslationY(0);
        mNav.setTranslationY(0);
        updateNav();
    }

    // Hide on scroll: on a phone the chips and the bar slide away as the list goes down and come back as it goes up, so
    // the videos get the screen; a tablet has room to keep them

    private void onGridScrolled(int dy, boolean atTop) {
        if (!mScrollHide.onScrolled(dy, atTop)) {
            return;
        }

        if (mScrollHide.isVisible()) {
            slideBars(true);
        } else {
            slideBars(false);
        }
    }

    private void showBars() {
        if (mScrollHide.show()) {
            slideBars(true);
        }
    }

    private void slideBars(boolean visible) {
        mHeader.animate().translationY(visible ? 0 : -mHeader.getBottom()).setDuration(150).start();
        mNav.animate().translationY(visible ? 0 : mRoot.getHeight() - mNav.getTop()).setDuration(150).start();
    }

    /** A bar item was tapped: Search opens its screen; the destination on show scrolls to its top; another one opens */
    private void openDestination(Destination destination) {
        if (destination == Destination.SEARCH) {
            MobileSearchActivity.start(this);
            return;
        }

        if (destination == currentDestination()) {
            // Home has no chip of its own (the bar has Home): Home again, from Music or News, is the Home section
            int homeAt = indexOf(MediaGroup.TYPE_HOME);

            if (destination == Destination.HOME && homeAt >= 0 && mShownSectionId != MediaGroup.TYPE_HOME) {
                showBars();
                selectAt(homeAt);
                return;
            }

            mGrid.scrollToTop();
            showBars();
            return;
        }

        int sectionId = NavRules.sectionFor(destination, isKid(), sectionIds(), tabIds(), mState.remembered(destination));

        if (sectionId >= 0) {
            showBars();
            selectAt(indexOf(sectionId));
        }
    }

    private Destination currentDestination() {
        int id = mShownSectionId >= 0 ? mShownSectionId : mState.section();
        return id >= 0 ? NavRules.destinationOf(id) : Destination.HOME;
    }

    private boolean isKid() {
        return ParentGate.isKidsProfile(this);
    }

    private List<Integer> sectionIds() {
        List<Integer> ids = new ArrayList<>();

        for (BrowseSection section : mSections) {
            ids.add(section.getId());
        }

        return ids;
    }

    private List<Integer> tabIds() {
        return HearthTabsData.instance(this).getTabs();
    }

    /** Anyone but a kid sees their YouTube account's picture, round, where the gear was; the gear stays without one */
    private void updateAvatar() {
        com.liskovsoft.mediaserviceinterfaces.oauth.Account account = ParentGate.isKidsProfile(this) ? null
                : com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager.instance().getSelectedAccount();
        String url = account != null ? account.getAvatarImageUrl() : null;

        mAvatar.setVisibility(url != null ? View.VISIBLE : View.GONE);
        mParentButton.setVisibility(url != null ? View.GONE : View.VISIBLE);

        if (url != null) {
            com.bumptech.glide.Glide.with(this).load(url)
                    .apply(com.bumptech.glide.request.RequestOptions.circleCropTransform()).into(mAvatar);
        }
    }

    private TextView barButton(String text, View.OnClickListener onClick) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextSize(14);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(48));
        button.setMinWidth(dp(48));
        button.setPadding(dp(14), dp(8), dp(14), dp(8));
        HearthLook.accentButton(button);
        button.setOnClickListener(onClick);
        return button;
    }

    private LinearLayout.LayoutParams barButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(8), 0, dp(2), 0);
        return params;
    }

    private int dp(int dp) {
        return HearthLook.dp(this, dp);
    }

    /** What YouTube lists signed out isn't supervised: a kid who isn't signed in gets no videos, not just no playback */
    private boolean isGated() {
        return KidGate.isClosed(this);
    }

    private void showGate() {
        mGated = true;
        mGrid.clear();
        mGrid.showProgress(false);
        mGrid.showMessage(getString(R.string.gated_title));
        updateNav();
    }

    // BrowseView

    @Override
    public void addSection(int index, BrowseSection section) {
        if (section == null || isHidden(section) || indexOf(section.getId()) >= 0) {
            return;
        }

        int at = index < 0 || index > mSections.size() ? mSections.size() : index;
        mSections.add(at, section);
        mPresenterIndexes.add(at, index);

        TextView chip = new TextView(this);
        chip.setText(section.getTitle());
        chip.setTextSize(14);
        chip.setGravity(Gravity.CENTER);
        chip.setMinHeight(dp(48)); // a finger's size, whatever the font size
        chip.setPadding(dp(14), dp(8), dp(14), dp(8));
        HearthLook.chip(chip, false);
        chip.setOnClickListener(v -> selectAt(mSections.indexOf(section)));
        mChips.add(at, chip);

        if (mPendingSelect >= 0 && mPresenterIndexes.contains(mPendingSelect)) {
            int pending = mPendingSelect;
            mPendingSelect = -1;
            selectSection(pending, false);
        }
        updateNav();
    }

    @Override
    public void removeSection(BrowseSection section) {
        int at = section != null ? indexOf(section.getId()) : -1;

        if (at < 0) {
            return;
        }

        mSections.remove(at);
        mPresenterIndexes.remove(at);
        mChips.remove(at);
        updateNav();
    }

    @Override
    public void removeAllSections() {
        mSections.clear();
        mPresenterIndexes.clear();
        mChips.clear();
        mChipBar.removeAllViews();
        mShownChips = new ArrayList<>();
        mShownSectionId = -1;
        updateNav();
    }

    /** Unlike the TV's header list, nothing tells the presenter that a chip was picked: say so here */
    @Override
    public void selectSection(int index, boolean focusOnContent) {
        // The section the saved state had on show wins over the presenter's start section, once it is there
        if (mRestoreSectionId >= 0) {
            int restored = indexOf(mRestoreSectionId);

            if (restored >= 0) {
                mRestoreSectionId = -1;
                selectAt(restored);
                return;
            }
        }

        if (index < 0) {
            return;
        }

        int at = mPresenterIndexes.indexOf(index);

        if (at < 0) {
            mPendingSelect = index; // applied as the chips arrive
            return;
        }

        selectAt(at);
    }

    /** Shows the section at this position of the sections this screen has */
    private void selectAt(int index) {
        if (index < 0 || index >= mSections.size()) {
            return;
        }

        int id = mSections.get(index).getId();

        if (id != mShownSectionId) {
            mGrid.clear();
        }

        mShownSectionId = id;
        mState.show(id);
        mPresenter.onSectionFocused(id);
        updateNav();
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
            mState.show(mShownSectionId);
        }
        mGrid.update(group);
        updateNav();
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

    /**
     * The bar (which destinations, which one is shown) and the chips of the shown destination. A kid with nobody signed
     * in has neither: only the message and the Parent button. mSections and mChips stay in the presenter's order, which
     * is what it counts by; {@link NavRules} orders what is shown.
     */
    private void updateNav() {
        if (mNav == null) {
            return;
        }

        boolean gated = isGated();
        boolean kid = isKid();
        List<Integer> ids = sectionIds();
        List<Destination> destinations = NavRules.bar(gated, kid, ids);
        Destination current = currentDestination();

        mNav.setVisibility(destinations.isEmpty() ? View.GONE : View.VISIBLE);
        mNav.setDestinations(destinations, current);

        if (destinations.isEmpty()) { // a gone bar has no layout change to tell the list that it left
            mGrid.setBottomInset(0);
            mGrid.setStartInset(0);
            FrameLayout.LayoutParams header = (FrameLayout.LayoutParams) mHeader.getLayoutParams();
            header.setMarginStart(0);
            mHeader.setLayoutParams(header);
        }

        List<TextView> shown = new ArrayList<>();

        if (!gated) {
            List<Integer> chips = NavRules.chips(current, kid, ids, tabIds());

            if (NavRules.showChips(chips.size())) {
                for (int at : chips) {
                    // The bar's Home is Home's own chip
                    if (!(current == Destination.HOME && ids.get(at) == MediaGroup.TYPE_HOME)) {
                        shown.add(mChips.get(at));
                    }
                }
            }
        }

        // Sections arrive one by one while loading: rebuild the row only when it is a different one
        if (!shown.equals(mShownChips)) {
            mShownChips = shown;
            mChipBar.removeAllViews();

            for (TextView chip : shown) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.setMargins(dp(4), 0, dp(4), 0);
                mChipBar.addView(chip, params);
            }
        }

        mChipScroll.setVisibility(shown.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        styleChips();
    }

    /** The chip of the section the presenter is showing: it may pick one before the chips exist, or on its own */
    private void styleChips() {
        int shown = indexOf(mShownSectionId);

        for (int i = 0; i < mChips.size(); i++) {
            HearthLook.chip(mChips.get(i), i == shown);
        }

        // The start section can be far along the row (Music is the sixth): bring its chip into view
        if (shown >= 0 && shown < mChips.size() && mChips.get(shown).getParent() == mChipBar) {
            TextView chip = mChips.get(shown);
            mChipScroll.post(() -> mChipScroll.smoothScrollTo(Math.max(0, chip.getLeft() - dp(16)), 0));
        }
    }
}
