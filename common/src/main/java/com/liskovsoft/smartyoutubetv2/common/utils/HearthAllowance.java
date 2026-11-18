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
 * playing starts and stops. When Hearth can't be relied on (older than contract 6, suspended, or in a kids profile
 * whose Hearth agent isn't connected) the last allowance it gave is kept for the rest of the day and counted down here
 * ({@link SilentHearthLimit}), and the time played meanwhile is kept for Hearth ({@link PlayTimer}). A kid Hearth never
 * gave a limit for has none, unless a parent set one on the device ({@link LocalAllowance}). On a phone with Glint, Glint
 * owns the daily limit instead of Hearth ({@link GlintChannel}): its minutes and lock, kept the same way when it goes
 * silent, with the device's own limit as an extra cap.
 */
public final class HearthAllowance {
    private static final String TAG = HearthAllowance.class.getSimpleName();
    /** Less than this waits for the next report */
    private static final long MIN_REPORT_MS = 1_000;
    private static final String PREFS = "hearth_allowance";
    private static final String PREF_HA_READ = "home_assistant_read";
    private static final String PREF_LOCAL = "local_allowance";
    private static final String PREF_DAY = "allowance_day";
    private static final String PREF_SILENT = "silent_hearth_limit";
    /** Played and not taken by Hearth yet ({@link PlayTimer}), so a restart doesn't forget it */
    private static final String PREF_UNREPORTED = "unreported_day_ms";
    private static boolean sUnreportedRestored;
    private static long sSavedUnreported = -1;
    /** Glint's limit among the kept ones ({@link SilentHearthLimit}), apart from any Hearth profile's */
    private static final String GLINT_KEY = "glint";
    /** Reports go to Hearth one at a time, off the main thread (a kids profile's Hearth agent passes them on) */
    private static final ExecutorService sReporter = Executors.newSingleThreadExecutor();
    private static YouTubeAllowance sAllowance;
    private static LocalAllowance sLocal;
    private static String sSaved;
    private static AllowanceDay sDay;
    private static String sSavedDay;
    private static SilentHearthLimit sSilent;
    private static String sSavedSilent;
    private static YouTubeAllowance.Verdict sLastVerdict;
    /** The playing clock (SystemClock.elapsedRealtime) and what Hearth hasn't taken: a missed stop counts at most one report */
    private static final PlayTimer sTimer = new PlayTimer(HearthProfile.MAX_REPORT_MS, HearthProfile.MAX_REPORT_MS);

    private HearthAllowance() {
    }

    /** A video started playing. True when it wasn't playing a moment ago. */
    public static boolean onPlaying() {
        return sTimer.start(SystemClock.elapsedRealtime());
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
        return sTimer.isPlaying();
    }

    /**
     * Stops the player when HearthTube mustn't play now, saying why, the way a refused Short closes it. True when it
     * stopped it.
     */
    public static boolean stopIfRefused(Context context, @Nullable PlaybackView player) {
        String refusal = player != null
                ? refusal(context, player.getVideo() != null ? player.getVideo().videoId : null) : null;

        if (refusal == null) {
            return false;
        }

        onStopped(context);
        MessageHelpers.showLongMessage(context, refusal);
        player.showProgressBar(false);
        player.setPlayWhenReady(false); // first, in case closing is slow or fails
        player.finishReally();
        return true;
    }

    /** Why HearthTube mustn't play now (to show), or null when it may. Asks Hearth afresh each time. */
    @Nullable
    public static String refusal(Context context) {
        Refusal refusal = refusalNow(context);
        return refusal != null ? refusal.message : null;
    }

    /** As {@link #refusal(Context)}, for a video about to open (a parent's approval is for one video only) */
    @Nullable
    public static String refusal(Context context, @Nullable String videoId) {
        Refusal refusal = refusalNow(context, videoId);
        return refusal != null ? refusal.message : null;
    }

    /** Why HearthTube mustn't play now, or null when it may */
    public static final class Refusal {
        /** A lock (bedtime, school time, a pause), not time used up: asking for more time doesn't help */
        public final boolean locked;
        public final String message;

