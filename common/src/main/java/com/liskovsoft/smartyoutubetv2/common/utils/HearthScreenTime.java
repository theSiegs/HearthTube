package com.liskovsoft.smartyoutubetv2.common.utils;

import android.app.Activity;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.ProfileLinkData;

import java.lang.ref.WeakReference;

/**
 * Google TV screen time for kids: when Hearth says time's up (Google TV's time-up or bedtime screen), HearthTube
 * stops the video and goes to the launcher, where Google TV's own screen takes over. And Hearth's daily YouTube
 * allowance ({@link HearthAllowance}): once it's used up (or Home Assistant's schedule is locked), a kid can't browse
 * or search either; HearthTube closes behind a full-screen "Out of YouTube time" page ({@link HearthTimeUpActivity})
 * that then goes home. It decides the way the player does, so where the player would play, HearthTube stays open.
 * Checked as each screen opens and every half minute while one is showing, and at once when Hearth announces a change.
 */
public final class HearthScreenTime {
    private static final long CHECK_MS = 30_000;
    private static WeakReference<Activity> sActivity = new WeakReference<>(null);
    private static final Runnable sCheck = new Runnable() {
        @Override
        public void run() {
            Activity activity = sActivity.get();

            if (activity != null && !activity.isFinishing()) {
                check(activity);
                Utils.postDelayed(this, CHECK_MS);
            }
        }
    };

    /** Hearth announces changes (time's up, profile switches) on its provider: react at once */
    private static final ContentObserver sObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            Activity activity = sActivity.get();

            if (activity != null && !activity.isFinishing()) {
                check(activity);
            }
        }
    };
    private static boolean sObserving;

    private HearthScreenTime() {
    }

    /** A HearthTube screen came to the front */
    public static void onResume(Activity activity) {
        sActivity = new WeakReference<>(activity);
        Utils.removeCallbacks(sCheck);

        if (!sObserving) {
            sObserving = true;
            for (Uri hearth : HearthProfile.ACTIVE_URIS) {
                try {
                    activity.getApplicationContext().getContentResolver().registerContentObserver(hearth, false, sObserver);
                } catch (Exception e) {
                    // No Hearth (or too old): the half-minute check stays
                }
            }
        }
        if (check(activity)) {
            Utils.postDelayed(sCheck, CHECK_MS);
        }
    }

    /** It left the front */
    public static void onPause(Activity activity) {
        if (sActivity.get() == activity) {
            Utils.removeCallbacks(sCheck);
            // Only a HearthTube screen in front reacts: never send another app to the launcher
            sActivity = new WeakReference<>(null);
        }
    }

    /**
     * @return false when time was up (and HearthTube stepped aside)
     */
    private static boolean check(Activity activity) {
        HearthProfile hearth = HearthProfile.isHearthSuspended(activity) ? null
                : HearthProfile.live(activity, HearthProfile.queryHearth(activity));
        int message;

        if (hearth != null) {
            // Hearth changed its "share what's playing" answer (its Settings → Home Assistant switch)
            com.liskovsoft.smartyoutubetv2.common.prefs.HearthLinkData.instance(activity).applyHearthAnswer(hearth.shareNowPlaying);
        }

        if (hearth != null && hearth.hasParentPin) {
            // Remembered, so if Hearth's PIN goes away later the parent gets to choose a new one (ParentGate)
            ProfileLinkData.instance(activity).setUsingHearthPin(true);
        }

        if (hearth != null && hearth.screenTimeUp) {
            message = R.string.screen_time_up;
        } else {
            return !closeIfOutOfTime(activity);
        }

        PlaybackPresenter.instance(activity).forceFinish();
        MessageHelpers.showLongMessage(activity, message);
        HearthProfile.goHomeOrStepAside(activity);
        return false;
    }

    /**
     * The kid's YouTube allowance is used up, or YouTube is locked: the "Out of YouTube time" page comes up and the
     * whole app closes behind it. Adults and "no limit" never get here ({@link HearthAllowance} says play), nor does
     * a parent who just typed the PIN in this profile. When the allowance can't be read, it's what the player does:
     * no Hearth (or an old one) and no limit kept from earlier today means HearthTube stays open.
     *
     * @return true when HearthTube is closing
     */
    private static boolean closeIfOutOfTime(Activity activity) {
        if (KidsMode.isMobileBuild(activity) || ParentGate.isUnlocked()) {
            return false;
        }

        HearthAllowance.Refusal refusal;

        try {
            refusal = HearthAllowance.refusalNow(activity);
        } catch (RuntimeException e) {
            // Can't tell: never close on a doubt
            return false;
        }

        if (refusal == null) {
            return false;
        }

        PlaybackPresenter.instance(activity).forceFinish();

        if (HearthTimeUpActivity.show(activity, refusal)) {
            // The way Back on the home screen leaves: every screen goes, the app is ready to start over
            com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager.instance(activity).properlyFinishTheApp(activity);
        } else {
            // A build without the page (SmartTube's own flavors): what the player says, and step aside
            MessageHelpers.showLongMessage(activity, refusal.message);
            HearthProfile.goHomeOrStepAside(activity);
        }

        return true;
    }
}
