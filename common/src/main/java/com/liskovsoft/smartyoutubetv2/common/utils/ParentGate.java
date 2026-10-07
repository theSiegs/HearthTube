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
import com.liskovsoft.smartyoutubetv2.common.prefs.ProfileLinkData;

import java.util.List;

/**
 * Keeps kids on their own YouTube account. In a Google TV kids profile, anything that changes the
 * account (picker, Accounts settings, sign in, pairing a profile) needs the parent PIN: Hearth's whenever Hearth
 * has one, so one PIN covers both apps, else HearthTube's own (Accounts settings). Account PINs don't count: a kid
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
        // No PIN anywhere yet (a kid's own copy of HearthTube starts empty): the parent setting it up chooses one
        Runnable ask = checker != null ? () -> askPin(context, checker, action) : () -> createPin(context, action, null);

        // The PIN screen belongs to the screen below an open settings panel and would show under it
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        if (dialog.isDialogShown()) {
            dialog.closeDialog();
            Utils.postDelayed(ask, PANEL_CLOSE_MS);
        } else {
            ask.run();
        }
    }

    private static void createPin(Context context, Runnable action, String message) {
        PinDialog.show(
                context,
                context.getString(R.string.set_parent_pin),
                message != null ? message : context.getString(R.string.first_parent_pin_hint),
                newPin -> {
                    Utils.post(() -> PinDialog.show(
                            context,
                            context.getString(R.string.confirm_profile_pin),
                            confirmed -> {
                                if (newPin.equals(confirmed)) {
                                    ProfileLinkData.instance(context).setParentPin(newPin);
                                    sUnlockedUntilMs = System.currentTimeMillis() + UNLOCK_MS;
                                    Utils.post(action);
                                } else {
                                    Utils.post(() -> createPin(context, action, context.getString(R.string.pin_mismatch)));
                                }
                                return true;
                            }));
                    return true;
                });
    }

    private interface PinChecker {
        boolean check(String pin);
    }

    /** Hearth's parent PIN when it has one, else HearthTube's own. Null: no PIN set anywhere. */
    private static PinChecker pinChecker(Context context) {
        if (usesHearthPin(context)) {
            return pin -> {
                int result = HearthProfile.verifyParentPin(context, pin);
                if (result > 0) {
                    MessageHelpers.showMessage(context, R.string.parent_pin_wait, result);
                }
                return result == HearthProfile.PIN_OK;
            };
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

    /** Hearth has a parent PIN, which then stands in for HearthTube's own */
    public static boolean usesHearthPin(Context context) {
        HearthProfile hearth = HearthProfile.queryHearth(context);
        return hearth != null && hearth.hasParentPin;
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
     * Hearth's word, else Family Link's supervision restrictions on our own user, else suspended apps (a kids
     * profile suspends every app a parent hasn't approved; adult profiles suspend none).
     */
    public static boolean isKidsProfile(Context context) {
        if (context == null || VERSION.SDK_INT < 24) {
            return false;
        }

        // Hearth's word first, when it has one (it can tell even when every app is approved)
        HearthProfile hearth = HearthProfile.isHearthSuspended(context) ? null : HearthProfile.queryHearth(context);
        if (hearth != null && Boolean.TRUE.equals(hearth.kidsProfile)) {
            return true;
        }

        // Family Link supervision, read for our own user (Hearth does the same): true even when a kid's apps are all
        // approved, and in a kid's own profile user, where Google TV runs HearthTube apart from user 0 and Hearth
        if (isSupervisedUser(context)) {
            return true;
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

    /** Restrictions Family Link puts on a supervised (kids) profile's user, whatever the parent allows */
    private static final String[] SUPERVISION_RESTRICTIONS = {
            "no_config_credentials", "no_grant_admin", "no_add_managed_profile"};

    private static boolean isSupervisedUser(Context context) {
        try {
            android.os.UserManager users = (android.os.UserManager) context.getSystemService(Context.USER_SERVICE);
            android.os.Bundle restrictions = users != null ? users.getUserRestrictions() : null;

            if (restrictions != null) {
                for (String restriction : SUPERVISION_RESTRICTIONS) {
                    if (restrictions.getBoolean(restriction, false)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // Can't tell: the suspended apps check decides
        }

        return false;
    }
}
