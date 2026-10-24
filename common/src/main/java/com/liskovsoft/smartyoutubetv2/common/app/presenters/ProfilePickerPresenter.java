package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;

import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.mediaserviceinterfaces.SignInService;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.AccountSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ProfilePickerView;
import com.liskovsoft.smartyoutubetv2.common.prefs.AccountsData;
import com.liskovsoft.smartyoutubetv2.common.utils.AccountTwins;
import com.liskovsoft.smartyoutubetv2.common.prefs.ProfileLinkData;
import com.liskovsoft.smartyoutubetv2.common.utils.GlideIconFetcher;
import com.liskovsoft.smartyoutubetv2.common.utils.HearthProfile;
import com.liskovsoft.smartyoutubetv2.common.utils.IntentExtractor;
import com.liskovsoft.smartyoutubetv2.common.utils.PinDialog;
import com.liskovsoft.smartyoutubetv2.common.utils.KidsMode;
import com.liskovsoft.smartyoutubetv2.common.utils.ProfileFollowRule;
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
    /** A kid's profile: its account is selected, with no picker and no native dialog. Its account PIN, if any, still asks. */
    public static final int FOLLOW_KID = 3;
    /** A kid's profile with several accounts and none selected: the "Ask a parent to sign in" state, no picker. */
    public static final int FOLLOW_ASK_PARENT = 4;
    @SuppressLint("StaticFieldLeak")
    private static final String TAG = ProfilePickerPresenter.class.getSimpleName();
    private static final long PARENT_CHOICE_DELAY_MS = 400;
    private static ProfilePickerPresenter sInstance;
    private final SignInService mSignInService;
    // Google TV profile the open picker links its pick to; null when picking just for now
    private String mLinkingProfile;
    // The welcome is open in a kid's profile that has accounts but none selected: it lists none and a parent chooses
    private boolean mParentChoice;

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

        List<Account> accounts = AccountTwins.withoutTwins(YouTubeServiceManager.instance().getSignInService().getAccounts());

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
     * A kid's profile: a Google TV kids profile (its own Android user), a supervised or kids-mode phone. Such a profile
     * has one account, selected for it without a picker or any link (several: a parent chooses); see {@link ProfileFollowRule}. The owner's user,
     * shared by adults, links a profile to an account instead.
     */
    public static boolean usesOneAccount(Context context) {
        return ParentGate.isKidsProfile(context) || (!HearthProfile.isOwnerUser(context) && !KidsMode.isMobileBuild(context));
    }

    /**
     * Pick the account for this launch (the rule is {@link ProfileFollowRule}). In a kid's profile, the selected
     * account stays or the first is selected: nothing is asked, nothing linked. In the owner's user, the account linked
     * to the active Google TV profile (as tracked by the Hearth launcher), else the one named like the profile, else
     * the picker, which opens for it. Deep links never open the picker nor take the selected account away.
     *
     * @return {@link #FOLLOW_NONE}, {@link #FOLLOW_SWITCHED}, {@link #FOLLOW_PICKER}, {@link #FOLLOW_KID} or
     * {@link #FOLLOW_ASK_PARENT}
     */
    public int followGoogleTvProfile(Intent intent) {
        mLinkingProfile = null;
        mParentChoice = false;
        Context context = getContext();
        ProfileLinkData links = ProfileLinkData.instance(context);
        List<Account> accounts = mSignInService.getAccounts();
        boolean oneAccount = usesOneAccount(context);
        links.purgeKidsDataOnce(oneAccount);

        // Links are saved under the profile's lasting key; the name is for guessing and showing
        HearthProfile profile = oneAccount || links.isFollowEnabled() ? HearthProfile.query(context) : null;
        String key = profile != null && !oneAccount ? profile.key() : null;
        if (key != null) {
            links.adoptName(key, profile.name);
        }

        Account selected = mSignInService.getSelectedAccount();
        ProfileLinkData.Link link = key != null ? links.getLink(key, accounts) : null;

        ProfileFollowRule.Decision decision = ProfileFollowRule.decide(oneAccount, links.isFollowEnabled(), key,
                profile != null ? profile.name : null, link != null ? link.account : null, accounts, selected,
                isDeepLink(intent), key != null ? accountsLinkedElsewhere(links, key, accounts) : null);

        if (decision.kind == ProfileFollowRule.Kind.STANDARD) {
            return FOLLOW_NONE;
        }

        if (decision.kind == ProfileFollowRule.Kind.ASK_PARENT) {
            return FOLLOW_ASK_PARENT;
        }

        if (decision.kind == ProfileFollowRule.Kind.ASK) {
            if (decision.deselect) {
                Log.d(TAG, "Profile %s has no link: putting the selected account away (still signed in)", key);
                mSignInService.selectAccount(null);
            }

            mLinkingProfile = key;
            return openPicker(accounts) ? FOLLOW_PICKER : FOLLOW_NONE;
        }

        if (decision.link) {
            links.setLink(key, decision.account);

            if (decision.guessed) {
                MessageHelpers.showMessage(context, context.getString(R.string.profile_matched_by_name,
                        decision.account.getName(), profile.name));
            }
        }

        if (!isSameAccount(selected, decision.account)) {
            AccountSelectionPresenter.instance(context).selectAccount(decision.account);
        }

        if (oneAccount && profile == null) {
            return FOLLOW_KID;
        }

        // Must come after selectAccount(), the unlock is per account
        AccountsData accountsData = AccountsData.instance(context);
        if (!accountsData.isPasswordAccepted()) {
            accountsData.setPasswordAccepted(true);
            BrowsePresenter.instance(context).updateSections();
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
            mParentChoice = false;
            getViewManager().startView(ProfilePickerView.class);
        });
    }

    public void show() {
        List<Account> accounts = mParentChoice ? new ArrayList<>() : getVisibleAccounts();
        GlideIconFetcher.fetchDrawables(getContext(),
                Helpers.map(accounts, Account::getAvatarImageUrl),
                icons -> createAndShowView(accounts, icons));
    }

    /**
     * One login per Google TV profile: while HearthTube follows Hearth's profile (the owner's user), the picker offers
     * only the profile's own account and that account's channels (YouTube lists each channel as an account with the
     * same email). A profile with no link yet gets the accounts no other profile is linked to (an account removed and
     * signed in again, say), and the welcome's "Sign in" when there are none. In a kid's profile, without Hearth, or
     * when it can't tell who's watching, every account.
     */
    public List<Account> getVisibleAccounts() {
        List<Account> accounts = AccountTwins.withoutTwins(mSignInService.getAccounts());
        String profile = getFollowedProfile();

        if (accounts == null || profile == null) {
            return accounts;
        }

        ProfileLinkData links = ProfileLinkData.instance(getContext());
        ProfileLinkData.Link link = links.getLink(profile, accounts);

        return ProfileFollowRule.offered(link != null ? link.account : null, accounts, accountsLinkedElsewhere(links, profile, accounts));
    }

    /** The accounts that Google TV profiles other than this one watch with (one login per profile) */
    private static List<Account> accountsLinkedElsewhere(ProfileLinkData links, String profile, List<Account> accounts) {
        List<Account> linked = new ArrayList<>();

        for (String other : links.getProfileKeys()) {
            ProfileLinkData.Link otherLink = !other.equals(profile) ? links.getLink(other, accounts) : null;

            if (otherLink != null) {
                linked.add(otherLink.account);
            }
        }

        return linked;
    }

    /** "Add account" in the picker: not while it follows a Google TV profile, which has its one login */
    public boolean canAddAccount() {
        return getFollowedProfile() == null;
    }

    /** The Google TV profile HearthTube follows (its key), or null when it doesn't follow one right now (never in a kid's profile) */
    @androidx.annotation.Nullable
    private String getFollowedProfile() {
        if (!ProfileLinkData.instance(getContext()).isFollowEnabled() || usesOneAccount(getContext())) {
            return null;
        }

        HearthProfile profile = HearthProfile.query(getContext());
        return profile != null ? profile.key() : null;
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

        boolean wasClosed = com.liskovsoft.smartyoutubetv2.common.utils.LaunchGate.isClosed(getContext());
        AccountSelectionPresenter.instance(getContext()).selectAccount(account);

        if (getView() != null) {
            getView().finishView();
        }

        if (wasClosed && !hasPin) {
            BrowsePresenter.instance(getContext()).updateSections(); // a kid's Home was only asking for a sign-in
        }

        if (hasPin) {
            // SplashPresenter.checkAccountPassword() locked Browse on boot. The PIN was just
            // entered, so unlock and reload it. Must come after selectAccount(), the unlock is per account.
            AccountsData.instance(getContext()).setPasswordAccepted(true);
            BrowsePresenter.instance(getContext()).updateSections();
        }
    }

    public void onAddAccountPicked() {
        List<Account> accounts = mSignInService.getAccounts();

        // A kid's profile with accounts but none chosen: the parent, past the PIN, chooses instead of signing in again
        if (mParentChoice && accounts != null && !accounts.isEmpty()) {
            ParentGate.run(getContext(), () -> {
                mParentChoice = false;
                closeView();
                Utils.postDelayed(() -> AccountSelectionPresenter.instance(getContext()).showParentChoice(
                        () -> BrowsePresenter.instance(getContext()).updateSections()), PARENT_CHOICE_DELAY_MS);
            });
            return;
        }

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
        openPicker(accounts);
    }

    /**
     * A kids profile with nobody signed in (or accounts and none chosen) was asked for something (see {@link
     * com.liskovsoft.smartyoutubetv2.common.utils.LaunchGate}): the welcome. A kid never gets a chooser and the app
     * picks no account for them: with accounts, the welcome's button leads a parent, past the PIN, to the choice.
     */
    public void askToSignIn() {
        List<Account> accounts = mSignInService.getAccounts();

        if (accounts == null || accounts.isEmpty()) {
            showWelcomeIfNeeded();
        } else {
            mLinkingProfile = null;
            mParentChoice = true;
            if (getViewManager().getActivity(ProfilePickerView.class) != null) {
                getViewManager().startView(ProfilePickerView.class);
            }
        }
    }

    /**
     * Opens the picker, or the welcome when there are no accounts. The phone has no picker screen (its sign-in is
     * the parent's): there, the first account is selected when none is.
     *
     * @return the picker is open
     */
    public boolean openPicker(List<Account> accounts) {
        if (getViewManager().getActivity(ProfilePickerView.class) != null) {
            getViewManager().startView(ProfilePickerView.class);
            return true;
        }

        // A kid's profile never gets an automatic account (a parent chooses)
        if (!usesOneAccount(getContext())) {
            selectFirstIfNone(accounts);
        }

        return false;
    }

    private void selectFirstIfNone(List<Account> accounts) {
        if (accounts != null && !accounts.isEmpty() && mSignInService.getSelectedAccount() == null) {
            AccountSelectionPresenter.instance(getContext()).selectAccount(accounts.get(0));
        }
    }
}
