package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.browse;

import com.liskovsoft.smartyoutubetv2.tv.ui.browse.BrowseActivity;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.FocusFinder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.SearchOrbView;
import androidx.leanback.widget.SearchOrbView.Colors;
import androidx.leanback.widget.TitleView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.locale.LocaleUtility;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ProfilePickerPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.AccountSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.LanguageSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager.AccountChangeListener;
import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataChangeBase.OnDataChange;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tooltips.TooltipCompatHandler;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.search.LongClickSearchOrbView;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.time.DateTimeView;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

import java.util.Locale;

import static androidx.leanback.widget.TitleViewAdapter.BRANDING_VIEW_VISIBLE;
import static androidx.leanback.widget.TitleViewAdapter.FULL_VIEW_VISIBLE;
import static androidx.leanback.widget.TitleViewAdapter.SEARCH_VIEW_VISIBLE;

/**
 * View that supports dpad navigation between children<br/>
 * NOTE: You should set android:nextFocusLeft and android:nextFocusRight<br/>
 * https://stackoverflow.com/questions/38169378/use-multiple-orb-buttons-or-other-buttons-in-the-leanbacks-title-view<br/>
 * https://stackoverflow.com/questions/40802470/add-button-to-browsefragment
 */
public class NavigateTitleView extends TitleView implements OnDataChange, AccountChangeListener {
    private LongClickSearchOrbView mAccountView;
    private TextView mAccountName;
    private SearchOrbView mLanguageView;
    private SearchOrbView mSettingsView;
    private final Runnable mOnSectionChange = this::updateSettingsButton;
    private SearchOrbView mExitPip;
    private TextView mPipTitle;
    private int mSearchVisibility = View.INVISIBLE;
    private int mBrandingVisibility = View.INVISIBLE;
    private DateTimeView mGlobalClock;
    private View mGlobalClockPill;
    private DateTimeView mGlobalDate;
    private SearchOrbView mSearchOrbView;
    private boolean mInitDone;
    private int mFlags = FULL_VIEW_VISIBLE;
    private int mIconWidth;
    private int mIconHeight;
    private boolean mIsSearchOrbEnabled;
    private boolean mIsAccountViewEnabled;
    private boolean mIsLanguageViewEnabled;
    private boolean mIsGlobalClockEnabled;

    public NavigateTitleView(Context context) {
        super(context);
    }

