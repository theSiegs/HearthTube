package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;

/**
 * Adults' Google TV profiles share the TV owner's Android user, so they share one running HearthTube: without this,
 * the next adult to open it would land in the last one's video. When Hearth's active profile has changed since a
 * HearthTube screen last looked, the player closes and HearthTube starts over from its launch screen, which picks the
 * new profile's account. Kids profiles are users of their own, each with its own HearthTube: nothing to do there.
 */
public final class HearthViewer {
    private static final String TAG = HearthViewer.class.getSimpleName();
    /** The profile HearthTube's screens were last shown to; null until Hearth first named one */
    private static String sLastKey;

    private HearthViewer() {
    }

    /** The profile HearthTube's screens were last shown to (Hearth's key, "user:0:slug"), or null when not known yet */
    @Nullable
    public static String currentKey() {
        return sLastKey;
    }

    /** The profile changed (or became known): what is kept per profile in memory starts over */
    private static void profileChanged() {
        KeywordFilter.onProfileChanged();
        KidsRefused.clear();
    }
    /**
     * HearthTube is being launched: when someone else is watching now, forget what the last one had open, so the
     * launch goes to the browse screen.
     */
    public static void onLaunch(Context context) {
        check(context, context != null && !HearthProfile.isHearthSuspended(context) ? HearthProfile.queryHearth(context) : null, false);
    }

    /**
     * A HearthTube screen came to the front: starts over when someone else is watching now.
     *
     * @return true when it started over
     */
    public static boolean onResume(Context context) {
        return check(context, context != null && !HearthProfile.isHearthSuspended(context) ? HearthProfile.queryHearth(context) : null, true);
    }

    private static boolean check(Context context, @Nullable HearthProfile hearth, boolean startOver) {
        if (context == null || !HearthProfile.isOwnerUser(context)) {
            return false;
        }

        // Mid-switch Hearth may still name the last profile: wait for the next look
        String key = hearth != null && !Boolean.FALSE.equals(hearth.profileReady) ? hearth.key() : null;

        if (!isNewViewer(sLastKey, key)) {
            if (key != null && !key.equals(sLastKey)) {
                sLastKey = key;
                profileChanged();
            }
            return false;
        }

        Log.d(TAG, "Another profile is watching now: starting over");
        sLastKey = key;
        profileChanged();
        PlaybackPresenter.instance(context).forceFinish();
        ViewManager.instance(context).forgetScreens();

        if (startOver) {
            ViewManager.instance(context).startView(SplashView.class, true);
        }
        return true;
    }

    /** {@code now} is someone other than {@code last}, both known */
    static boolean isNewViewer(@Nullable String last, @Nullable String now) {
        return last != null && now != null && !last.equals(now);
    }
}
