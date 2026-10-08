package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.AccountsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.ProfileLinkData;
import com.liskovsoft.smartyoutubetv2.common.utils.HearthProfile;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import io.reactivex.disposables.Disposable;

import java.util.List;
import com.liskovsoft.smartyoutubetv2.common.utils.ParentGate;

public class YTSignInPresenter extends SignInPresenter {
    private static final String TAG = YTSignInPresenter.class.getSimpleName();
    private static final String SIGN_IN_URL = "https://yt.be/activate"; // 18+, no search history
    private static final String QR_SIGN_IN_URL = "https://youtube.com/qr/activate/";
    //private static final String SIGN_IN_URL = "https://youtube.com/tv/activate"; // 18+, no search history
    //private static final String SIGN_IN_URL = "https://youtube.com/activate"; // age restricted, supports search history
    @SuppressLint("StaticFieldLeak")
    private static YTSignInPresenter sInstance;
    private final ServiceManager mService;
    private Disposable mSignInAction;
    // HearthTube: accounts before this sign-in, to tell which one is new
    private final java.util.Set<String> mAccountsBefore = new java.util.HashSet<>();
    private boolean mSignedIn;

    private YTSignInPresenter(Context context) {
        super(context);
        mService = YouTubeServiceManager.instance();
    }

    public static YTSignInPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new YTSignInPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public void unhold() {
        RxHelper.disposeActions(mSignInAction);
        sInstance = null;
    }

    @Override
    public void onViewDestroyed() {
        super.onViewDestroyed();
        boolean signedIn = mSignedIn;
        Context context = getContext();
        unhold();

        // HearthTube: closed without signing in, and nobody can watch yet: back to the welcome
        if (!signedIn && context != null) {
            Utils.post(() -> ProfilePickerPresenter.instance(context).showWelcomeIfNeeded());
        }
    }

    @Override
    public void onViewInitialized() {
        super.onViewInitialized();
        RxHelper.disposeActions(mSignInAction);
        updateUserCode();
    }

    @Override
    public void onActionClicked() {
        if (getView() != null) {
            getView().close();
        }
    }

    private void updateUserCode() {
        mSignInAction = mService.getSignInService().signInObserve()
                .subscribe(
                        userCode ->
                                getView().showCode(userCode, SIGN_IN_URL, QR_SIGN_IN_URL + userCode.replace(" ", "-")),
                        error -> {
                            Log.e(TAG, "Sign in error: %s", error.getMessage());
                            if (getView() != null) {
                                getView().showCode(error.getMessage(), "");
                            }
                        },
                        () -> {
                            // Success
                            mSignedIn = true;
                            onSignedIn();

                            if (getView() != null) {
                                getView().close();
                            }
                        }
                 );
    }

    public void start() {
        // HearthTube: a new account in a kids profile would become theirs
        ParentGate.run(getContext(), this::startUnlocked);
    }

    private void startUnlocked() {
        mSignedIn = false;
        mAccountsBefore.clear();
        List<Account> accounts = mService.getSignInService().getAccounts();
        if (accounts != null) {
            for (Account account : accounts) {
                mAccountsBefore.add(account.getName());
            }
        }

        // The welcome or picker it came from closes first (closing later would bring Home over the sign-in)
        ProfilePickerPresenter.instance(getContext()).closeView();
        super.start();
        RxHelper.disposeActions(mSignInAction);
    }

    /**
     * HearthTube: no account list to pick from after signing in. The new account is the one watching, this
     * Google TV profile (through Hearth) remembers it, and a note says who's signed in.
     */
    private void onSignedIn() {
        Context context = getContext();
        List<Account> accounts = mService.getSignInService().getAccounts();
        Account added = null;

        if (accounts != null) {
            for (Account account : accounts) {
                if (!mAccountsBefore.contains(account.getName())) {
                    added = account;
                    break;
                }
            }
        }

        if (added == null) {
            added = mService.getSignInService().getSelectedAccount();
        }

        if (added == null) {
            return;
        }

        AccountSelectionPresenter.instance(context).selectAccount(added);

        // The profile's account from now on: in its own copy (each Google TV profile runs one), or when it has
        // none yet. In the owner's copy an account added for someone else doesn't take the profile over.
        ProfileLinkData links = ProfileLinkData.instance(context);
        HearthProfile profile = HearthProfile.query(context);
        if (profile != null && links.isFollowEnabled()
                && (!HearthProfile.isOwnerUser(context) || links.getLink(profile.key(), accounts) == null)) {
            links.setLink(profile.key(), added);
        }

        AccountsData.instance(context).setPasswordAccepted(true);
        BrowsePresenter.instance(context).updateSections();
        MessageHelpers.showMessage(context, context.getString(R.string.signed_in_as, added.getName()));
    }
}
