package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.ProfileLinkData;

import java.util.List;

/**
 * Keeps kids on their own YouTube account. In a Google TV kids profile, anything that changes the
 * account (picker, Accounts settings, sign in, pairing a profile) needs the parent PIN: Hearth's whenever Hearth
 * has one, so one PIN covers both apps, else HearthTube's own (Accounts settings). Account PINs don't count: a kid
 * knows their own. The person at the screen of a kids profile never chooses the PIN (see {@link ParentPinSource}):
 * with Hearth there, a PIN comes from Hearth only, and a PIN of HearthTube's own is chosen only without Hearth and
 * before any account is signed in (the parent's first setup), or in an adult's profile. Otherwise it stays locked; a
 * parent changes accounts from their own Google TV profile. Wrong PINs are throttled like Hearth's.
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
        run(context, action, null);
    }

    /**
     * Like {@link #run(Context, Runnable)}; {@code onCancel}, when given, runs if the PIN screen is left without the
     * PIN (Back), or can't be checked.
     */
    public static void run(Context context, Runnable action, Runnable onCancel) {
        if (context == null) { // can't tell who is there: stay locked
            if (onCancel != null) {
                onCancel.run();
            }
            return;
        }

        if (!isLocked(context)) {
            action.run();
            return;
        }

        ProfileLinkData links = ProfileLinkData.instance(context);
        HearthProfile hearth = HearthProfile.live(context, HearthProfile.queryHearth(context));
        // On the phone the first parent signs in (and chooses the PIN) in kids mode: an account doesn't mean a PIN was set
        boolean signedIn = !KidsMode.isMobileBuild(context) && KidsRefused.isSignedIn();
        Runnable ask;

        switch (ParentPinSource.decide(hearth != null, hearth != null && hearth.hasParentPin, links.isUsingHearthPin(),
                HearthProfile.isGenuine(context), links.hasParentPin(), isKidsProfile(context), signedIn)) {
            case HEARTH:
                links.setUsingHearthPin(true);
                ask = () -> askPin(context, hearthPinChecker(context), action, onCancel);
                break;
            case LOCAL:
                ask = () -> askPin(context, localPinChecker(context), action, onCancel);
                break;
            case CREATE:
                // Hearth's PIN went away for good (Hearth uninstalled) and HearthTube has none of its own: nobody is
                // signed in yet, so this is the parent setting up
                String message = links.isUsingHearthPin() ? context.getString(R.string.parent_pin_hearth_gone) : null;
                links.setUsingHearthPin(false);
                ask = () -> createPin(context, action, message, onCancel);
                break;
            case HEARTH_SILENT:
                // Hearth is there but not answering (busy, restarting): its PIN can't be checked, and a new one
                // can't be chosen either, or a kid could wait for this moment
                refuse(context, R.string.parent_pin_hearth_silent, onCancel);
                return;
            case SET_IN_HEARTH:
                refuse(context, R.string.parent_pin_set_in_hearth, onCancel);
                return;
            default:
                refuse(context, R.string.kids_profile_accounts_locked, onCancel);
                return;
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

    private static void createPin(Context context, Runnable action, String message, Runnable onCancel) {
        boolean[] chosen = {false};
        PinDialog.show(
                context,
                context.getString(R.string.set_parent_pin),
                message != null ? message : context.getString(R.string.first_parent_pin_hint),
                newPin -> {
                    chosen[0] = true;
                    boolean[] confirmed = {false};
                    Utils.post(() -> PinDialog.show(
                            context,
                            context.getString(R.string.confirm_profile_pin),
                            null,
                            again -> {
                                confirmed[0] = true;
                                if (newPin.equals(again)) {
                                    ProfileLinkData.instance(context).setParentPin(newPin);
                                    sUnlockedUntilMs = android.os.SystemClock.elapsedRealtime() + UNLOCK_MS;
                                    Utils.post(action);
                                } else {
                                    Utils.post(() -> createPin(context, action, context.getString(R.string.pin_mismatch), onCancel));
                                }
                                return true;
                            },
                            cancelIf(confirmed, onCancel)));
                    return true;
                },
                cancelIf(chosen, onCancel));
    }

    /** A PIN screen's dismiss listener: {@code onCancel} when it closed without its PIN */
    private static Runnable cancelIf(boolean[] done, Runnable onCancel) {
        return onCancel == null ? null : () -> {
            if (!done[0]) {
                onCancel.run();
            }
        };
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

    /** Stays locked: says why, and counts as the PIN screen left without the PIN */
    private static void refuse(Context context, int messageId, Runnable onCancel) {
        MessageHelpers.showLongMessage(context, messageId);
        if (onCancel != null) {
            onCancel.run();
        }
    }

    /** HearthTube's own PIN, throttled like Hearth's: 5 wrong tries, then a minute's wait */
    private static PinChecker localPinChecker(Context context) {
        return pin -> {
            int result = ProfileLinkData.instance(context).verifyParentPin(pin);
            if (result > 0) {
                MessageHelpers.showMessage(context, R.string.parent_pin_wait, result);
            }
            return result == ProfileLinkData.PIN_OK;
        };
    }

    private static void askPin(Context context, PinChecker checker, Runnable action, Runnable onCancel) {
        boolean[] passed = {false};
        PinDialog.show(
                context,
                context.getString(R.string.parent_pin_title),
                context.getString(R.string.parent_pin_message),
                pin -> {
                    if (checker.check(pin)) {
                        passed[0] = true;
                        sUnlockedUntilMs = android.os.SystemClock.elapsedRealtime() + UNLOCK_MS;
                        Utils.post(action); // after the PIN screen closes
                        return true;
                    }
                    return false;
                },
                cancelIf(passed, onCancel));
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

        askPin(context, localPinChecker(context), action, null);
    }

    /** A parent entered the PIN in the last few minutes */
    public static boolean isUnlocked() {
        return android.os.SystemClock.elapsedRealtime() <= sUnlockedUntilMs;
    }

    /**
     * A kids profile that hasn't been unlocked by a parent just now.
     */
    public static boolean isLocked(Context context) {
        return !isUnlocked() && isKidsProfile(context);
    }

    /**
     * Hearth's word, else Family Link's supervision restrictions on our own user, else suspended apps (a kids
     * profile suspends every app a parent hasn't approved; adult profiles suspend none).
     */
    public static boolean isKidsProfile(Context context) {
        if (context == null) {
            return true; // can't tell who is there: treat as a kid (filters stay on, accounts stay locked)
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
        KidsDecision.Signals signals = new KidsDecision.Signals();
        signals.explicit = KidsMode.getExplicit(context);

        // Hearth's word first, when it has one (it can tell even when every app is approved)
        HearthProfile hearth = HearthProfile.isHearthSuspended(context) ? null : HearthProfile.queryHearth(context);
        signals.hearthKids = hearth != null ? hearth.kidsProfile : null;
        signals.hearthLive = hearth != null && Boolean.FALSE.equals(hearth.kidsProfile) && HearthProfile.live(context, hearth) != null;

        // Family Link supervision, read for our own user (Hearth does the same): true even when a kid's apps are all
        // approved, and in a kid's own profile user, where Google TV runs HearthTube apart from user 0 and Hearth
        signals.supervised = isSupervisedUser(context);

        if (KidsDecision.isKids(signals)) {
            return true;
        }

        // A kids profile suspends every app a parent hasn't approved. Only a TV has profiles like that: on a phone,
        // apps an adult paused with Digital Wellbeing would look the same. The scan is the costly signal, so it runs last.
        signals.suspendedGuessAllowed = !KidsMode.isMobileBuild(context);
        signals.manySuspended = signals.suspendedGuessAllowed && manySuspendedApps(context);

        return KidsDecision.isKids(signals);
    }

    /** An adult pausing an app or two with Digital Wellbeing suspends them too, so one or two don't make a kids profile */
    private static boolean manySuspendedApps(Context context) {
        PackageManager packageManager = context.getPackageManager();
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

    /** Restrictions Family Link puts on a supervised user, whatever the parent allows (see {@link SupervisionRestrictions}) */
    private static boolean isSupervisedUser(Context context) {
        try {
            android.os.UserManager users = (android.os.UserManager) context.getSystemService(Context.USER_SERVICE);
            android.os.Bundle restrictions = users != null ? users.getUserRestrictions() : null;

            if (restrictions != null) {
                return SupervisionRestrictions.isSupervised(key -> restrictions.getBoolean(key, false), KidsMode.isMobileBuild(context));
            }
        } catch (Exception e) {
            // Can't tell: the suspended apps check decides
        }

        return false;
    }
}
