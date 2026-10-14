package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;

import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.mediaserviceinterfaces.SignInService;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.AccountSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ProfilePickerView;
import com.liskovsoft.smartyoutubetv2.common.prefs.AccountsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.ProfileLinkData;
import com.liskovsoft.smartyoutubetv2.common.utils.GlideIconFetcher;
import com.liskovsoft.smartyoutubetv2.common.utils.HearthProfile;
import com.liskovsoft.smartyoutubetv2.common.utils.IntentExtractor;
import com.liskovsoft.smartyoutubetv2.common.utils.PinDialog;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayList;
import java.util.List;
import com.liskovsoft.smartyoutubetv2.common.utils.ParentGate;

public class ProfilePickerPresenter extends BasePresenter<ProfilePickerView> {
    /** {@link #followGoogleTvProfile}: not following (no Hearth, no accounts...), the usual picker logic applies. */
    public static final int FOLLOW_NONE = 0;
    /** Switched to the profile's account. The Google TV profile says who's watching, so no picker and no PIN. */
    public static final int FOLLOW_SWITCHED = 1;
    /** First time for this profile: the picker is open and remembers the pick for it. */
    public static final int FOLLOW_PICKER = 2;
    @SuppressLint("StaticFieldLeak")
    private static ProfilePickerPresenter sInstance;
    private final SignInService mSignInService;
    // Google TV profile the open picker links its pick to; null when picking just for now
    private String mLinkingProfile;

    private ProfilePickerPresenter(Context context) {
        super(context);
        ServiceManager service = YouTubeServiceManager.instance();
        mSignInService = service.getSignInService();
    }

    public static ProfilePickerPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new ProfilePickerPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public void unhold() {
        sInstance = null;
    }

    /**
     * Does the "Who's watching?" picker replace the native account dialog?
     * Same gating as AccountSelectionPresenter: the boot toggle and more than one account.
     */
    public static boolean isEnabled(Context context) {
        if (!AccountsData.instance(context).isSelectAccountOnBootEnabled()) {
            return false;
        }

        List<Account> accounts = YouTubeServiceManager.instance().getSignInService().getAccounts();

        return accounts != null && accounts.size() > 1;
    }

    /**
     * Show the picker for this launch? Deep links (NFC, cast-and-play, shared URLs, voice search,
     * or an intent that already names an account) go straight to the content with the current account.
     */
    public static boolean shouldShow(Context context, Intent intent) {
        return isEnabled(context) && !isDeepLink(intent);
    }

    /** A launch that goes straight to content (cast, shared link, voice search...), not a plain launcher tap */
    public static boolean isDeepLinkIntent(Intent intent) {
        return intent != null && isDeepLink(intent);
    }

    private static boolean isDeepLink(Intent intent) {
        if (IntentExtractor.extractAccountName(intent) != null) {
            // Caller already specified which account to use.
            return true;
        }

        // HearthTube: settings opened by Hearth go straight there, as the profile already is
        if (com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.SettingsDeepLink.isOpenSettings(intent)) {
            return true;
        }

        // NFC taps, cast-and-play, shared YouTube URLs, voice search etc. all carry a data Uri.
        // A plain launcher tap (or the ATV root icon) doesn't, so this tells the two apart.
        return IntentExtractor.hasData(intent) && !IntentExtractor.isRootUrl(intent);
    }

