package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build.VERSION;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.AccountsData;

import java.util.List;

/**
 * Keeps kids on their own YouTube account. In a Google TV kids profile, anything that changes the
 * account (picker, Accounts settings, sign in, pairing a profile) needs a parent's PIN: the PIN of any
 * locked HearthTube account. With no PIN set anywhere it's simply locked; a parent changes accounts from
 * their own Google TV profile.
 */
public final class ParentGate {
    /** One PIN covers a few steps in a row (e.g. Accounts settings, then Sign in) */
    private static final long UNLOCK_MS = 10 * 60 * 1_000;
    private static final long PANEL_CLOSE_MS = 300;
    private static long sUnlockedUntilMs;

    private ParentGate() {
    }

    /**
     * Runs the action now, or after a parent's PIN in a kids profile.
     */
    public static void run(Context context, Runnable action) {
        if (!isLocked(context)) {
            action.run();
            return;
        }

        AccountsData accountsData = AccountsData.instance(context);

        if (!accountsData.hasAnyPassword()) {
            MessageHelpers.showLongMessage(context, R.string.kids_profile_accounts_locked);
            return;
        }

        // The PIN screen belongs to the screen below an open settings panel and would show under it
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        if (dialog.isDialogShown()) {
            dialog.closeDialog();
            Utils.postDelayed(() -> askPin(context, accountsData, action), PANEL_CLOSE_MS);
        } else {
            askPin(context, accountsData, action);
        }
    }

    private static void askPin(Context context, AccountsData accountsData, Runnable action) {
        PinDialog.show(
                context,
                context.getString(R.string.parent_pin_title),
                context.getString(R.string.parent_pin_message),
                pin -> {
                    if (accountsData.isAnyPassword(pin)) {
                        sUnlockedUntilMs = System.currentTimeMillis() + UNLOCK_MS;
                        Utils.post(action); // after the PIN screen closes
                        return true;
                    }
                    return false;
                });
    }

    /**
     * A kids profile that hasn't been unlocked by a parent just now.
     */
    public static boolean isLocked(Context context) {
        return System.currentTimeMillis() > sUnlockedUntilMs && isKidsProfile(context);
    }

    /**
     * Google TV has no API for it, but a kids profile suspends every app a parent hasn't approved
     * (the same check the Hearth launcher uses). Adult profiles suspend none.
     */
    public static boolean isKidsProfile(Context context) {
        if (context == null || VERSION.SDK_INT < 24) {
            return false;
        }

        PackageManager packageManager = context.getPackageManager();

        for (String category : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            List<ResolveInfo> activities = packageManager.queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(category), 0);

            for (ResolveInfo info : activities) {
                if ((info.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SUSPENDED) != 0) {
                    return true;
                }
            }
        }

        return false;
    }
}