    public NavigateTitleView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NavigateTitleView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        // findViewById is null in constructor. Init mAccountView later.
    }

    @Override
    public View focusSearch(View focused, int direction) {
        View nextFoundFocusableViewInLayout = null;

        // Only concerned about focusing left and right at the moment
        if (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) {

            // Try to find the next focusable item in this layout for the supplied direction
            int nextFoundFocusableViewInLayoutId = -1;
            switch(direction) {
                case View.FOCUS_LEFT :
                    nextFoundFocusableViewInLayoutId = focused.getNextFocusLeftId();
                    break;
                case View.FOCUS_RIGHT :
                    nextFoundFocusableViewInLayoutId = focused.getNextFocusRightId();
                    break;
            }

            // View id for next focus direction found....get the View
            if (nextFoundFocusableViewInLayoutId != -1) {
                nextFoundFocusableViewInLayout = findViewById(nextFoundFocusableViewInLayoutId);
            }
        }

        //  Return the found View in the layout if it's focusable
        if (nextFoundFocusableViewInLayout != null && nextFoundFocusableViewInLayout != focused && nextFoundFocusableViewInLayout.isFocusable()) {
            if (nextFoundFocusableViewInLayout.getVisibility() == View.VISIBLE) {
                return nextFoundFocusableViewInLayout;
            } else {
                return focusSearch(nextFoundFocusableViewInLayout, direction);
            }
        } else if (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) {
            // HearthTube: left and right stay in the top bar (tabs, search, bell). Nothing further left: null, and
            // BrowseActivity slides the side menu in. Nothing further right: stay.
            View next = FocusFinder.getInstance().findNextFocus(this, focused, direction);
            return next != null ? next : direction == View.FOCUS_RIGHT ? focused : null;
        } else {
            // No focusable view found in layout...propagate to super (should invoke the BrowseFrameLayout.OnFocusSearchListener
            return super.focusSearch(focused, direction);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        // Only the browse screen switches sections, other screens keep the plain gear
        if (getContext() instanceof BrowseActivity) {
            BrowsePresenter.instance(getContext()).addOnSectionChange(mOnSectionChange);
            updateSettingsButton();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (getContext() instanceof BrowseActivity) {
            BrowsePresenter.instance(getContext()).removeOnSectionChange(mOnSectionChange);
        }
    }

    /**
     * The bell, where the gear was: notifications, like the Hearth launcher's. Settings live in the side menu
     * (Left at the left edge).
     */
    private void updateSettingsButton() {
        if (mSettingsView == null) {
            return;
        }

        mSettingsView.setOrbIcon(ContextCompat.getDrawable(getContext(), R.drawable.ic_orb_bell));
        TooltipCompatHandler.setTooltipText(mSettingsView, getContext().getString(R.string.notifications_bell));
    }

    /** 0: everything shows. 1: no name by the avatar. 2: no date in the clock either. */
    private int mCompactLevel;
    private int mNameWidth;
    private int mDateWidth;

    /**
     * HearthTube: the left side (avatar, tabs, search) and the right side (bell, clock) share one bar. When they'd
     * overlap (a long name, the Queued tab), the name goes first, then the date; they come back when there's room.
     */
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (mAccountView == null || mSettingsView == null || mAccountName == null || mGlobalDate == null) {
            return;
        }

        View leftGroup = (View) mAccountView.getParent();
        View rightGroup = (View) mGlobalClockPill.getParent();

        // The clock pill sits 32dp from the screen's right edge, like Hearth's (the title bar's own padding is wider)
        int[] onScreen = new int[2];
        getLocationOnScreen(onScreen);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int pillRight = onScreen[0] + rightGroup.getLeft() + mGlobalClockPill.getRight();
        float shift = screenWidth - getResources().getDimensionPixelSize(R.dimen.hearth_clock_margin_end) - pillRight;
        if (rightGroup.getTranslationX() != shift) {
            rightGroup.setTranslationX(shift);
        }

        if (leftGroup == null || rightGroup == null || rightGroup.getWidth() == 0) {
            return;
        }

        int gap = Math.round(16 * getResources().getDisplayMetrics().density);
        int room = rightGroup.getLeft() - gap - leftGroup.getLeft();
        // Up to the last child that shows (the background video button is invisible, not gone, and takes space)
        int needed = 0;
        if (leftGroup instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) leftGroup;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child.getVisibility() == View.VISIBLE) {
                    needed = Math.max(needed, child.getRight());
                }
            }
        }
        // Hidden views measure 0: remember their widths from when they showed
        if (mAccountName.getVisibility() == View.VISIBLE) {
            mNameWidth = mAccountName.getMeasuredWidth() + ((MarginLayoutParams) mAccountName.getLayoutParams()).getMarginStart();
        }
        if (mGlobalDate.getVisibility() == View.VISIBLE) {
            mDateWidth = mGlobalDate.getMeasuredWidth() + ((MarginLayoutParams) mGlobalDate.getLayoutParams()).getMarginEnd();
        }

        // Width the left side would need with the name showing, and the room it would have with the date showing
        int fullNeeded = needed + (mAccountName.getVisibility() != View.VISIBLE ? mNameWidth : 0);
        int roomWithDate = room - (mGlobalDate.getVisibility() != View.VISIBLE ? mDateWidth : 0);

        int level = fullNeeded <= roomWithDate ? 0 : fullNeeded - mNameWidth <= roomWithDate ? 1 : 2;

        if (level != mCompactLevel) {
            mCompactLevel = level;
            post(this::applyCompactLevel);
        }
    }

    private void applyCompactLevel() {
        if (mIsAccountViewEnabled) {
            mAccountName.setVisibility(mCompactLevel >= 1 ? View.GONE : mSearchVisibility);
        }

        if (mIsGlobalClockEnabled) {
            mGlobalDate.setVisibility(mCompactLevel >= 2 ? View.GONE : mBrandingVisibility);
        }
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        // HearthTube: up from the videos lands on the open tab
        HearthTabBar tabs = findViewById(R.id.hearth_tabs);
        View tab = tabs != null && tabs.getVisibility() == View.VISIBLE ? tabs.getSelectedTab() : null;
        if (tab != null && tab.requestFocus()) {
            return true;
        }

        // Gives focus to the SearchOrb first....if not...default to normal descendant focus search
        return getSearchAffordanceView().requestFocus() || super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
    }

    @Override
    public void updateComponentsVisibility(int flags) {
        // Fix for: Fatal Exception: java.lang.IllegalStateException
        // Fragment has not been attached yet.
        // Inside: super.updateComponentsVisibility(flags);
        if (getWindowToken() == null) {
            return;
        }

        super.updateComponentsVisibility(flags);

        init();

        mFlags = flags;

        mSearchVisibility = (flags & SEARCH_VIEW_VISIBLE) == SEARCH_VIEW_VISIBLE
                ? View.VISIBLE : View.INVISIBLE;

        mBrandingVisibility = (flags & BRANDING_VIEW_VISIBLE) == BRANDING_VIEW_VISIBLE
                ? View.VISIBLE : View.INVISIBLE;

        if (mIsSearchOrbEnabled) {
            mSearchOrbView.setVisibility(View.GONE);
        }

        if (mIsAccountViewEnabled) {
            mAccountView.setVisibility(mSearchVisibility);
            mAccountName.setVisibility(mCompactLevel >= 1 ? View.GONE : mSearchVisibility);
        }

        if (mIsLanguageViewEnabled) {
            mLanguageView.setVisibility(mSearchVisibility);
        }

        if (mExitPip != null && (PlaybackPresenter.instance(getContext()).isRunningInBackground() || mSearchVisibility != View.VISIBLE)) {
            mExitPip.setVisibility(mSearchVisibility);
            mPipTitle.setVisibility(mSearchVisibility);
        }

        if (mIsGlobalClockEnabled) {
            mGlobalClock.setVisibility(mBrandingVisibility);
        }

        if (mIsGlobalClockEnabled) {
            mGlobalDate.setVisibility(mCompactLevel >= 2 ? View.GONE : mBrandingVisibility);
            mGlobalClockPill.setVisibility(mBrandingVisibility);
        }
    }

    private void init() {
        if (mInitDone) {
            return;
        }

        MediaServiceManager.instance().addAccountListener(this);

        setupButtons();

        MainUIData mainUIData = MainUIData.instance(getContext());
        mainUIData.setOnChange(this);

        mInitDone = true;
    }

    private void setupButtons() {
        MainUIData mainUIData = MainUIData.instance(getContext());

        mSearchOrbView = findViewById(R.id.title_orb);

        mAccountView = findViewById(R.id.account_orb);
        mAccountName = findViewById(R.id.account_name);
        mAccountView.setOnOrbClickedListener(v -> ProfilePickerPresenter.instance(getContext()).start());
        mAccountView.setOnOrbLongClickedListener(v -> {
            AccountSettingsPresenter.instance(getContext()).show();
            return true;
        });
        TooltipCompatHandler.setTooltipText(mAccountView, getContext().getString(R.string.settings_accounts));

        mSettingsView = findViewById(R.id.settings_orb);
        mSettingsView.setVisibility(View.VISIBLE); // hidden in the layout: plain leanback title bars (error pages) reuse it
        mSettingsView.setOnOrbClickedListener(v -> BrowsePresenter.instance(getContext()).selectSection(MediaGroup.TYPE_NOTIFICATIONS));
        updateSettingsButton();

        mLanguageView = findViewById(R.id.language_orb);
        mLanguageView.setOnOrbClickedListener(v -> LanguageSettingsPresenter.instance(getContext()).show());
        TooltipCompatHandler.setTooltipText(mLanguageView, getContext().getString(R.string.settings_language_country));

        mExitPip = findViewById(R.id.exit_pip);
        mPipTitle = findViewById(R.id.pip_title);
        mExitPip.setOnOrbClickedListener(v -> ViewManager.instance(getContext()).startView(PlaybackView.class));
        ViewUtil.enableMarquee(mPipTitle);
        ViewUtil.setTextScrollSpeed(mPipTitle, mainUIData.getCardTextScrollSpeed());
        TooltipCompatHandler.setTooltipText(mExitPip, getContext().getString(R.string.return_to_background_video));

        mGlobalClock = findViewById(R.id.global_time);
        mGlobalClock.showDate(false);

        mGlobalDate = findViewById(R.id.global_date);
        mGlobalClockPill = findViewById(R.id.global_clock_pill);
        mGlobalDate.showTime(false);
        mGlobalDate.showDate(true);

        // Hearth's text shadow: black 54%, offset (0, 2), blur 4
        float density = getResources().getDisplayMetrics().density;
        mGlobalDate.setShadowLayer(4 * density, 0, 2 * density, 0x8A000000);
        mGlobalClock.setShadowLayer(4 * density, 0, 2 * density, 0x8A000000);

        updateButtonsVisibility();
    }

    private void updateButtonsVisibility() {
        MainUIData mainUIData = MainUIData.instance(getContext());

        mIsSearchOrbEnabled = !mainUIData.isTopButtonEnabled(MainUIData.TOP_BUTTON_SEARCH);
        mIsAccountViewEnabled = mainUIData.isTopButtonEnabled(MainUIData.TOP_BUTTON_BROWSE_ACCOUNTS);
        mIsLanguageViewEnabled = mainUIData.isTopButtonEnabled(MainUIData.TOP_BUTTON_CHANGE_LANGUAGE);
        mIsGlobalClockEnabled = GeneralData.instance(getContext()).isGlobalClockEnabled();

        mSearchOrbView.setVisibility(mIsSearchOrbEnabled ? View.VISIBLE : View.GONE);
        mAccountView.setVisibility(mIsAccountViewEnabled ? View.VISIBLE : View.GONE);
        mAccountName.setVisibility(mIsAccountViewEnabled ? View.VISIBLE : View.GONE);
        mLanguageView.setVisibility(mIsLanguageViewEnabled ? View.VISIBLE : View.GONE);
        mGlobalClock.setVisibility(mIsGlobalClockEnabled ? View.VISIBLE : View.GONE);
        mGlobalDate.setVisibility(mIsGlobalClockEnabled ? View.VISIBLE : View.GONE);
        mGlobalClockPill.setVisibility(mIsGlobalClockEnabled ? View.VISIBLE : View.GONE);

        Utils.postDelayed(this::updateAccountIcon, 1_000); // give a time to engine to fetch an updated icon url
        //updateAccountIcon();
        updateLanguageIcon();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);

        if (visibility == View.VISIBLE) { // scroll grid up, scroll grid down
            applyPipParameters();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);

        if (hasWindowFocus) { // pip window closed, dialog closed
            applyPipParameters();
            // The Google TV profile may have changed while we were away
            if (mAccountName != null && mIsAccountViewEnabled) {
                mAccountName.setText(getFirstName(MediaServiceManager.instance().getSelectedAccount()));
            }
        }
    }

    @Override
    public void onAccountChanged(Account account) {
        updateAccountIcon();
    }

    private void applyPipParameters() {
        if (mExitPip != null) {
            int newVisibility = PlaybackPresenter.instance(getContext()).isRunningInBackground() ? mSearchVisibility : View.INVISIBLE;
            mExitPip.setVisibility(newVisibility);
            mPipTitle.setVisibility(newVisibility);

            if (newVisibility == View.VISIBLE) {
                Video video = PlaybackPresenter.instance(getContext()).getVideo();
                mPipTitle.setText(video != null ? String.format("%s - %s", video.getTitle(), video.getAuthor()) : "");
            }
        }
    }

    private void updateAccountIcon() {
        if (!mIsAccountViewEnabled) {
            return;
        }

        Account current = MediaServiceManager.instance().getSelectedAccount();
        mAccountName.setText(getFirstName(current));

        if (current != null && current.getAvatarImageUrl() != null) {
            loadIcon(mAccountView, current.getAvatarImageUrl(), false);
            String accountName = current.getName() != null ? current.getName() : current.getEmail();
            //TooltipCompatHandler.setTooltipText(mAccountView, Utils.updateTooltip(getContext(), accountName));
            TooltipCompatHandler.setTooltipText(mAccountView, accountName);
        } else {
            Colors orbColors = mAccountView.getOrbColors();
            mAccountView.setOrbColors(new Colors(orbColors.color, orbColors.brightColor, ContextCompat.getColor(getContext(), R.color.orb_icon_color)));
            mAccountView.setOrbIcon(ContextCompat.getDrawable(getContext(), R.drawable.browse_title_account));
            TooltipCompatHandler.setTooltipText(mAccountView, getContext().getString(R.string.profile_guest));
        }
    }

    /**
     * Who's watching: the Google TV profile's name (through Hearth), like Hearth's top bar. Without Hearth, the
     * account's first name ("Alex Rivera" -> "Alex"), or the guest when nobody's signed in.
     */
    private String getFirstName(Account account) {
        com.liskovsoft.smartyoutubetv2.common.utils.HearthProfile profile =
                com.liskovsoft.smartyoutubetv2.common.utils.HearthProfile.query(getContext());

        if (profile != null && profile.name != null) {
            return profile.name;
        }

        String name = account != null ? (account.getName() != null ? account.getName() : account.getEmail()) : null;

        if (name == null || name.trim().isEmpty()) {
            return getContext().getString(R.string.profile_guest);
        }

        String[] words = name.trim().split("\\s+");
        return words[0];
    }

    private void updateLanguageIcon() {
        if (!mIsLanguageViewEnabled) {
            return;
        }

        // Use delay to fix icon initialization on app boot
        Utils.postDelayed(() -> {
            Locale locale = LocaleUtility.getCurrentLocale(getContext());
            loadIcon(mLanguageView, Utils.getCountryFlagUrl(locale.getCountry()), true); // flag server could be down
            TooltipCompatHandler.setTooltipText(mLanguageView, String.format("%s (%s)", locale.getDisplayCountry(), locale.getDisplayLanguage()));
        }, 100);
    }

    private void loadIcon(SearchOrbView view, String url, boolean useCache) {
        if (view == null) {
            return;
        }

        // The view with GONE visibility has zero width and height
        if (view.getWidth() <= 0 || view.getHeight() <= 0) {
            Utils.postDelayed(() -> loadIcon(view, url, useCache), 500);
            return;
        }

        Context context = view.getContext();

        if (context instanceof Activity && !Utils.checkActivity((Activity) context)) {
            return;
        }

        // Size of the view might increase after icon change (bug on some firmwares). So, it's better to cache initial values.
        if (mIconWidth == 0 || mIconHeight == 0) {
            mIconWidth = view.getWidth();
            mIconHeight = view.getHeight();
        }

        try {
            loadIcon(context, view, url, mIconWidth, mIconHeight, useCache);
        } catch (ExceptionInInitializerError e) {
            // Glide Kivi error
            e.printStackTrace();
        }
    }

    private static void loadIcon(Context context, SearchOrbView view, String url, int iconWidth, int iconHeight, boolean useCache) {
        Glide.with(context)
                .load(url)
                .apply(ViewUtil.glideOptions())
                .diskCacheStrategy(useCache ? DiskCacheStrategy.ALL : DiskCacheStrategy.NONE)
                .circleCrop() // resize image
                .into(new SimpleTarget<Drawable>(iconWidth, iconHeight) {
                    @Override
                    public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                        Colors orbColors = view.getOrbColors();
                        view.setOrbColors(new Colors(orbColors.color, orbColors.brightColor, Color.TRANSPARENT));
                        view.setOrbIcon(resource);
                    }
                });
    }

    @Override
    public void onDataChange() {
        try {
            updateButtonsVisibility();
            updateComponentsVisibility(mFlags);
        } catch (IllegalStateException e) {
            // Fragment BrowseFragment has not been attached yet.
            e.printStackTrace();
        }
    }
}