    /**
     * Use the YouTube account linked to the active Google TV profile (as tracked by the Hearth launcher).
     * A profile without a link is linked to the account named after it, or else to whatever is picked
     * in the picker, which opens for it. Deep links never open the picker.
     *
     * @return {@link #FOLLOW_NONE}, {@link #FOLLOW_SWITCHED} or {@link #FOLLOW_PICKER}
     */
    public int followGoogleTvProfile(Intent intent) {
        mLinkingProfile = null;
        ProfileLinkData links = ProfileLinkData.instance(getContext());

        if (!links.isFollowEnabled()) {
            return FOLLOW_NONE;
        }

        List<Account> accounts = mSignInService.getAccounts();

        // Nobody signed in yet: the welcome asks for that
        if (accounts == null || accounts.isEmpty()) {
            return FOLLOW_NONE;
        }

        HearthProfile profile = HearthProfile.query(getContext());
        boolean kids = ParentGate.isKidsProfile(getContext());

        if (profile == null) {
            // Can't tell who's watching. In a kids profile, a parent's account isn't kept:
            // the picker asks, and only a kid's account goes through without the parent PIN.
            if (kids && links.isParentAccount(mSignInService.getSelectedAccount()) && !ParentGate.isUnlocked()) {
                if (isDeepLink(intent)) {
                    return FOLLOW_NONE;
                }

                getViewManager().startView(ProfilePickerView.class);
                return FOLLOW_PICKER;
            }

            return FOLLOW_NONE;
        }

        // Links are saved under the profile's lasting key; the name is for guessing and showing
        String key = profile.key();
        links.adoptName(key, profile.name);
        links.setKidsProfile(key, kids);

        ProfileLinkData.Link link = links.getLink(key, accounts);

        if (link == null && accounts.size() == 1 && !HearthProfile.isOwnerUser(getContext())) {
            // Each Google TV profile runs its own copy of HearthTube: outside the owner's, its one account is
            // that person's, nothing to ask
            links.setLink(key, accounts.get(0));
            link = links.getLink(key, accounts);
        }

        if (link == null) {
            Account guess = ProfileLinkData.guessAccount(profile.name, accounts);

            if (guess != null) {
                links.setLink(key, guess);
                link = links.getLink(key, accounts);
            }
        }

        if (link == null) {
            // One login per Google TV profile: nobody else's account meanwhile. The picker shows this profile no
            // accounts, so it's the welcome, and signing in links the new account to the profile (YTSignInPresenter)
            mSignInService.selectAccount(null);

            if (isDeepLink(intent)) {
                return FOLLOW_NONE;
            }

            mLinkingProfile = key;
            getViewManager().startView(ProfilePickerView.class);
            return FOLLOW_PICKER;
        }

        if (!isSameAccount(mSignInService.getSelectedAccount(), link.account)) {
            AccountSelectionPresenter.instance(getContext()).selectAccount(link.account);
        }

        // Must come after selectAccount(), the unlock is per account
        AccountsData accountsData = AccountsData.instance(getContext());
        if (!accountsData.isPasswordAccepted()) {
            accountsData.setPasswordAccepted(true);
            BrowsePresenter.instance(getContext()).updateSections();
        }

        return FOLLOW_SWITCHED;
    }

    private static boolean isSameAccount(Account current, Account target) {
        if (current == null || target == null) {
            return current == target;
        }

        return Helpers.equals(current.getName(), target.getName());
    }

    /**
     * Name of the Google TV profile the open picker is choosing an account for, or null.
     */
    public String getLinkingProfile() {
        String name = mLinkingProfile != null ? ProfileLinkData.instance(getContext()).getDisplayName(mLinkingProfile) : null;
        // Hearth may not know the name yet: never show the bare key ("user:12")
        return name != null && !name.equals(mLinkingProfile) ? name : null;
    }

    /**
     * Open the picker mid-session (account button). Without any account there's nothing to pick, go to sign in instead.
     */
    public void start() {
        List<Account> accounts = mSignInService.getAccounts();

        if (accounts == null || accounts.isEmpty()) {
            AccountSettingsPresenter.instance(getContext()).show();
            return;
        }

        // HearthTube: kids stay on their account
        ParentGate.run(getContext(), () -> {
            mLinkingProfile = null; // a switch for now; the Google TV profile's link stays
            getViewManager().startView(ProfilePickerView.class);
        });
    }

    public void show() {
        List<Account> accounts = getVisibleAccounts();
        GlideIconFetcher.fetchDrawables(getContext(),
                Helpers.map(accounts, Account::getAvatarImageUrl),
                icons -> createAndShowView(accounts, icons));
    }

    /**
     * One login per Google TV profile: while HearthTube follows Hearth's profile, the picker offers only the
     * profile's own account and that account's channels (YouTube lists each channel as an account with the same
     * email), and none when it has no account yet (the picker is then the welcome, which signs it in). Without
     * Hearth, or when it can't tell who's watching, every account.
     */
    private List<Account> getVisibleAccounts() {
        List<Account> accounts = mSignInService.getAccounts();
        String profile = getFollowedProfile();

        if (accounts == null || profile == null) {
            return accounts;
        }

        ProfileLinkData.Link link = ProfileLinkData.instance(getContext()).getLink(profile, accounts);
        List<Account> own = new ArrayList<>();

        for (Account account : accounts) {
            if (link != null && isSamePerson(account, link.account)) {
                own.add(account);
            }
        }

        return own;
    }

