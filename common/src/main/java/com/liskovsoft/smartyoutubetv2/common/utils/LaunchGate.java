package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.ProfilePickerPresenter;

/**
 * HearthTube: what YouTube lists signed out isn't supervised, so in a kids profile nobody signed in means nothing is
 * shown or played, however the app was opened (the launcher, a link, a cast, voice search, Google TV's search, Watch
 * Next, a home screen shortcut). The kid sees the welcome ("Sign in", after a parent's PIN) instead. Adults who aren't
 * signed in keep the old way: a link plays first, a plain launch asks.
 */
public final class LaunchGate {
    public enum Decision {
        /** Carry on with what was asked for */
        PROCEED,
        /** Ask first (the welcome or the picker); what was asked for waits or is dropped */
        ASK
    }

    private LaunchGate() {
    }

    /**
     * @param kids       a kids profile
     * @param hasAccount an account is selected
     * @param isDeepLink the launch carries something to open (a link, a cast, a search)
     */
    public static Decision decide(boolean kids, boolean hasAccount, boolean isDeepLink) {
        if (hasAccount) {
            return Decision.PROCEED;
        }

        return kids || !isDeepLink ? Decision.ASK : Decision.PROCEED;
    }

    /**
     * The phone has no picker screen: which of the signed-in accounts to select when none is. A kid's phone only gets
     * accounts from a parent, but with several, which one is the kid's isn't known: none (the screens ask a parent).
     *
     * @return the index, or -1 for none
     */
    public static int phoneAutoSelect(boolean kids, int accounts) {
        if (accounts <= 0 || (kids && accounts > 1)) {
            return -1;
        }

        return 0;
    }

    /**
     * A kids profile and nobody signed in: no videos, no search, no channels. On the TV; the phone's own screens ask
     * the same (KidGate) and show "Ask a parent to sign in" where they stand.
     */
    public static boolean isClosed(Context context) {
        return !KidsMode.isMobileBuild(context) && ParentGate.isKidsProfile(context) && !KidsRefused.isSignedIn();
    }

    /** Closed: shows the welcome in place of what was asked for and says so (true) */
    public static boolean refuse(Context context) {
        if (!isClosed(context)) {
            return false;
        }

        ProfilePickerPresenter.instance(context).askToSignIn();
        return true;
    }
}