        Refusal(boolean locked, String message) {
            this.locked = locked;
            this.message = message;
        }
    }

    /** True when only a parent's PIN window lets a video play now: ending that window would stop it */
    public static boolean playsOnlyByParent(Context context) {
        return ParentGate.isUnlocked() && refusalOf(context, decide(context)) != null;
    }

    /** As {@link #refusal}, saying whether it's a lock or time used up. Asks Hearth afresh each time. */
    @Nullable
    public static Refusal refusalNow(Context context) {
        // A parent's approval is for its video in the player: the browse screens close as before
        com.liskovsoft.smartyoutubetv2.common.app.models.data.Video video = context != null
                && com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager.instance(context).isPlayerInForeground()
                ? com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter.instance(context).getVideo() : null;
        return refusalNow(context, video != null ? video.videoId : null);
    }

    /** As {@link #refusalNow(Context)}, for this video (the one playing, or about to open) */
    @Nullable
    public static Refusal refusalNow(Context context, @Nullable String videoId) {
        YouTubeAllowance.Decision decision = decide(context);

        // A parent just typed the PIN in this profile: the same few minutes the time's-up page stays away, and the
        // first video opened then plays on to its end; that one only (what plays still counts toward the kid's day)
        if (ParentGate.isUnlocked() || ParentGate.isApproved(videoId)) {
            return null;
        }

        return refusalOf(context, decision);
    }

