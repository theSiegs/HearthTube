package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HearthTube's side of Hearth's daily YouTube allowance (Hearth's provider contract 6): tells Hearth how long videos
 * actually played (not paused, buffering or in menus), and stops them when the allowance is used up or Home
 * Assistant's schedule is locked ({@link YouTubeAllowance} decides). The player (HearthAllowanceController) says when
 * playing starts and stops. Without a Hearth that can be relied on (missing, older than contract 6, or in a kids
 * profile whose Hearth agent isn't connected) there's no allowance, as before.
 */
public final class HearthAllowance {
    private static final String TAG = HearthAllowance.class.getSimpleName();
    /** Less than this waits for the next report */
    private static final long MIN_REPORT_MS = 1_000;
    private static final String PREFS = "hearth_allowance";
    private static final String PREF_HA_READ = "home_assistant_read";
    private static final Object sLock = new Object();
    /** Reports go to Hearth one at a time, off the main thread (a kids profile's Hearth agent passes them on) */
    private static final ExecutorService sReporter = Executors.newSingleThreadExecutor();
    private static YouTubeAllowance sAllowance;
    private static String sSaved;
    private static YouTubeAllowance.Verdict sLastVerdict;
    /** When the current stretch of playing started (SystemClock.elapsedRealtime), -1 while not playing */
    private static long sPlayingSince = -1;
    /** Played and not taken by Hearth yet; at most a report's worth (older time is let go) */
    private static long sUnreportedMs;

    private HearthAllowance() {
    }

    /** A video started playing. True when it wasn't playing a moment ago. */
    public static boolean onPlaying() {
        synchronized (sLock) {
            if (sPlayingSince != -1) {
                return false;
            }

            sPlayingSince = SystemClock.elapsedRealtime();
            return true;
        }
    }

    /** It's waiting (buffering): the time so far counts, and goes with the next report */
    public static void onWaiting(Context context) {
        take(context, false);
    }

    /** It paused or stopped, or the player closed: the time so far counts, and is reported now */
    public static void onStopped(Context context) {
        take(context, false);
        send(context);
    }

    /** Reports what's played so far, playing on (every half minute, and when HearthTube goes to the background) */
    public static void report(Context context) {
        take(context, true);
        send(context);
    }

    public static boolean isPlaying() {
        synchronized (sLock) {
            return sPlayingSince != -1;
        }
    }

    /**
     * Stops the player when HearthTube mustn't play now, saying why, the way a refused Short closes it. True when it
     * stopped it.
     */
    public static boolean stopIfRefused(Context context, @Nullable PlaybackView player) {
        String refusal = player != null ? refusal(context) : null;

        if (refusal == null) {
            return false;
        }

        onStopped(context);
        MessageHelpers.showLongMessage(context, refusal);
        player.showProgressBar(false);
        player.finishReally();
        return true;
    }

    /** Why HearthTube mustn't play now (to show), or null when it may. Asks Hearth afresh each time. */
    @Nullable
    public static String refusal(Context context) {
        YouTubeAllowance.Decision decision = decide(context);

        switch (decision.verdict) {
            case LOCKED:
                return decision.message != null ? decision.message : context.getString(R.string.youtube_bedtime);
            case USED_UP_HOME_ASSISTANT:
                return decision.message != null ? decision.message : context.getString(R.string.youtube_time_used_up_shared);
            case USED_UP:
                return context.getString(R.string.youtube_time_used_up);
            default:
                return null;
        }
    }

    private static YouTubeAllowance.Decision decide(Context context) {
        if (context == null) {
            return YouTubeAllowance.Decision.NO_LIMIT;
        }

        // What's playing right now counts too
        take(context, true);

        HearthProfile hearth = HearthProfile.isHearthSuspended(context) ? null
                : HearthProfile.live(context, HearthProfile.queryHearth(context));

        // Mid-switch the row may still be the last profile's: the next check decides
        if (hearth != null && Boolean.FALSE.equals(hearth.profileReady)) {
            return YouTubeAllowance.Decision.NO_LIMIT;
        }

        YouTubeAllowance.Row row = hearth == null ? null : new YouTubeAllowance.Row(hearth.contractVersion, hearth.key(),
                hearth.youtubeMinutesLeft, hearth.scheduleLocked, hearth.allowanceMessage, hearth.allowanceSource,
                hearth.allowanceCheckedAt);
        YouTubeAllowance.Decision decision = allowance(context).decide(row, System.currentTimeMillis());
        save(context);

        if (decision.verdict != sLastVerdict) {
            sLastVerdict = decision.verdict;
            Log.i(TAG, "YouTube allowance: %s (Hearth: %s min left, source %s, locked %s, read at %s)", decision,
                    hearth != null ? hearth.youtubeMinutesLeft : null, hearth != null ? hearth.allowanceSource : null,
                    hearth != null && hearth.scheduleLocked, hearth != null ? hearth.allowanceCheckedAt : null);
        }

        return decision;
    }

    /** Counts the playing since the last time (and goes on counting when {@code stillPlaying}) */
    private static void take(Context context, boolean stillPlaying) {
        long ms;

        synchronized (sLock) {
            if (sPlayingSince == -1) {
                return;
            }

            long now = SystemClock.elapsedRealtime();
            // Counted every half minute while playing: anything longer was a missed stop
            ms = Math.min(now - sPlayingSince, HearthProfile.MAX_REPORT_MS);
            sPlayingSince = stillPlaying ? now : -1;
            sUnreportedMs = Math.min(sUnreportedMs + ms, HearthProfile.MAX_REPORT_MS);
        }

        allowance(context).played(ms, System.currentTimeMillis());
        save(context);
    }

    /** Hands the time Hearth hasn't had yet to it, off the main thread; what it doesn't take waits for the next time */
    private static void send(Context context) {
        Context app = context.getApplicationContext();

        sReporter.execute(() -> {
            long ms;

            synchronized (sLock) {
                if (sUnreportedMs < MIN_REPORT_MS) {
                    return;
                }

                ms = sUnreportedMs;
                sUnreportedMs = 0;
            }

            // In a Google TV profile, only while Hearth's agent there is connected (it passes the time on to Hearth)
            boolean taken = HearthProfile.live(app, HearthProfile.queryHearth(app)) != null && HearthProfile.reportPlaying(app, ms);

            if (taken) {
                Log.d(TAG, "Reported %s ms of playing to Hearth", ms);
            } else {
                synchronized (sLock) {
                    sUnreportedMs = Math.min(sUnreportedMs + ms, HearthProfile.MAX_REPORT_MS);
                }
                Log.d(TAG, "Hearth didn't take %s ms of playing: kept for the next report", ms);
            }
        });
    }

    private static synchronized YouTubeAllowance allowance(Context context) {
        if (sAllowance == null) {
            sAllowance = new YouTubeAllowance();
            sSaved = prefs(context).getString(PREF_HA_READ, null);
            sAllowance.restore(sSaved);
        }

        return sAllowance;
    }

    /** Home Assistant's last read and the playing since, kept across restarts for the local countdown */
    private static synchronized void save(Context context) {
        String saved = allowance(context).save();

        if (saved != null && !saved.equals(sSaved)) {
            sSaved = saved;
            prefs(context).edit().putString(PREF_HA_READ, saved).apply();
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
