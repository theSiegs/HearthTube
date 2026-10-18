package com.thesiegs.hearthtube.mobile.ui;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.utils.KidsRefused;
import com.liskovsoft.smartyoutubetv2.common.utils.ParentGate;

/**
 * What YouTube lists signed out isn't supervised, so on a kid's device nothing it lists may be shown until a parent has
 * signed the kid in: not the home screen, not search results, not a channel's videos. Every screen that shows videos
 * asks here first.
 */
public final class KidGate {
    private KidGate() {
    }

    /**
     * Runs the action now, or after the parent PIN on a kid's device. For what changes things: a video's menu
     * (subscribe, playlists, blocking channels and words) is a parent's.
     */
    public static void parentOnly(Context context, Runnable action) {
        ParentGate.run(context, action);
    }

    /** Kids mode, and nobody signed in: show no videos */
    public static boolean isClosed(Context context) {
        return ParentGate.isKidsProfile(context) && !KidsRefused.isSignedIn();
    }
}
