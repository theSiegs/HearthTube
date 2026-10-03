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
import com.liskovsoft.smartyoutubetv2.common.utils.GlideIconFetcher;
import com.liskovsoft.smartyoutubetv2.common.utils.IntentExtractor;
import com.liskovsoft.smartyoutubetv2.common.utils.PinDialog;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayList;
import java.util.List;

public class ProfilePickerPresenter extends BasePresenter<ProfilePickerView> {
    @SuppressLint("StaticFieldLeak")
    private static ProfilePickerPresenter sInstance;
    private final SignInService mSignInService;

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

    private static boolean isDeepLink(Intent intent) {
        if (IntentExtractor.extractAccountName(intent) != null) {
            // Caller already specified which account to use.
            return true;
        }

        // NFC taps, cast-and-play, shared YouTube URLs, voice search etc. all carry a data Uri.
        // A plain launcher tap (or the ATV root icon) doesn't, so this tells the two apart.
        return IntentExtractor.hasData(intent) && !IntentExtractor.isRootUrl(intent);
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

        getViewManager().startView(ProfilePickerView.class);
    }

    public void show() {
        GlideIconFetcher.fetchDrawables(getContext(),
                Helpers.map(mSignInService.getAccounts(), Account::getAvatarImageUrl),
                icons -> createAndShowView(mSignInService.getAccounts(), icons));
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

    public void onGuestPicked() {
        onAccountPicked(null);
    }

    public void onAddAccountPicked() {
        if (getView() != null) {
            getView().finishView();
        }

        YTSignInPresenter.instance(getContext()).start();
    }
}
