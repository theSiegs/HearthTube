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
    /** One PIN covers a few steps in a row (e.g. Accounts settings, then Sign in); playing a video locks again */
    private static final long UNLOCK_MS = 3 * 60 * 1_000;
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

        ProfileLinkData links = ProfileLinkData.instance(context);
        HearthProfile hearth = HearthProfile.live(context, HearthProfile.queryHearth(context));
        Runnable ask;

        if (hearth != null && hearth.hasParentPin) {
            links.setUsingHearthPin(true);
            ask = () -> askPin(context, hearthPinChecker(context), action);
        } else if (links.isUsingHearthPin()) {
            if (isHearthSilent(context, hearth)) {
                // Hearth is there but not answering (busy, restarting): its PIN can't be checked, and a new one
                // can't be chosen either, or a kid could wait for this moment
                MessageHelpers.showLongMessage(context, R.string.parent_pin_hearth_silent);
                return;
            }

            // Hearth's PIN went away for good (removed in Hearth, which takes that PIN, or Hearth uninstalled):
            // HearthTube's own PIN from before is likely forgotten, so the parent chooses a new one right away
            links.setUsingHearthPin(false);
            links.setParentPin(null);
            ask = () -> createPin(context, action, context.getString(R.string.parent_pin_hearth_gone));
        } else if (!links.hasParentPin()) {
            // No PIN yet (a kid's own copy starts empty): the parent setting it up chooses one
            ask = () -> createPin(context, action, null);
        } else {
            ask = () -> askPin(context, links::checkParentPin, action);
        }

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

    private static PinChecker hearthPinChecker(Context context) {
        return pin -> {
            int result = HearthProfile.verifyParentPin(context, pin);
            if (result > 0) {
                MessageHelpers.showMessage(context, R.string.parent_pin_wait, result);
            } else if (result == HearthProfile.PIN_UNAVAILABLE) {
                // Not a wrong PIN: Hearth (or its agent's link to it) didn't answer in time
                MessageHelpers.showLongMessage(context, R.string.parent_pin_hearth_silent);
            }
            return result == HearthProfile.PIN_OK;
        };
    }

    /**
     * Hearth is installed and genuine but didn't answer. (Not installed, or answering that it has no PIN, are both
     * for good: the first takes an adult uninstalling it, the second Hearth's own PIN.)
     */
    private static boolean isHearthSilent(Context context, HearthProfile hearth) {
        return hearth == null && HearthProfile.isGenuine(context) == Boolean.TRUE;
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
        HearthProfile hearth = HearthProfile.live(context, HearthProfile.queryHearth(context));
        return hearth != null && hearth.hasParentPin;
    }

    /** The parent is done (a video starts playing): the next account change asks again */
    public static void lock() {
        sUnlockedUntilMs = 0;
    }

    /**
     * Asks for the parent PIN that's set now (HearthTube's own), then runs the action: changing or removing it takes
     * knowing it, wherever the setting is opened.
     */
    public static void confirmParentPin(Context context, Runnable action) {
        ProfileLinkData links = ProfileLinkData.instance(context);

        if (!links.hasParentPin()) {
            action.run();
            return;
        }

        askPin(context, links::checkParentPin, action);
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

        // Asked many times per screen, and each answer can take a provider query and package scans: keep it a few
        // seconds (a profile switch restarts the app anyway)
        long now = android.os.SystemClock.elapsedRealtime();
        if (now < sKidsCheckedUntilMs) {
            return sKids;
        }

        sKids = isKidsProfileNow(context);
        sKidsCheckedUntilMs = now + KIDS_CACHE_MS;
        return sKids;
    }

    private static final long KIDS_CACHE_MS = 5_000;
    /** Suspended launcher apps that make a kids profile (a Digital Wellbeing pause is one or two) */
    private static final int MIN_SUSPENDED_FOR_KIDS = 3;
    private static volatile boolean sKids;
    private static volatile long sKidsCheckedUntilMs;

    private static boolean isKidsProfileNow(Context context) {

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
        // A kids profile suspends every app a parent hasn't approved, usually many. A grown-up pausing an app or two
        // with Digital Wellbeing suspends them too, so one or two don't make a kids profile.
        java.util.Set<String> suspended = new java.util.HashSet<>();

        for (String category : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            List<ResolveInfo> activities = packageManager.queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(category), 0);

            for (ResolveInfo info : activities) {
                if ((info.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SUSPENDED) != 0) {
                    suspended.add(info.activityInfo.packageName);
                    if (suspended.size() >= MIN_SUSPENDED_FOR_KIDS) {
                        return true;
                    }
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
