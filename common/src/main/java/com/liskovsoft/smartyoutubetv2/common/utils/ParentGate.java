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
import com.liskovsoft.smartyoutubetv2.common.prefs.HearthLinkData;
import com.liskovsoft.smartyoutubetv2.common.prefs.ProfileLinkData;

import java.util.List;

/**
 * Keeps kids on their own YouTube account. In a Google TV kids profile, anything that changes the
 * account (picker, Accounts settings, sign in, pairing a profile) needs the parent PIN, set in Accounts
 * settings, or Hearth's parent PIN (Works with Hearth) so one PIN covers both apps. Account PINs don't count: a kid
 * knows their own. Without a parent PIN it's simply locked; a parent changes accounts from their own Google TV profile.
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

        PinChecker checker = pinChecker(context);

        if (checker == null) {
            MessageHelpers.showLongMessage(context, R.string.kids_profile_accounts_locked);
            return;
        }

        // The PIN screen belongs to the screen below an open settings panel and would show under it
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        if (dialog.isDialogShown()) {
            dialog.closeDialog();
            Utils.postDelayed(() -> askPin(context, checker, action), PANEL_CLOSE_MS);
        } else {
            askPin(context, checker, action);
        }
    }

    private interface PinChecker {
        boolean check(String pin);
    }

    /** Hearth's parent PIN when it has one (and that's switched on), else HearthTube's own. Null: no PIN set anywhere. */
    private static PinChecker pinChecker(Context context) {
        if (HearthLinkData.instance(context).isHearthParentPinEnabled()) {
            HearthProfile hearth = HearthProfile.queryHearth(context);

            if (hearth != null && hearth.hasParentPin) {
                return pin -> {
                    int result = HearthProfile.verifyParentPin(context, pin);
                    if (result > 0) {
                        MessageHelpers.showMessage(context, R.string.parent_pin_wait, result);
                    }
                    return result == HearthProfile.PIN_OK;
                };
            }
        }

        String parentPin = ProfileLinkData.instance(context).getParentPin();
        return parentPin != null ? parentPin::equals : null;
    }

    private static void askPin(Context context, PinChecker checker, Runnable action) {
        PinDialog.show(
                context,
                context.getString(R.string.parent_pin_title),
                context.getString(R.string.parent_pin_message),
                pin -> {
                    if (checker.check(pin)) {
                        sUnlockedUntilMs = System.currentTimeMillis() + UNLOCK_MS;
                        Utils.post(action); // after the PIN screen closes
                        return true;
                    }
                    return false;
                });
    }

    /** A parent entered the PIN in the last few minutes */
    public static boolean isUnlocked() {
        return System.currentTimeMillis() <= sUnlockedUntilMs;
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