    @Nullable
    private static Refusal refusalOf(Context context, YouTubeAllowance.Decision decision) {
        switch (decision.verdict) {
            case LOCKED:
                return new Refusal(true, decision.message != null ? decision.message : context.getString(R.string.youtube_bedtime));
            case USED_UP_HOME_ASSISTANT:
                return new Refusal(false, decision.message != null ? decision.message : context.getString(R.string.youtube_time_used_up_shared));
            case USED_UP:
                return new Refusal(false, decision.message != null ? decision.message : context.getString(R.string.youtube_time_used_up));
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

        // Time limits and bedtimes are for kids: an adult's profile plays, whatever Hearth's row says (and isn't worth asking)
        if (!ParentGate.isKidsProfile(context)) {
            return YouTubeAllowance.Decision.NO_LIMIT;
        }

        HearthProfile hearth = HearthProfile.isHearthSuspended(context) ? null
                : HearthProfile.live(context, HearthProfile.queryHearth(context));

        // A phone with Glint: Glint's word on the phone's one daily limit
        if (hearth == null) {
            GlintChannel.Limit glint = GlintChannel.limit(context);

            if (glint != null) {
                return glintDecision(context, glint);
            }
        }

        // No Hearth to ask (a phone or tablet, or Hearth is silent), or mid-switch, when its row may still be the last
        // profile's: what it last gave counts down here, and a limit a parent set on the device itself
        if (hearth == null || Boolean.FALSE.equals(hearth.profileReady)) {
            return silentDecision(context);
        }

        // Hearth's own minutes count only what it was told: what's played and not reported yet comes off too (a refused
        // report, or the half minute before the next one). Home Assistant's are counted down from their read already.
        boolean haMinutes = YouTubeAllowance.SOURCE_HOME_ASSISTANT.equals(hearth.allowanceSource);
        Integer minutesLeft = hearth.youtubeMinutesLeft != null && !haMinutes
                ? (int) Math.max(0, hearth.youtubeMinutesLeft - sTimer.unreportedMs() / 60_000L) : hearth.youtubeMinutesLeft;
        YouTubeAllowance.Row row = new YouTubeAllowance.Row(hearth.contractVersion, hearth.key(),
                minutesLeft, hearth.scheduleLocked, hearth.allowanceMessage, hearth.allowanceSource,
                hearth.allowanceCheckedAt);
        YouTubeAllowance.Decision decision = allowance(context).decide(row, System.currentTimeMillis());
        save(context);
        rememberForSilence(context, hearth.key(), decision);

        if (decision.verdict != sLastVerdict) {
            sLastVerdict = decision.verdict;
            Log.i(TAG, "YouTube allowance: %s (Hearth: %s min left, source %s, locked %s, read at %s)", decision,
                    hearth != null ? hearth.youtubeMinutesLeft : null, hearth != null ? hearth.allowanceSource : null,
                    hearth != null && hearth.scheduleLocked, hearth != null ? hearth.allowanceCheckedAt : null);
        }

        return decision;
    }

    /**
     * Hearth can't be asked: its last lock still holds; otherwise what it last gave for today, less what's played since,
     * and the device's own limit
     */
    private static YouTubeAllowance.Decision silentDecision(Context context) {
        if (silent(context).isLocked()) {
            return new YouTubeAllowance.Decision(YouTubeAllowance.Verdict.LOCKED, silent(context).lockMessage(), 0L);
        }

        String today = today(context);
        Long left = silent(context).msLeft(today);
        Long local = local(context).msLeft(today);

        if (left == null || (local != null && local < left)) {
            left = local;
        }

        return left != null && left <= 0
                ? new YouTubeAllowance.Decision(YouTubeAllowance.Verdict.USED_UP, null, 0L)
                : new YouTubeAllowance.Decision(YouTubeAllowance.Verdict.PLAY, null, left);
    }

    /**
     * Glint's minutes (counted up to the last report it had), less what played here since, kept for when it goes
     * silent; its lock; and the device's own limit as an extra cap
     */
    private static YouTubeAllowance.Decision glintDecision(Context context, GlintChannel.Limit glint) {
        String today = today(context);
        Long left = glint.minutes == null ? null
                : Math.max(0, glint.minutes * 60_000L - GlintChannel.playedSince(glint.asOf));
        silent(context).remember(GLINT_KEY, left, today, System.currentTimeMillis());
        silent(context).rememberLock(GLINT_KEY, glint.locked, glint.message);
        saveSilent(context);

        if (glint.locked) {
            return new YouTubeAllowance.Decision(YouTubeAllowance.Verdict.LOCKED, glint.message, 0L);
        }

        Long local = local(context).msLeft(today);
        boolean localBinds = local != null && (left == null || local < left);

        if (localBinds) {
            left = local;
        }

        // Glint's wording is about Glint's limit, not the device's own
        return left != null && left <= 0
                ? new YouTubeAllowance.Decision(YouTubeAllowance.Verdict.USED_UP, localBinds ? null : glint.message, 0L)
                : new YouTubeAllowance.Decision(YouTubeAllowance.Verdict.PLAY, null, left);
    }

    /**
     * Keeps what Hearth just gave, for when it goes silent: its lock (held until Hearth says otherwise), or else its
     * minutes. A lock leaves the kept minutes as they were (it says nothing about them).
     */
    private static void rememberForSilence(Context context, String profileKey, YouTubeAllowance.Decision decision) {
        boolean locked = decision.verdict == YouTubeAllowance.Verdict.LOCKED;
        silent(context).rememberLock(profileKey, locked, decision.message);

        if (!locked) {
            silent(context).remember(profileKey, decision.msLeft, today(context), System.currentTimeMillis());
        }

        saveSilent(context);
    }

    /** Counts the playing since the last time (and goes on counting when {@code stillPlaying}) */
    private static void take(Context context, boolean stillPlaying) {
        restoreUnreported(context);
        long ms = sTimer.take(SystemClock.elapsedRealtime(), stillPlaying);

        if (ms <= 0) {
            return;
        }

        saveUnreported(context);

        // The countdowns kept on the device are a kid's: an adult's (or a parent's on a shared tablet) watching
        // mustn't use them up. In a kids profile it counts, a parent's PIN window included.
        if (!ParentGate.isKidsProfile(context)) {
            return;
        }

        allowance(context).played(ms, System.currentTimeMillis());
        save(context);
        local(context).played(today(context), ms);
        saveLocal(context);
        silent(context).played(ms);
        saveSilent(context);
    }

    /** Once per process: what was played and not taken by Hearth before a restart waits again */
    private static synchronized void restoreUnreported(Context context) {
        if (sUnreportedRestored || context == null) {
            return;
        }

        sUnreportedRestored = true;
        // Kept as "day:ms": time from an earlier day isn't today's to report (Hearth would charge it to today)
        String saved = prefs(context).getString(PREF_UNREPORTED, null);
        long ms = 0;

        if (saved != null) {
            int colon = saved.lastIndexOf(':');
            try {
                if (colon > 0 && saved.substring(0, colon).equals(today(context))) {
                    ms = Long.parseLong(saved.substring(colon + 1));
                }
            } catch (NumberFormatException e) {
                ms = 0;
            }
        }

        sSavedUnreported = ms;
        sTimer.restoreUnreported(ms);
    }

    private static synchronized void saveUnreported(Context context) {
        long ms = sTimer.unreportedMs();

        if (ms != sSavedUnreported) {
            sSavedUnreported = ms;
            prefs(context).edit().putString(PREF_UNREPORTED, today(context) + ":" + ms).apply();
        }
    }

    /** The daily limit a parent set on this device (minutes); 0 is none. Used only without a Hearth. */
    public static int getLocalLimitMinutes(Context context) {
        return local(context).getLimitMinutes();
    }

    public static void setLocalLimitMinutes(Context context, int minutes) {
        local(context).setLimitMinutes(minutes);
        saveLocal(context);
    }

    private static synchronized LocalAllowance local(Context context) {
        if (sLocal == null) {
            sLocal = new LocalAllowance();
            sLocal.restore(prefs(context).getString(PREF_LOCAL, null));
        }

        return sLocal;
    }

    private static synchronized void saveLocal(Context context) {
        prefs(context).edit().putString(PREF_LOCAL, local(context).save()).apply();
    }

    /** Today's date for the allowances, which a changed device date doesn't move back or skip ahead ({@link AllowanceDay}) */
    private static synchronized String today(Context context) {
        if (sDay == null) {
            sDay = new AllowanceDay();
            sSavedDay = prefs(context).getString(PREF_DAY, null);
            sDay.restore(sSavedDay);
        }

        String day = sDay.today(System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootCount(context));
        String saved = sDay.save();

        if (!saved.equals(sSavedDay)) {
            sSavedDay = saved;
            prefs(context).edit().putString(PREF_DAY, saved).apply();
        }

        return day;
    }

    private static long bootCount(Context context) {
        try {
            return android.provider.Settings.Global.getInt(context.getContentResolver(), "boot_count", -1);
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static synchronized SilentHearthLimit silent(Context context) {
        if (sSilent == null) {
            sSilent = new SilentHearthLimit();
            sSavedSilent = prefs(context).getString(PREF_SILENT, null);
            sSilent.restore(sSavedSilent);
        }

        return sSilent;
    }

    private static synchronized void saveSilent(Context context) {
        String saved = silent(context).save();

        if (!java.util.Objects.equals(saved, sSavedSilent)) {
            sSavedSilent = saved;
            prefs(context).edit().putString(PREF_SILENT, saved).apply();
        }
    }

    /** Hands the time Hearth hasn't had yet to it, off the main thread; what it doesn't take waits for the next time */
    private static void send(Context context) {
        Context app = context.getApplicationContext();

        sReporter.execute(() -> {
            // In a Google TV profile, only while Hearth's agent there is connected (it passes the time on to Hearth);
            // one report's worth at a time, so a long wait is handed over in as many reports as it takes
            restoreUnreported(app);
            boolean live = sTimer.unreportedMs() >= MIN_REPORT_MS && HearthProfile.live(app, HearthProfile.queryHearth(app)) != null;
            long ms;

            while (live && (ms = sTimer.nextReport(MIN_REPORT_MS)) > 0) {
                if (HearthProfile.reportPlaying(app, ms)) {
                    Log.d(TAG, "Reported %s ms of playing to Hearth", ms);
                    saveUnreported(app);
                } else {
                    sTimer.giveBack(ms);
                    saveUnreported(app);
                    Log.d(TAG, "Hearth didn't take %s ms of playing: kept for the next report", ms);
                    return;
                }
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