    /** "Add account" in the picker: not while it follows a Google TV profile, which has its one login */
    public boolean canAddAccount() {
        return getFollowedProfile() == null;
    }

    /** The Google TV profile HearthTube follows (its key), or null when it doesn't follow one right now */
    @androidx.annotation.Nullable
    private String getFollowedProfile() {
        if (!ProfileLinkData.instance(getContext()).isFollowEnabled()) {
            return null;
        }

        HearthProfile profile = HearthProfile.query(getContext());
        return profile != null ? profile.key() : null;
    }

    /** The same Google account: by email when both have one, else by name */
    static boolean isSamePerson(Account account, Account other) {
        if (account == null || other == null) {
            return false;
        }

        if (account.getEmail() != null && other.getEmail() != null) {
            return account.getEmail().equalsIgnoreCase(other.getEmail());
        }

        return Helpers.equals(account.getName(), other.getName());
    }

    private void createAndShowView(List<Account> accounts, List<Drawable> icons) {
        if (getView() == null) {
            return;
        }

        AccountsData accountsData = AccountsData.instance(getContext());

        List<Boolean> locked = new ArrayList<>();
        for (Account account : accounts) {
            locked.add(accountsData.getAccountPassword(account.getName()) != null);
        }

        getView().show(accounts, icons, locked);
    }

    public void onAccountPicked(Account account) {
        // In a kids profile, a parent's account is a parent's call; so is pairing the profile
        boolean parentAccount = ProfileLinkData.instance(getContext()).isParentAccount(account);
        if ((mLinkingProfile != null || parentAccount) && ParentGate.isLocked(getContext())) {
            ParentGate.run(getContext(), () -> switchTo(account, true));
            return;
        }

        String pin = account != null ? AccountsData.instance(getContext()).getAccountPassword(account.getName()) : null;

        if (pin == null) {
            switchTo(account, false);
            return;
        }

        // Ask for the PIN on top of the picker, before switching. Back keeps the picker open.
        PinDialog.show(
                getContext(),
                getContext().getString(R.string.enter_profile_pin),
                newValue -> {
                    if (Utils.passwordMatch(pin, newValue)) {
                        switchTo(account, true);
                        return true;
                    }
                    return false;
                });
    }

    private void switchTo(Account account, boolean hasPin) {
        String profile = mLinkingProfile != null ? mLinkingProfile : getFollowedProfile();

        // The pick is the profile's account from now on (only its own channels are offered, getVisibleAccounts)
        if (profile != null && account != null) {
            ProfileLinkData.instance(getContext()).setLink(profile, account);
        }
        mLinkingProfile = null;

        AccountSelectionPresenter.instance(getContext()).selectAccount(account);

        if (getView() != null) {
            getView().finishView();
        }

        if (hasPin) {
            // SplashPresenter.checkAccountPassword() locked Browse on boot. The PIN was just
            // entered, so unlock and reload it. Must come after selectAccount(), the unlock is per account.
            AccountsData.instance(getContext()).setPasswordAccepted(true);
            BrowsePresenter.instance(getContext()).updateSections();
        }
    }

    public void onAddAccountPicked() {
        // The picker stays until sign-in really starts (after the parent PIN in a kids profile), so cancelling
        // the PIN leaves the welcome up rather than a signed-out Home. YTSignInPresenter closes it.
        YTSignInPresenter.instance(getContext()).start();
    }

    /** Closes the picker, if it's open */
    public void closeView() {
        if (getView() != null) {
            getView().finishView();
        }
    }

    /**
     * HearthTube's first screen in a copy nobody has signed in to: the picker as a welcome ("Hi, Sam" and
     * "Sign in"). Not when there's an account to watch with.
     */
    public void showWelcomeIfNeeded() {
        List<Account> accounts = mSignInService.getAccounts();

        if (accounts != null && !accounts.isEmpty()) {
            return;
        }

        mLinkingProfile = null;
        getViewManager().startView(ProfilePickerView.class);
    }
}
