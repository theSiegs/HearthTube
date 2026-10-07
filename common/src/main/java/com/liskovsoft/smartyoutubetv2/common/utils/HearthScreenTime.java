package com.liskovsoft.smartyoutubetv2.common.utils;

import android.app.Activity;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;

import java.lang.ref.WeakReference;

/**
 * Google TV screen time for kids: when Hearth says time's up (Google TV's time-up or bedtime screen), HearthTube
 * stops the video and goes to the launcher, where Google TV's own screen takes over. Checked as each screen opens
 * and every half minute while one is showing, and at once when Hearth announces a change.
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
            try {
                activity.getApplicationContext().getContentResolver()
                        .registerContentObserver(Uri.parse("content://com.leanbitlab.ltvL.profile/active"), false, sObserver);
                sObserving = true;
            } catch (Exception e) {
                // No Hearth (or too old): the half-minute check stays
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
        HearthProfile hearth = HearthProfile.isHearthSuspended(activity) ? null : HearthProfile.queryHearth(activity);
        int message;

        if (hearth != null && hearth.screenTimeUp) {
            message = R.string.screen_time_up;
        } else if (isUnwatchedKidsProfile(activity, hearth)) {
            message = R.string.screen_time_unknown;
        } else {
            return true;
        }

        PlaybackPresenter.instance(activity).forceFinish();
        MessageHelpers.showLongMessage(activity, message);
        HearthProfile.goHome(activity);
        return false;
    }

    /**
     * Fail-safe: HearthTube is a device admin (so Google TV can't suspend it in kids profiles) in a kids profile,
     * but Hearth can't tell screen time (missing, suspended, too old, or its accessibility service isn't running). Then
     * HearthTube stays shut, as Google TV
     * would have kept it.
     */
    private static boolean isUnwatchedKidsProfile(Activity activity, HearthProfile hearth) {
        // Hearth's accessibility service sees screen time: without it, screen_time_up stays 0 whatever Google TV says
        boolean hearthWatches = hearth != null && hearth.kidsProfile != null && !Boolean.FALSE.equals(hearth.serviceRunning);

        return !hearthWatches && isOwnAdminActive(activity) && ParentGate.isKidsProfile(activity);
    }

    private static boolean isOwnAdminActive(Activity activity) {
        try {
            android.app.admin.DevicePolicyManager dpm =
                    (android.app.admin.DevicePolicyManager) activity.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE);
            java.util.List<android.content.ComponentName> admins = dpm != null ? dpm.getActiveAdmins() : null;

            if (admins != null) {
                for (android.content.ComponentName admin : admins) {
                    if (activity.getPackageName().equals(admin.getPackageName())) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // No device policy service: not an admin
        }

        return false;
    }
}
